package com.monotron.support.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.monotron.support.auth.dto.AuthResponse;
import com.monotron.support.auth.dto.GoogleAuthConfig;
import com.monotron.support.auth.dto.GoogleAuthRequest;
import com.monotron.support.auth.dto.LoginRequest;
import com.monotron.support.auth.dto.RegisterRequest;
import com.monotron.support.auth.dto.TokenPair;
import com.monotron.support.billing.BillingService;
import com.monotron.support.common.ConflictException;
import com.monotron.support.common.ForbiddenException;
import com.monotron.support.common.Ids;
import com.monotron.support.common.NotFoundException;
import com.monotron.support.common.UnauthorizedException;
import com.monotron.support.security.AuthPrincipal;
import com.monotron.support.security.JwtService;
import com.monotron.support.tenancy.dto.UserDto;
import com.monotron.support.tenancy.dto.WorkspaceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication and tenant bootstrap.
 *
 * <p>Registering creates the full tenant in one transaction: the platform user,
 * the workspace, the membership, the messenger app (whose id is what customers
 * embed on their website) and the widget defaults.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String DEFAULT_GREETING = "Hi there 👋";
    private static final String DEFAULT_SUBTITLE = "We typically reply in a few minutes.";
    private static final String DEFAULT_COLOR = "#4F46E5";
    private static final String DEFAULT_INTRO = "Ask us anything — we're here to help.";
    private static final String DEFAULT_PLACEHOLDER = "Send a message…";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final BillingService billingService;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, JwtService jwtService,
            BillingService billingService, GoogleTokenVerifier googleTokenVerifier) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.billingService = billingService;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normaliseEmail(request.email());
        String name = request.name().trim();

        if (findUserByEmail(email).isPresent()) {
            throw new ConflictException("An account with this email already exists");
        }

        return createAccount(email, name, passwordEncoder.encode(request.password()), request.workspaceName());
    }

    /**
     * Sign in with a Google Identity Services ID token.
     *
     * <p>Existing accounts are matched by verified email, so a Google login
     * also works for someone who originally registered with a password. When
     * the intent is {@code REGISTER} and no account exists, a new workspace is
     * created with the same defaults as password registration.
     */
    @Transactional
    public AuthResponse googleAuth(GoogleAuthRequest request) {
        GoogleTokenVerifier.GoogleUser googleUser = googleTokenVerifier.verify(request.credential());
        String email = normaliseEmail(googleUser.email());
        String intent = request.intent().trim().toUpperCase(Locale.ROOT);

        Optional<UserRow> existing = findUserByEmail(email);
        if (existing.isPresent()) {
            UserRow user = existing.get();
            if ("DISABLED".equalsIgnoreCase(user.status())) {
                throw new ForbiddenException("This account has been disabled");
            }
            jdbc.update("update users set status = 'ONLINE', last_login_at = now(), updated_at = now() where id = ?",
                    user.id());

            List<WorkspaceDto> workspaces = workspacesOf(user.id());
            WorkspaceDto active = workspaces.isEmpty() ? null : workspaces.get(0);
            TokenPair tokens = issueTokens(user, active == null ? null : active.id(),
                    active == null ? AuthPrincipal.ROLE_AGENT : active.role());
            return new AuthResponse(toDto(user, active == null ? "AGENT" : active.role()), workspaces, active,
                    tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
        }

        if ("LOGIN".equals(intent)) {
            throw new NotFoundException(
                    "No Monotron account found for this Google email. Create an account first.");
        }

        String name = googleUser.name() == null || googleUser.name().isBlank()
                ? email
                : googleUser.name().trim();
        // Google users never use a password. Store a high-entropy hash so the
        // NOT NULL column stays satisfied and password login cannot match it.
        return createAccount(email, name, passwordEncoder.encode(Ids.refreshToken()), request.workspaceName());
    }

    public GoogleAuthConfig googleConfig() {
        return new GoogleAuthConfig(googleTokenVerifier.isConfigured(), googleTokenVerifier.clientId());
    }

    // ------------------------------------------------------------------
    // Login / refresh / logout
    // ------------------------------------------------------------------

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normaliseEmail(request.email());
        UserRow user = findUserByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        if ("DISABLED".equalsIgnoreCase(user.status())) {
            throw new ForbiddenException("This account has been disabled");
        }

        jdbc.update("update users set status = 'ONLINE', last_login_at = now(), updated_at = now() where id = ?",
                user.id());

        List<WorkspaceDto> workspaces = workspacesOf(user.id());
        WorkspaceDto active = workspaces.isEmpty() ? null : workspaces.get(0);

        TokenPair tokens = issueTokens(user, active == null ? null : active.id(),
                active == null ? AuthPrincipal.ROLE_AGENT : active.role());

        return new AuthResponse(toDto(user, active == null ? "AGENT" : active.role()), workspaces, active,
                tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {
        String hash = hash(refreshToken);
        RefreshRow row = jdbc.query("""
                select rt.id, rt.user_id, rt.expires_at, rt.revoked, rt.workspace_id, u.email, u.name, u.role, u.status
                from refresh_tokens rt
                join users u on u.id = rt.user_id
                where rt.token_hash = ?
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            return new RefreshRow(
                    rs.getString("id"),
                    rs.getString("user_id"),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getBoolean("revoked"),
                    rs.getString("workspace_id"),
                    rs.getString("email"),
                    rs.getString("name"),
                    rs.getString("role"),
                    rs.getString("status"));
        }, hash);

        if (row == null || row.revoked() || row.expiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token is invalid or has expired");
        }

        // Rotate: the presented token is revoked and replaced by a fresh one.
        jdbc.update("update refresh_tokens set revoked = true where id = ?", row.id());

        UserRow user = new UserRow(row.userId(), row.email(), null, row.name(), row.role(), row.status());
        String role = row.workspaceId() == null ? user.role() : membershipRole(row.workspaceId(), row.userId());
        TokenPair tokens = issueTokens(user, row.workspaceId(), role);
        return tokens;
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        jdbc.update("update refresh_tokens set revoked = true where token_hash = ?", hash(refreshToken));
    }

    // ------------------------------------------------------------------
    // Session
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AuthResponse currentSession(AuthPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        UserRow user = findUserById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists"));
        List<WorkspaceDto> workspaces = workspacesOf(user.id());
        WorkspaceDto active = workspaces.stream()
                .filter(w -> w.id().equals(principal.workspaceId()))
                .findFirst()
                .orElse(workspaces.isEmpty() ? null : workspaces.get(0));
        return new AuthResponse(toDto(user, active == null ? user.role() : active.role()), workspaces, active,
                null, null, 0);
    }

    @Transactional
    public AuthResponse switchWorkspace(AuthPrincipal principal, String workspaceId) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        UserRow user = findUserById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists"));
        String role = membershipRole(workspaceId, user.id());
        if (role == null) {
            throw new ForbiddenException("You are not a member of that workspace");
        }
        List<WorkspaceDto> workspaces = workspacesOf(user.id());
        WorkspaceDto active = workspaces.stream()
                .filter(w -> w.id().equals(workspaceId))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("You are not a member of that workspace"));

        TokenPair tokens = issueTokens(user, workspaceId, role);
        return new AuthResponse(toDto(user, role), workspaces, active, tokens.accessToken(), tokens.refreshToken(),
                tokens.expiresIn());
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private AuthResponse createAccount(String email, String name, String passwordHash, String requestedWorkspaceName) {
        String userId = Ids.uuid();
        jdbc.update("""
                insert into users (id, email, password_hash, name, role, status, created_at, updated_at)
                values (?, ?, ?, ?, 'ADMIN', 'ONLINE', now(), now())
                """, userId, email, passwordHash, name);

        String workspaceName = requestedWorkspaceName == null || requestedWorkspaceName.isBlank()
                ? name + "'s workspace"
                : requestedWorkspaceName.trim();

        String workspaceId = Ids.uuid();
        jdbc.update("""
                insert into workspaces (id, name, slug, created_at, updated_at)
                values (?, ?, ?, now(), now())
                """, workspaceId, workspaceName, Ids.slug(workspaceName, true));

        jdbc.update("""
                insert into workspace_memberships (workspace_id, user_id, role, created_at)
                values (?, ?, 'ADMIN', now())
                """, workspaceId, userId);

        // From here on the new tenant becomes the active one so that the
        // workspace stamping trigger and the RLS policies agree with it.
        bindTenant(workspaceId);
        String appId = createMessengerApp(workspaceId, workspaceName);
        billingService.startTrial(workspaceId);

        UserDto user = new UserDto(userId, email, name, "ADMIN", "ONLINE");
        WorkspaceDto workspace = new WorkspaceDto(workspaceId, workspaceName, slugOf(workspaceId),
                "ADMIN", appId);

        UserRow userRow = new UserRow(userId, email, null, name, "ADMIN", "ONLINE");
        TokenPair tokens = issueTokens(userRow, workspaceId, "ADMIN");
        return new AuthResponse(user, List.of(workspace), workspace, tokens.accessToken(), tokens.refreshToken(),
                tokens.expiresIn());
    }

    private String createMessengerApp(String workspaceId, String workspaceName) {
        String appId = Ids.appId();
        jdbc.update("""
                insert into apps (id, name, hmac_secret, created_at, workspace_id, ai_enabled, auto_reply, ai_temperature)
                values (?, ?, ?, now(), ?, true, true, 0.2)
                """, appId, workspaceName, Ids.refreshToken(), workspaceId);

        jdbc.update("""
                insert into widget_settings (app_id, workspace_name, greeting, subtitle, color, intro_message,
                                            input_placeholder, show_branding, auto_open, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, true, true, now())
                """, appId, workspaceName, DEFAULT_GREETING, DEFAULT_SUBTITLE, DEFAULT_COLOR, DEFAULT_INTRO,
                DEFAULT_PLACEHOLDER);
        return appId;
    }

    private TokenPair issueTokens(UserRow user, String workspaceId, String role) {
        String accessToken = jwtService.issueAccessToken(user.id(), user.email(), user.name(), workspaceId, role);
        String refreshToken = Ids.refreshToken();
        jdbc.update("""
                insert into refresh_tokens (id, user_id, token_hash, expires_at, revoked, created_at, workspace_id)
                values (?, ?, ?, ?, false, now(), ?)
                """, Ids.uuid(), user.id(), hash(refreshToken),
                java.sql.Timestamp.from(Instant.now().plus(jwtService.refreshTokenTtl())), workspaceId);
        return new TokenPair(accessToken, refreshToken, jwtService.accessTokenTtl().toSeconds());
    }

    private List<WorkspaceDto> workspacesOf(String userId) {
        List<WorkspaceDto> workspaces = jdbc.query("""
                select w.id, w.name, w.slug, wm.role, l.app_id
                from workspace_memberships wm
                join workspaces w on w.id = wm.workspace_id
                left join app_workspace_lookup l on l.workspace_id = w.id
                where wm.user_id = ?
                order by wm.created_at asc
                """, (rs, i) -> new WorkspaceDto(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("role"),
                rs.getString("app_id")), userId);
        return workspaces == null ? List.of() : workspaces;
    }

    private String membershipRole(String workspaceId, String userId) {
        List<String> roles = jdbc.query("""
                select role from workspace_memberships where workspace_id = ? and user_id = ?
                """, (rs, i) -> rs.getString(1), workspaceId, userId);
        return roles.isEmpty() ? null : roles.get(0);
    }

    private Optional<UserRow> findUserByEmail(String email) {
        List<UserRow> rows = jdbc.query("""
                select id, email, password_hash, name, role, status from users where lower(email) = lower(?)
                """, (rs, i) -> new UserRow(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("name"),
                rs.getString("role"),
                rs.getString("status")), email);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private Optional<UserRow> findUserById(String userId) {
        List<UserRow> rows = jdbc.query("""
                select id, email, password_hash, name, role, status from users where id = ?
                """, (rs, i) -> new UserRow(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("name"),
                rs.getString("role"),
                rs.getString("status")), userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private UserDto toDto(UserRow user, String role) {
        return new UserDto(user.id(), user.email(), user.name(), role, user.status());
    }

    private String slugOf(String workspaceId) {
        List<String> slugs = jdbc.query("select slug from workspaces where id = ?", (rs, i) -> rs.getString(1),
                workspaceId);
        return slugs.isEmpty() ? null : slugs.get(0);
    }

    private void bindTenant(String workspaceId) {
        jdbc.queryForObject("select set_config('app.workspace_id', ?, true)", String.class, workspaceId);
    }

    private static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record UserRow(String id, String email, String passwordHash, String name, String role, String status) {
    }

    private record RefreshRow(String id, String userId, Instant expiresAt, boolean revoked, String workspaceId,
                              String email, String name, String role, String status) {
    }
}
