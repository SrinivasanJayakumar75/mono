package com.monotron.support.channels;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.monotron.support.channels.dto.OAuthStartDto;
import com.monotron.support.common.ApiException;
import com.monotron.support.common.ForbiddenException;
import com.monotron.support.common.Ids;
import com.monotron.support.config.AppProperties;
import com.monotron.support.security.AuthPrincipal;
import com.monotron.support.security.TenantBinding;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Starts and completes the OAuth handshake for external channels.
 *
 * <p>Only the platform app credentials come from environment variables. The
 * resulting tenant access/refresh tokens are encrypted and stored in
 * {@code channel_credentials} for that workspace. No token is ever returned to
 * the browser.
 */
@Service
public class ChannelOAuthService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final List<String> GMAIL_SCOPES = List.of(
            "openid",
            "email",
            "profile",
            "https://www.googleapis.com/auth/gmail.readonly",
            "https://www.googleapis.com/auth/gmail.send");

    private static final List<String> WHATSAPP_SCOPES = List.of(
            "whatsapp_business_management",
            "whatsapp_business_messaging",
            "business_management");

    private static final List<String> INSTAGRAM_SCOPES = List.of(
            "instagram_basic",
            "instagram_manage_messages",
            "pages_show_list",
            "pages_manage_metadata",
            "business_management");

    private final AppProperties properties;
    private final ChannelOAuthStateService stateService;
    private final ChannelCredentialCrypto crypto;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final TenantBinding tenantBinding;
    private final RestClient restClient;

    public ChannelOAuthService(AppProperties properties,
                               ChannelOAuthStateService stateService,
                               ChannelCredentialCrypto crypto,
                               JdbcTemplate jdbc,
                               TransactionTemplate transactionTemplate,
                               TenantBinding tenantBinding) {
        this.properties = properties;
        this.stateService = stateService;
        this.crypto = crypto;
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.tenantBinding = tenantBinding;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public OAuthStartDto start(AuthPrincipal principal, String channel) {
        requireWorkspace(principal);
        requireEditor(principal);
        String normalized = ChannelConnectionService.normalizeChannel(channel);
        String state = stateService.issue(principal.workspaceId(), normalized);
        String url = switch (normalized) {
            case "GMAIL" -> gmailAuthorizationUrl(state);
            case "WHATSAPP", "INSTAGRAM" -> metaAuthorizationUrl(normalized, state);
            default -> throw new IllegalStateException("Unhandled channel " + normalized);
        };
        return new OAuthStartDto(normalized, url);
    }

    public String handleCallback(String channel, String state, String code, String error) {
        String normalized;
        try {
            normalized = ChannelConnectionService.normalizeChannel(channel);
        } catch (RuntimeException ex) {
            return failureRedirect("Invalid channel");
        }
        if (error != null && !error.isBlank()) {
            return failureRedirect(error);
        }
        if (code == null || code.isBlank()) {
            return failureRedirect("The provider did not return an authorization code.");
        }

        ChannelOAuthStateService.State parsed = stateService.parse(state)
                .orElse(null);
        if (parsed == null) {
            return failureRedirect("The authorization request expired. Please try again.");
        }
        if (!parsed.channel().equalsIgnoreCase(normalized)) {
            return failureRedirect("The authorization request did not match the channel.");
        }

        try {
            TokenResult tokens = switch (normalized) {
                case "GMAIL" -> exchangeGmail(code);
                case "WHATSAPP", "INSTAGRAM" -> exchangeMeta(normalized, code);
                default -> throw new IllegalStateException("Unhandled channel " + normalized);
            };
            save(parsed.workspaceId(), normalized, tokens);
            return successRedirect(normalized);
        } catch (RuntimeException ex) {
            return failureRedirect(message(ex));
        }
    }

    public String failureRedirect(String message) {
        return frontendBase("/channels")
                + "?error=" + URLEncoder.encode(message == null ? "Connection failed" : message,
                StandardCharsets.UTF_8);
    }

    private String successRedirect(String channel) {
        return frontendBase("/channels") + "?connected=" + URLEncoder.encode(channel, StandardCharsets.UTF_8);
    }

    private String frontendBase(String path) {
        String base = properties.getChannels().getFrontendBaseUrl();
        if (base == null || base.isBlank()) {
            base = "http://localhost:3000";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private String gmailAuthorizationUrl(String state) {
        AppProperties.Channels.Gmail gmail = properties.getChannels().getGmail();
        if (!gmail.isConfigured()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Gmail OAuth is not configured. Add GMAIL_CLIENT_ID and GMAIL_CLIENT_SECRET.");
        }
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", gmail.getClientId())
                .queryParam("redirect_uri", gmail.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(" ", GMAIL_SCOPES))
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("include_granted_scopes", "true")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    private String metaAuthorizationUrl(String channel, String state) {
        AppProperties.Channels.Meta meta = properties.getChannels().getMeta();
        if (!meta.isConfigured()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Meta OAuth is not configured. Add META_APP_ID and META_APP_SECRET.");
        }
        return UriComponentsBuilder.fromUriString(
                        "https://www.facebook.com/" + meta.getGraphVersion() + "/dialog/oauth")
                .queryParam("client_id", meta.getAppId())
                .queryParam("redirect_uri", meta.redirectUri(channel))
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(",", scopes(channel)))
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    private List<String> scopes(String channel) {
        return "WHATSAPP".equals(channel) ? WHATSAPP_SCOPES : INSTAGRAM_SCOPES;
    }

    private TokenResult exchangeGmail(String code) {
        AppProperties.Channels.Gmail gmail = properties.getChannels().getGmail();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", gmail.getClientId());
        form.add("client_secret", gmail.getClientSecret());
        form.add("redirect_uri", gmail.getRedirectUri());
        form.add("grant_type", "authorization_code");

        Map<String, Object> token = postForm("https://oauth2.googleapis.com/token", form);
        String accessToken = string(token.get("access_token"));
        if (accessToken == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Google did not return an access token.");
        }
        String refreshToken = string(token.get("refresh_token"));
        Instant expiresAt = expiry(token.get("expires_in"));
        String scope = string(token.get("scope"));

        Map<String, Object> profile = restClient.get()
                .uri("https://www.googleapis.com/oauth2/v2/userinfo")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(MAP_TYPE);
        String email = profile == null ? null : string(profile.get("email"));
        String id = profile == null ? null : string(profile.get("id"));

        return new TokenResult(accessToken, refreshToken, expiresAt, scope, email, id);
    }

    private TokenResult exchangeMeta(String channel, String code) {
        AppProperties.Channels.Meta meta = properties.getChannels().getMeta();
        String redirectUri = meta.redirectUri(channel);

        Map<String, Object> shortLived = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("graph.facebook.com")
                        .path("/" + meta.getGraphVersion() + "/oauth/access_token")
                        .queryParam("client_id", meta.getAppId())
                        .queryParam("client_secret", meta.getAppSecret())
                        .queryParam("redirect_uri", redirectUri)
                        .queryParam("code", code)
                        .build())
                .retrieve()
                .body(MAP_TYPE);
        String shortToken = shortLived == null ? null : string(shortLived.get("access_token"));
        if (shortToken == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Meta did not return an access token.");
        }

        Map<String, Object> longLived = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("graph.facebook.com")
                        .path("/" + meta.getGraphVersion() + "/oauth/access_token")
                        .queryParam("grant_type", "fb_exchange_token")
                        .queryParam("client_id", meta.getAppId())
                        .queryParam("client_secret", meta.getAppSecret())
                        .queryParam("fb_exchange_token", shortToken)
                        .build())
                .retrieve()
                .body(MAP_TYPE);
        String longLivedToken = longLived == null ? null : string(longLived.get("access_token"));
        String accessToken = longLivedToken == null ? shortToken : longLivedToken;
        Instant expiresAt = longLived == null
                ? expiry(shortLived.get("expires_in"))
                : expiry(longLived.get("expires_in"));

        Map<String, Object> profile = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("graph.facebook.com")
                        .path("/" + meta.getGraphVersion() + "/me")
                        .queryParam("fields", "id,name,username")
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .body(MAP_TYPE);
        String id = profile == null ? null : string(profile.get("id"));
        String username = profile == null ? null : string(profile.get("username"));
        String name = profile == null ? null : string(profile.get("name"));
        String label = username != null ? username : name;

        return new TokenResult(accessToken, null, expiresAt, String.join(",", scopes(channel)), label, id);
    }

    private Map<String, Object> postForm(String url, MultiValueMap<String, String> form) {
        try {
            Map<String, Object> body = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MAP_TYPE);
            return body == null ? Map.of() : body;
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "The provider rejected the authorization code.", ex);
        }
    }

    private void save(String workspaceId, String channel, TokenResult tokens) {
        transactionTemplate.executeWithoutResult(status -> {
            tenantBinding.bind(workspaceId);
            try {
                jdbc.update("""
                        insert into channel_credentials
                            (id, channel, external_account_id, account_label, encrypted_access_token,
                             encrypted_refresh_token, token_expires_at, scopes, connected_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                        on conflict (workspace_id, channel) do update
                        set external_account_id = excluded.external_account_id,
                            account_label = excluded.account_label,
                            encrypted_access_token = excluded.encrypted_access_token,
                            encrypted_refresh_token = coalesce(
                                excluded.encrypted_refresh_token,
                                channel_credentials.encrypted_refresh_token),
                            token_expires_at = excluded.token_expires_at,
                            scopes = excluded.scopes,
                            connected_at = now(),
                            updated_at = now()
                        """,
                        Ids.uuid(),
                        channel,
                        tokens.externalAccountId(),
                        tokens.accountLabel() == null ? "" : tokens.accountLabel(),
                        crypto.encrypt(tokens.accessToken()),
                        crypto.encrypt(tokens.refreshToken()),
                        tokens.expiresAt() == null ? null : Timestamp.from(tokens.expiresAt()),
                        tokens.scopes() == null ? "" : tokens.scopes());
            } finally {
                tenantBinding.clear();
            }
        });
    }

    private static Instant expiry(Object value) {
        if (value instanceof Number number) {
            return Instant.now().plusSeconds(number.longValue());
        }
        if (value instanceof String text) {
            try {
                return Instant.now().plusSeconds(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String message(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Could not connect the channel."
                : ex.getMessage();
    }

    private void requireWorkspace(AuthPrincipal principal) {
        if (principal == null || !principal.hasWorkspace()) {
            throw new ForbiddenException("No workspace is selected for this session");
        }
    }

    private void requireEditor(AuthPrincipal principal) {
        if (principal != null && (principal.isAdmin()
                || AuthPrincipal.ROLE_AGENT.equalsIgnoreCase(principal.role()))) {
            return;
        }
        throw new ForbiddenException("You do not have permission to connect channels");
    }

    private record TokenResult(
            String accessToken,
            String refreshToken,
            Instant expiresAt,
            String scopes,
            String accountLabel,
            String externalAccountId) {
    }
}
