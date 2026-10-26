package com.monotron.support.billing;

import java.time.Duration;
import java.util.LinkedHashMap;
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
 * Small Razorpay REST client. We call the documented HTTPS API directly so the
 * app does not need a provider SDK; the key secret is only used for HTTP Basic
 * auth and signature checks on the server.
 */
@Component
public class RazorpayClient {

    private static final Logger log = LoggerFactory.getLogger(RazorpayClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final AppProperties.Billing properties;
    private final RestClient restClient;

    public RazorpayClient(AppProperties appProperties) {
        this.properties = appProperties.getBilling();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = properties.getTimeout();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(
                        properties.getKeyId(), properties.getKeySecret()))
                .requestFactory(factory)
                .build();
    }

    public boolean configured() {
        return hasText(properties.getKeyId()) && hasText(properties.getKeySecret());
    }

    public boolean webhookConfigured() {
        return hasText(properties.getWebhookSecret());
    }

    public boolean testMode() {
        return properties.getKeyId().startsWith("rzp_test_");
    }

    public Map<String, Object> createPlan(String planKey, int amount, String currency) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", "Monotron " + label(planKey));
        item.put("amount", amount);
        item.put("currency", currency);
        item.put("description", "Monotron " + label(planKey) + " subscription");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("period", period(planKey));
        body.put("interval", 1);
        body.put("item", item);
        body.put("notes", Map.of("plan_key", planKey));

        return post("/plans", body, "create plan");
    }

    public Map<String, Object> createSubscription(String planId, int totalCount, String email,
            Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plan_id", planId);
        body.put("total_count", totalCount);
        body.put("quantity", 1);
        body.put("customer_notify", 1);
        body.put("notes", notes);
        if (hasText(email)) {
            body.put("notify_info", Map.of("notify_email", email));
        }
        return post("/subscriptions", body, "create subscription");
    }

    public Map<String, Object> fetchSubscription(String subscriptionId) {
        return get("/subscriptions/{id}", subscriptionId, "fetch subscription");
    }

    public Map<String, Object> cancelSubscription(String subscriptionId, boolean atCycleEnd) {
        return post("/subscriptions/{id}/cancel", subscriptionId,
                Map.of("cancel_at_cycle_end", atCycleEnd ? 1 : 0), "cancel subscription");
    }

    public Map<String, Object> fetchPayment(String paymentId) {
        return get("/payments/{id}", paymentId, "fetch payment");
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
            log.warn("Razorpay {} failed with {}: {}", operation, ex.getStatusCode(),
                    ex.getResponseBodyAsString());
            throw providerError(operation);
        } catch (RestClientException ex) {
            log.warn("Razorpay {} could not be reached", operation, ex);
            throw providerError(operation);
        }
    }

    private Map<String, Object> post(String uri, String id, Object body, String operation) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri(uri, id)
                    .body(body)
                    .retrieve()
                    .body(MAP_TYPE);
            if (response == null) {
                throw providerError(operation);
            }
            return response;
        } catch (RestClientResponseException ex) {
            log.warn("Razorpay {} failed with {}: {}", operation, ex.getStatusCode(),
                    ex.getResponseBodyAsString());
            throw providerError(operation);
        } catch (RestClientException ex) {
            log.warn("Razorpay {} could not be reached", operation, ex);
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
            log.warn("Razorpay {} failed with {}: {}", operation, ex.getStatusCode(),
                    ex.getResponseBodyAsString());
            throw providerError(operation);
        } catch (RestClientException ex) {
            log.warn("Razorpay {} could not be reached", operation, ex);
            throw providerError(operation);
        }
    }

    private ApiException providerError(String operation) {
        return new ApiException(HttpStatus.BAD_GATEWAY,
                "Razorpay could not " + operation + ". Please try again.");
    }

    private static String period(String planKey) {
        return "YEARLY".equals(planKey) ? "yearly" : "monthly";
    }

    private static String label(String planKey) {
        return "YEARLY".equals(planKey) ? "Yearly" : "Monthly";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
