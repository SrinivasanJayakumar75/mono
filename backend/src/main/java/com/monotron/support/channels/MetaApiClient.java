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
 * <p>The client is stateless. Tenant access/page tokens are always passed in by
 * the caller and are never cached here.
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
     * Finds Facebook Pages that have a linked Instagram professional account.
     * The returned page access token is the token used for Instagram messaging.
     */
    public List<InstagramAccount> instagramAccounts(String userAccessToken) {
        Map<String, Object> body = get(userAccessToken, "/me/accounts", Map.of(
                "fields", "id,name,access_token,instagram_business_account{id,username,name}",
                "limit", "100"));
        List<InstagramAccount> accounts = new ArrayList<>();
        for (Map<String, Object> page : list(body.get("data"))) {
            Map<String, Object> instagram = map(page.get("instagram_business_account"));
            String instagramId = string(instagram.get("id"));
            String pageId = string(page.get("id"));
            if (instagramId == null || instagramId.isBlank()
                    || pageId == null || pageId.isBlank()) {
                continue;
            }
            String pageToken = string(page.get("access_token"));
            if (pageToken == null || pageToken.isBlank()) {
                continue;
            }
            accounts.add(new InstagramAccount(
                    pageId,
                    string(page.get("name")),
                    pageToken,
                    instagramId,
                    string(instagram.get("username")),
                    string(instagram.get("name"))));
        }
        return accounts;
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
     * Facebook Login for Business returns a Page token. Instagram Messaging
     * sends through the linked Page, using the customer's Instagram-scoped id.
     */
    public void sendInstagramText(String pageId, String pageAccessToken, String recipientId, String body) {
        Map<String, Object> payload = Map.of(
                "recipient", Map.of("id", recipientId),
                "message", Map.of("text", body));
        post(pageId + "/messages", payload, pageAccessToken);
    }

    private Map<String, Object> get(String accessToken, String path, Map<String, ?> query) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.scheme("https").host("graph.facebook.com")
                                .path(versionPath(path));
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

    private Map<String, Object> getWithoutAuth(String path, Map<String, ?> query) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.scheme("https").host("graph.facebook.com")
                                .path(versionPath(path));
                        query.forEach((key, value) -> uriBuilder.queryParam(key, value));
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(MAP_TYPE);
            return body == null ? Map.of() : body;
        } catch (RestClientException ex) {
            throw failure("Meta token request failed", ex);
        }
    }

    private Map<String, Object> post(String path, Object payload, String accessToken) {
        try {
            Map<String, Object> body = restClient.post()
                    .uri(uriBuilder -> uriBuilder.scheme("https").host("graph.facebook.com")
                            .path(versionPath(path)).build())
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

    public record InstagramAccount(
            String pageId,
            String pageName,
            String pageAccessToken,
            String instagramId,
            String username,
            String displayName) {
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
