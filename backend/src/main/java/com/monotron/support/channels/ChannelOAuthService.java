package com.monotron.support.channels;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monotron.support.channels.dto.OAuthStartDto;
import com.monotron.support.common.ApiException;
import com.monotron.support.common.ForbiddenException;
import com.monotron.support.common.Ids;
import com.monotron.support.config.AppProperties;
import com.monotron.support.security.AuthPrincipal;
import com.monotron.support.security.TenantBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.client.RestClientResponseException;
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

    private static final Logger log = LoggerFactory.getLogger(ChannelOAuthService.class);

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
            "instagram_business_basic",
            "instagram_business_manage_messages");

    /**
     * Unified Login requests its permissions through the Facebook Login for
     * Business configuration, but the granted permission names are stored with
     * the connection for display and diagnostics.
     */
    private static final List<String> INSTAGRAM_UNIFIED_SCOPES = List.of(
            "instagram_basic",
            "instagram_manage_messages");

    private final AppProperties properties;
    private final ChannelOAuthStateService stateService;
    private final ChannelCredentialCrypto crypto;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final TenantBinding tenantBinding;
    private final MetaApiClient metaApiClient;
    private final ObjectMapper mapper;
    private final RestClient restClient;

    public ChannelOAuthService(AppProperties properties,
                               ChannelOAuthStateService stateService,
                               ChannelCredentialCrypto crypto,
                               JdbcTemplate jdbc,
                               TransactionTemplate transactionTemplate,
                               TenantBinding tenantBinding,
                               MetaApiClient metaApiClient,
                               ObjectMapper mapper) {
        this.properties = properties;
        this.stateService = stateService;
        this.crypto = crypto;
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.tenantBinding = tenantBinding;
        this.metaApiClient = metaApiClient;
        this.mapper = mapper;
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
            case "WHATSAPP" -> whatsappAuthorizationUrl(state);
            case "INSTAGRAM" -> instagramAuthorizationUrl(state);
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
                case "WHATSAPP" -> exchangeWhatsApp(code);
                case "INSTAGRAM" -> exchangeInstagram(code);
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

    private String whatsappAuthorizationUrl(String state) {
        AppProperties.Channels.Meta meta = properties.getChannels().getMeta();
        if (!meta.isWhatsAppConfigured()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "WhatsApp OAuth is not configured. Add META_APP_ID and META_APP_SECRET.");
        }
        return UriComponentsBuilder.fromUriString(
                        "https://www.facebook.com/" + meta.getGraphVersion() + "/dialog/oauth")
                .queryParam("client_id", meta.getAppId())
                .queryParam("redirect_uri", meta.getWhatsappRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(",", WHATSAPP_SCOPES))
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    /**
     * New Meta apps only expose Instagram API with Unified Login: it reuses
     * the Meta app id/secret and a Facebook Login for Business config id.
     * Older apps that still have the Instagram product use the legacy
     * Instagram Login flow.
     */
    private String instagramAuthorizationUrl(String state) {
        AppProperties.Channels.Meta meta = properties.getChannels().getMeta();
        if (!meta.isInstagramConfigured()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Instagram OAuth is not configured. Add META_INSTAGRAM_CONFIG_ID "
                            + "(Unified Login) or META_INSTAGRAM_APP_ID / "
                            + "META_INSTAGRAM_APP_SECRET (legacy Instagram Login).");
        }
        if (meta.isInstagramUnified()) {
            return UriComponentsBuilder.fromUriString(
                            "https://www.facebook.com/" + meta.getGraphVersion() + "/dialog/oauth")
                    .queryParam("client_id", meta.getAppId())
                    .queryParam("config_id", meta.getInstagramConfigId())
                    .queryParam("redirect_uri", meta.getInstagramRedirectUri())
                    .queryParam("response_type", "code")
                    .queryParam("display", "popup")
                    .queryParam("prompt", "login")
                    .queryParam("state", state)
                    .build()
                    .toUriString();
        }
        return UriComponentsBuilder.fromUriString("https://www.instagram.com/oauth/authorize")
                .queryParam("client_id", meta.getInstagramAppId())
                .queryParam("redirect_uri", meta.getInstagramRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(",", INSTAGRAM_SCOPES))
                .queryParam("enable_fb_login", "0")
                .queryParam("state", state)
                .build()
                .toUriString();
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

        return new TokenResult(accessToken, refreshToken, expiresAt, scope, email, id, Map.of());
    }

    private TokenResult exchangeWhatsApp(String code) {
        AppProperties.Channels.Meta meta = properties.getChannels().getMeta();
        String redirectUri = meta.getWhatsappRedirectUri();

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

        return whatsappToken(accessToken, expiresAt);
    }

    /**
     * Unified Login exchanges the code on graph.facebook.com and returns a
     * Business Integration System User access token. The legacy Instagram
     * Login flow exchanges on api.instagram.com and graph.instagram.com.
     */
    private TokenResult exchangeInstagram(String code) {
        AppProperties.Channels.Meta meta = properties.getChannels().getMeta();
        if (meta.isInstagramUnified()) {
            return exchangeInstagramUnified(code, meta);
        }
        String redirectUri = meta.getInstagramRedirectUri();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", meta.getInstagramAppId());
        form.add("client_secret", meta.getInstagramAppSecret());
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri);
        form.add("code", code);

        Map<String, Object> shortLived;
        try {
            shortLived = restClient.post()
                    .uri("https://api.instagram.com/oauth/access_token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MAP_TYPE);
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Instagram rejected the authorization code. Check the Instagram app "
                            + "credentials and redirect URI.", ex);
        }
        String shortToken = shortLived == null ? null : string(shortLived.get("access_token"));
        if (shortToken == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Instagram did not return an access token.");
        }

        Map<String, Object> longLived;
        try {
            longLived = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("graph.instagram.com")
                            .path("/access_token")
                            .queryParam("grant_type", "ig_exchange_token")
                            .queryParam("client_secret", meta.getInstagramAppSecret())
                            .queryParam("access_token", shortToken)
                            .build())
                    .retrieve()
                    .body(MAP_TYPE);
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Instagram rejected the token exchange.", ex);
        }
        String longLivedToken = longLived == null ? null : string(longLived.get("access_token"));
        String accessToken = longLivedToken == null ? shortToken : longLivedToken;
        Instant expiresAt = longLived == null
                ? expiry(shortLived.get("expires_in"))
                : expiry(longLived.get("expires_in"));

        return instagramToken(accessToken, expiresAt);
    }

    private TokenResult exchangeInstagramUnified(String code, AppProperties.Channels.Meta meta) {
        String redirectUri = meta.getInstagramRedirectUri();
        Map<String, Object> token;
        try {
            token = restClient.get()
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
        } catch (RestClientResponseException ex) {
            String detail = ex.getResponseBodyAsString();
            log.warn("Instagram Unified Login code exchange failed: HTTP {} {} redirect_uri={}",
                    ex.getStatusCode().value(), detail, redirectUri);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Meta rejected the Instagram authorization code (HTTP "
                            + ex.getStatusCode().value() + "): " + detail
                            + " redirect_uri=" + redirectUri
                            + ". The redirect URL in the Unified Login configuration must match "
                            + "this value exactly. Click Connect again in a fresh tab; a code "
                            + "can only be exchanged once and expires quickly.", ex);
        } catch (RestClientException ex) {
            log.warn("Instagram Unified Login code exchange failed", ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Meta rejected the Instagram authorization code. Check the Unified Login "
                            + "configuration and the redirect URI.", ex);
        }
        String accessToken = token == null ? null : string(token.get("access_token"));
        if (accessToken == null || accessToken.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Meta did not return an Instagram access token.");
        }
        return instagramUnifiedToken(accessToken, expiry(token.get("expires_in")));
    }

    private TokenResult instagramUnifiedToken(String accessToken, Instant expiresAt) {
        MetaApiClient.InstagramUnifiedAccount account =
                metaApiClient.instagramUnifiedProfile(accessToken);
        if (account.igUserId() == null || account.igUserId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Meta did not return an Instagram professional account. Open the Meta App "
                            + "Dashboard, edit the Unified Login configuration and make sure it "
                            + "requests an Instagram account asset with instagram_basic and "
                            + "instagram_manage_messages, save the same Instagram redirect URL, "
                            + "then reconnect and select the account. In Development mode the "
                            + "account must also accept an Instagram Tester invite.");
        }
        try {
            metaApiClient.subscribeInstagramUnified(account.igUserId(), accessToken);
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Instagram connected, but Meta rejected the webhook subscription: "
                            + ex.getMessage(), ex);
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("igUserId", account.igUserId());
        metadata.put("username", blankToEmpty(account.username()));
        metadata.put("clientBusinessId", blankToEmpty(account.clientBusinessId()));
        metadata.put("unifiedLogin", "true");
        metadata.put("tokenType", "SUAT");
        String label = firstNonBlank(account.username(), "Instagram");
        return new TokenResult(accessToken, null, expiresAt,
                String.join(",", INSTAGRAM_UNIFIED_SCOPES), label,
                account.igUserId(), metadata);
    }

    private TokenResult whatsappToken(String accessToken, Instant expiresAt) {
        AppProperties.Channels.Meta meta = properties.getChannels().getMeta();
        MetaApiClient.WhatsAppAccount account;
        if (meta.hasPartialWhatsAppManualConfig()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Set both META_WHATSAPP_WABA_ID and META_WHATSAPP_PHONE_NUMBER_ID, "
                            + "or leave both empty.");
        }
        if (meta.isWhatsAppManualConfigured()) {
            account = metaApiClient.whatsappPhoneNumber(
                    meta.getWhatsappPhoneNumberId(), meta.getWhatsappWabaId(), accessToken);
        } else {
            List<MetaApiClient.WhatsAppAccount> accounts =
                    metaApiClient.whatsappAccounts(accessToken);
            if (accounts.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "Meta did not return a WhatsApp Business phone number. In the Meta App "
                                + "Dashboard open WhatsApp -> API Setup, add a test phone number "
                                + "(or add your own number in WhatsApp Manager), then reconnect "
                                + "with a Facebook account that administers that business. You can "
                                + "also set META_WHATSAPP_WABA_ID and "
                                + "META_WHATSAPP_PHONE_NUMBER_ID to use a number directly.");
            }
            account = accounts.get(0);
        }
        try {
            metaApiClient.subscribeWhatsApp(account.wabaId(), accessToken);
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "WhatsApp connected, but Meta rejected the webhook subscription: " + ex.getMessage(), ex);
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("phoneNumberId", account.phoneNumberId());
        metadata.put("wabaId", account.wabaId());
        metadata.put("displayPhoneNumber", blankToEmpty(account.displayPhoneNumber()));
        metadata.put("verifiedName", blankToEmpty(account.verifiedName()));
        String label = firstNonBlank(
                account.displayPhoneNumber(), account.verifiedName(), account.wabaName(), "WhatsApp");
        return new TokenResult(accessToken, null, expiresAt,
                String.join(",", WHATSAPP_SCOPES), label, account.phoneNumberId(), metadata);
    }

    private TokenResult instagramToken(String accessToken, Instant expiresAt) {
        MetaApiClient.InstagramProfile account = metaApiClient.instagramProfile(accessToken);
        if (account.userId() == null || account.userId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Instagram did not return a professional account for the authorized user.");
        }
        try {
            metaApiClient.subscribeInstagram(account.userId(), accessToken);
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Instagram connected, but Meta rejected the webhook subscription: "
                            + ex.getMessage(), ex);
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("igUserId", account.userId());
        metadata.put("username", blankToEmpty(account.username()));
        metadata.put("accountType", blankToEmpty(account.accountType()));
        String label = firstNonBlank(account.username(), account.name(), "Instagram");
        return new TokenResult(accessToken, null, expiresAt,
                String.join(",", INSTAGRAM_SCOPES), label, account.userId(), metadata);
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
                             encrypted_refresh_token, token_expires_at, scopes, metadata,
                             connected_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now())
                        on conflict (workspace_id, channel) do update
                        set external_account_id = excluded.external_account_id,
                            account_label = excluded.account_label,
                            encrypted_access_token = excluded.encrypted_access_token,
                            encrypted_refresh_token = coalesce(
                                excluded.encrypted_refresh_token,
                                channel_credentials.encrypted_refresh_token),
                            token_expires_at = excluded.token_expires_at,
                            scopes = excluded.scopes,
                            metadata = excluded.metadata,
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
                        tokens.scopes() == null ? "" : tokens.scopes(),
                        writeMetadata(tokens.metadata()));

                if (!"GMAIL".equals(channel)) {
                    // A reconnect can move the provider account to a different
                    // workspace, so clear this workspace's old routes first.
                    jdbc.update("""
                            delete from channel_account_lookup
                            where workspace_id = ? and channel = ?
                            """, workspaceId, channel);
                    if (tokens.externalAccountId() != null
                            && !tokens.externalAccountId().isBlank()) {
                        saveLookup(channel, tokens.externalAccountId(), workspaceId);
                    }
                    String pageId = tokens.metadata().get("pageId");
                    if (pageId != null && !pageId.isBlank()
                            && !pageId.equals(tokens.externalAccountId())) {
                        saveLookup(channel, pageId, workspaceId);
                    }
                }
            } finally {
                tenantBinding.clear();
            }
        });
    }

    private void saveLookup(String channel, String externalAccountId, String workspaceId) {
        jdbc.update("""
                insert into channel_account_lookup (channel, external_account_id, workspace_id, updated_at)
                values (?, ?, ?, now())
                on conflict (channel, external_account_id) do update
                set workspace_id = excluded.workspace_id, updated_at = now()
                """, channel, externalAccountId, workspaceId);
    }

    private String writeMetadata(Map<String, String> metadata) {
        try {
            return mapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not store channel metadata", ex);
        }
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

    private static String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return "";
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record TokenResult(
            String accessToken,
            String refreshToken,
            Instant expiresAt,
            String scopes,
            String accountLabel,
            String externalAccountId,
            Map<String, String> metadata) {
    }
}
