package com.monotron.support.channels;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.monotron.support.config.AppProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Small Meta Graph API client for WhatsApp Cloud API and Instagram Messaging.
 *
 * <p>The client is stateless. Tenant access tokens are always passed in by
 * the caller and are never cached here. WhatsApp uses
 * {@code graph.facebook.com}. Instagram uses {@code graph.facebook.com} with
 * Unified Login and {@code graph.instagram.com} with the legacy Instagram
 * Login flow.
 */
@Component
public class MetaApiClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final AppProperties properties;
    private final RestClient restClient;

    public MetaApiClient(AppProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Exchanges a still-valid long-lived Meta user token for a fresh one.
     */
    public MetaToken refreshUserToken(String accessToken) {
        AppProperties.Channels.Meta meta = properties.getChannels().getMeta();
        Map<String, Object> body = getWithoutAuth("/oauth/access_token", Map.of(
                "grant_type", "fb_exchange_token",
                "client_id", meta.getAppId(),
                "client_secret", meta.getAppSecret(),
                "fb_exchange_token", accessToken));
        String refreshed = string(body.get("access_token"));
        if (refreshed == null || refreshed.isBlank()) {
            throw new IllegalStateException("Meta did not return a refreshed access token.");
        }
        return new MetaToken(refreshed, expiry(body.get("expires_in")));
    }

    /**
     * Reads the Instagram professional account behind an Instagram Login
     * access token. {@code user_id} is the account id Meta uses in webhook
     * payloads; {@code id} is the app-scoped fallback.
     */
    public InstagramProfile instagramProfile(String accessToken) {
        Map<String, Object> body = getOnHost("graph.instagram.com", versionPath("/me"),
                accessToken, Map.of("fields", "user_id,username,name,account_type"));
        String userId = string(body.get("user_id"));
        if (userId == null || userId.isBlank()) {
            userId = string(body.get("id"));
        }
        return new InstagramProfile(
                userId,
                string(body.get("username")),
                string(body.get("name")),
                string(body.get("account_type")));
    }

    /**
     * Enables Instagram message notifications for the connected account.
     * Dashboard field selection alone does not create the per-account
     * subscription.
     */
    public void subscribeInstagram(String igUserId, String accessToken) {
        postOnHost("graph.instagram.com", versionPath(igUserId + "/subscribed_apps"),
                Map.of("subscribed_fields", "messages"), accessToken);
    }

    /**
     * Reads the Instagram professional account shared through Unified Login.
     * With a Business Integration System User token {@code /me} is the system
     * user, so the account is resolved through
     * {@code /me/assigned_instagram_accounts}.
     */
    public InstagramUnifiedAccount instagramUnifiedProfile(String accessToken) {
        Map<String, Object> me = get(accessToken, "/me", Map.of("fields", "client_business_id"));
        String clientBusinessId = string(me.get("client_business_id"));

        Map<String, Object> body = get(accessToken, "/me/assigned_instagram_accounts",
                Map.of("fields", "id,username", "limit", "100"));
        List<Map<String, Object>> accounts = list(body.get("data"));
        if (accounts.isEmpty()) {
            return new InstagramUnifiedAccount(null, null, clientBusinessId);
        }
        Map<String, Object> account = accounts.get(0);
        return new InstagramUnifiedAccount(
                string(account.get("id")),
                string(account.get("username")),
                clientBusinessId);
    }

    /**
     * Enables Instagram message notifications for a Unified Login account.
     * The per-account subscription is required even when the dashboard already
     * subscribes to the {@code messages} field.
     */
    public void subscribeInstagramUnified(String igUserId, String accessToken) {
        postOnHost("graph.facebook.com", versionPath(igUserId + "/subscribed_apps"),
                Map.of("subscribed_fields", "messages"), accessToken);
    }

    /**
     * Finds the first WhatsApp Cloud API phone number available to the user.
     */
    public List<WhatsAppAccount> whatsappAccounts(String userAccessToken) {
        Map<String, Object> body = get(userAccessToken, "/me/businesses", Map.of(
                "fields", "id,name,owned_whatsapp_business_accounts"
                        + "{id,name,phone_numbers{id,display_phone_number,verified_name}}",
                "limit", "100"));
        List<WhatsAppAccount> accounts = new ArrayList<>();
        for (Map<String, Object> business : list(body.get("data"))) {
            Map<String, Object> owned = map(business.get("owned_whatsapp_business_accounts"));
            for (Map<String, Object> waba : list(owned.get("data"))) {
                Map<String, Object> phoneNumbers = map(waba.get("phone_numbers"));
                String wabaId = string(waba.get("id"));
                if (wabaId == null || wabaId.isBlank()) {
                    continue;
                }
                for (Map<String, Object> phone : list(phoneNumbers.get("data"))) {
                    String phoneNumberId = string(phone.get("id"));
                    if (phoneNumberId == null || phoneNumberId.isBlank()) {
                        continue;
                    }
                    accounts.add(new WhatsAppAccount(
                            string(business.get("id")),
                            string(business.get("name")),
                            wabaId,
                            string(waba.get("name")),
                            phoneNumberId,
                            string(phone.get("display_phone_number")),
                            string(phone.get("verified_name"))));
                }
            }
        }
        return accounts;
    }

    /**
     * Subscribes the Meta app to the WhatsApp business account. WhatsApp webhook
     * delivery requires this step in addition to the app-level callback URL.
     */
    public void subscribeWhatsApp(String wabaId, String accessToken) {
        post(wabaId + "/subscribed_apps", Map.of(), accessToken);
    }

    public void sendWhatsAppText(String phoneNumberId, String accessToken, String to, String body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", to);
        payload.put("type", "text");
        payload.put("text", Map.of("preview_url", false, "body", body));
        post(phoneNumberId + "/messages", payload, accessToken);
    }

    /**
     * Sends an Instagram DM with an Instagram Login user access token. The
     * customer id is the Instagram-scoped id from the webhook payload.
     */
    public void sendInstagramText(String accessToken, String recipientId, String body) {
        Map<String, Object> payload = Map.of(
                "recipient", Map.of("id", recipientId),
                "message", Map.of("text", body));
        postJsonOnHost("graph.instagram.com", versionPath("/me/messages"), payload, accessToken);
    }

    /**
     * Sends an Instagram DM through Unified Login. The token belongs to the
     * business integration system user, so the Instagram account id must be in
     * the request path instead of {@code /me}.
     */
    public void sendInstagramUnifiedText(String igUserId, String accessToken,
                                         String recipientId, String body) {
        Map<String, Object> payload = Map.of(
                "recipient", Map.of("id", recipientId),
                "message", Map.of("text", body));
        postJsonOnHost("graph.facebook.com", versionPath(igUserId + "/messages"),
                payload, accessToken);
    }

    /**
     * Instagram long-lived user tokens are valid for 60 days and can be
     * refreshed once they are at least 24 hours old.
     */
    public MetaToken refreshInstagramToken(String accessToken) {
        Map<String, Object> body = getOnHost("graph.instagram.com", "/refresh_access_token",
                Map.of("grant_type", "ig_refresh_token", "access_token", accessToken));
        String refreshed = string(body.get("access_token"));
        if (refreshed == null || refreshed.isBlank()) {
            throw new IllegalStateException("Instagram did not return a refreshed access token.");
        }
        return new MetaToken(refreshed, expiry(body.get("expires_in")));
    }

    private Map<String, Object> get(String accessToken, String path, Map<String, ?> query) {
        return getOnHost("graph.facebook.com", versionPath(path), accessToken, query);
    }

    private Map<String, Object> getWithoutAuth(String path, Map<String, ?> query) {
        return getOnHost("graph.facebook.com", versionPath(path), null, query);
    }

    private Map<String, Object> getOnHost(String host, String path, Map<String, ?> query) {
        return getOnHost(host, path, null, query);
    }

    private Map<String, Object> getOnHost(String host, String path, String accessToken,
                                          Map<String, ?> query) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.scheme("https").host(host).path(path);
                        query.forEach((key, value) -> uriBuilder.queryParam(key, value));
                        return uriBuilder.build();
                    })
                    .headers(headers -> {
                        if (accessToken != null) {
                            headers.setBearerAuth(accessToken);
                        }
                    })
                    .retrieve()
                    .body(MAP_TYPE);
            return body == null ? Map.of() : body;
        } catch (RestClientException ex) {
            throw failure("Meta API request failed", ex);
        }
    }

    private Map<String, Object> post(String path, Object payload, String accessToken) {
        return postJsonOnHost("graph.facebook.com", versionPath(path), payload, accessToken);
    }

    private Map<String, Object> postJsonOnHost(String host, String path, Object payload,
                                               String accessToken) {
        try {
            Map<String, Object> body = restClient.post()
                    .uri(uriBuilder -> uriBuilder.scheme("https").host(host)
                            .path(path).build())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(MAP_TYPE);
            return body == null ? Map.of() : body;
        } catch (RestClientException ex) {
            throw failure("Meta API request failed", ex);
        }
    }

    private Map<String, Object> postOnHost(String host, String path, Map<String, ?> query,
                                           String accessToken) {
        try {
            Map<String, Object> body = restClient.post()
                    .uri(uriBuilder -> {
                        uriBuilder.scheme("https").host(host).path(path);
                        query.forEach((key, value) -> uriBuilder.queryParam(key, value));
                        return uriBuilder.build();
                    })
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(MAP_TYPE);
            return body == null ? Map.of() : body;
        } catch (RestClientException ex) {
            throw failure("Meta API request failed", ex);
        }
    }

    private String versionPath(String path) {
        String version = properties.getChannels().getMeta().getGraphVersion();
        return "/" + (version == null || version.isBlank() ? "v23.0" : version)
                + (path.startsWith("/") ? path : "/" + path);
    }

    private static RuntimeException failure(String message, RestClientException ex) {
        if (ex instanceof RestClientResponseException response) {
            String body = response.getResponseBodyAsString();
            String detail = body == null || body.isBlank() ? "" : " " + body;
            return new IllegalStateException(
                    message + " (HTTP " + response.getStatusCode().value() + ")" + detail, ex);
        }
        return new IllegalStateException(message, ex);
    }

    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) raw;
            return map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item instanceof Map<?, ?> map) {
                items.add((Map<String, Object>) map);
            }
        }
        return items;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
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

    public record MetaToken(String accessToken, Instant expiresAt) {
    }

    public record InstagramProfile(
            String userId,
            String username,
            String name,
            String accountType) {
    }

    public record InstagramUnifiedAccount(
            String igUserId,
            String username,
            String clientBusinessId) {
    }

    public record WhatsAppAccount(
            String businessId,
            String businessName,
            String wabaId,
            String wabaName,
            String phoneNumberId,
            String displayPhoneNumber,
            String verifiedName) {
    }
}
