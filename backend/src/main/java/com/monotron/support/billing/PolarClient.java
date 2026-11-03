package com.monotron.support.billing;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.monotron.support.common.ApiException;
import com.monotron.support.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Small Polar REST client. We call the documented HTTPS API directly so the
 * app does not need a provider SDK; the organization access token is only used
 * as a Bearer token on the server.
 */
@Component
public class PolarClient {

    private static final Logger log = LoggerFactory.getLogger(PolarClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final AppProperties.Billing properties;
    private final RestClient restClient;

    public PolarClient(AppProperties appProperties) {
        this.properties = appProperties.getBilling();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = properties.getTimeout();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        String baseUrl = properties.getApiBaseUrl();
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> {
                    if (hasText(properties.getAccessToken())) {
                        headers.setBearerAuth(properties.getAccessToken());
                    }
                })
                .requestFactory(factory)
                .build();
    }

    public boolean configured() {
        return hasText(properties.getAccessToken())
                && hasText(properties.getMonthlyProductId())
                && hasText(properties.getYearlyProductId());
    }

    public boolean configuredFor(String interval) {
        return hasText(properties.getAccessToken()) && hasText(productId(interval));
    }

    public boolean webhookConfigured() {
        return hasText(properties.getWebhookSecret());
    }

    public boolean testMode() {
        String baseUrl = properties.getApiBaseUrl();
        return properties.isTestMode()
                || (baseUrl != null && baseUrl.toLowerCase().contains("sandbox"));
    }

    public String productId(String interval) {
        return "YEARLY".equals(interval)
                ? properties.getYearlyProductId()
                : properties.getMonthlyProductId();
    }

    public Map<String, Object> createCheckout(String interval, String email, String workspaceId,
            String userId) {
        String productId = productId(interval);
        if (!hasText(productId)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Polar " + label(interval).toLowerCase()
                            + " product is not configured yet. Add "
                            + envName(interval) + " to backend/.env.");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workspace_id", workspaceId);
        metadata.put("user_id", userId);
        metadata.put("plan", interval);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("products", List.of(productId));
        body.put("success_url", successUrl());
        body.put("return_url", properties.getSuccessUrl());
        body.put("external_customer_id", workspaceId);
        body.put("metadata", metadata);
        body.put("allow_trial", false);
        if (hasText(properties.getCurrency())) {
            body.put("currency", properties.getCurrency().toLowerCase(Locale.ROOT));
        }
        if (hasText(email)) {
            body.put("customer_email", email);
        }

        return post("/checkouts/", body, "create checkout");
    }

    public Map<String, Object> fetchCheckout(String checkoutId) {
        return get("/checkouts/{id}", checkoutId, "fetch checkout");
    }

    public Map<String, Object> fetchSubscription(String subscriptionId) {
        return get("/subscriptions/{id}", subscriptionId, "fetch subscription");
    }

    /**
     * Polar can return a succeeded checkout before its {@code subscription_id}
     * is populated. Listing the customer's subscriptions lets the return page
     * recover the subscription from the checkout id instead of making the user
     * wait for a webhook.
     */
    public Map<String, Object> listSubscriptionsByCustomer(String customerId) {
        return get("/subscriptions/?customer_id={id}&limit=100", customerId,
                "list subscriptions");
    }

    public Map<String, Object> cancelSubscription(String subscriptionId) {
        return patch("/subscriptions/{id}", subscriptionId,
                Map.of("cancel_at_period_end", true), "cancel subscription");
    }

    private String successUrl() {
        String base = properties.getSuccessUrl();
        if (!hasText(base)) {
            base = "http://localhost:3000/billing";
        }
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "checkout=success&checkout_id={CHECKOUT_ID}";
    }

    private Map<String, Object> post(String uri, Object body, String operation) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri(uri)
                    .body(body)
                    .retrieve()
                    .body(MAP_TYPE);
            if (response == null) {
                throw providerError(operation);
            }
            return response;
        } catch (RestClientResponseException ex) {
            log.warn("Polar {} failed with {}: {}", operation, ex.getStatusCode(),
                    ex.getResponseBodyAsString());
            throw providerError(operation);
        } catch (RestClientException ex) {
            log.warn("Polar {} could not be reached", operation, ex);
            throw providerError(operation);
        }
    }

    private Map<String, Object> patch(String uri, String id, Object body, String operation) {
        try {
            Map<String, Object> response = restClient.patch()
                    .uri(uri, id)
                    .body(body)
                    .retrieve()
                    .body(MAP_TYPE);
            if (response == null) {
                throw providerError(operation);
            }
            return response;
        } catch (RestClientResponseException ex) {
            log.warn("Polar {} failed with {}: {}", operation, ex.getStatusCode(),
                    ex.getResponseBodyAsString());
            throw providerError(operation);
        } catch (RestClientException ex) {
            log.warn("Polar {} could not be reached", operation, ex);
            throw providerError(operation);
        }
    }

    private Map<String, Object> get(String uri, String id, String operation) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uri, id)
                    .retrieve()
                    .body(MAP_TYPE);
            if (response == null) {
                throw providerError(operation);
            }
            return response;
        } catch (RestClientResponseException ex) {
            log.warn("Polar {} failed with {}: {}", operation, ex.getStatusCode(),
                    ex.getResponseBodyAsString());
            throw providerError(operation);
        } catch (RestClientException ex) {
            log.warn("Polar {} could not be reached", operation, ex);
            throw providerError(operation);
        }
    }

    private ApiException providerError(String operation) {
        return new ApiException(HttpStatus.BAD_GATEWAY,
                "Polar could not " + operation + ". Please try again.");
    }

    private static String label(String interval) {
        return "YEARLY".equals(interval) ? "Yearly" : "Monthly";
    }

    private static String envName(String interval) {
        return "YEARLY".equals(interval) ? "POLAR_YEARLY_PRODUCT_ID" : "POLAR_MONTHLY_PRODUCT_ID";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
