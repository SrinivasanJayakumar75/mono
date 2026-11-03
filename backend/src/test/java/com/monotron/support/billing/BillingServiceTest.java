package com.monotron.support.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monotron.support.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BillingServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PolarClient polar = mock(PolarClient.class);
    private final BillingService service = new BillingService(
            mock(JdbcTemplate.class), mapper, polar, new AppProperties());

    @Test
    void recoversSubscriptionFromCustomerWhenCheckoutHasNoSubscriptionId() {
        when(polar.listSubscriptionsByCustomer("cus_1")).thenReturn(Map.of(
                "items", List.of(
                        Map.of("id", "sub_other", "checkout_id", "co_other",
                                "metadata", Map.of("workspace_id", "ws_1")),
                        Map.of("id", "sub_1", "checkout_id", "co_1",
                                "metadata", Map.of("workspace_id", "ws_1")))));
        JsonNode checkout = mapper.valueToTree(Map.of("customer_id", "cus_1"));

        String subscriptionId = service.subscriptionFromCheckoutCustomer(checkout, "co_1", "ws_1");

        assertThat(subscriptionId).isEqualTo("sub_1");
    }

    @Test
    void ignoresSubscriptionForAnotherWorkspace() {
        when(polar.listSubscriptionsByCustomer("cus_1")).thenReturn(Map.of(
                "items", List.of(Map.of("id", "sub_1", "checkout_id", "co_1",
                        "metadata", Map.of("workspace_id", "ws_2")))));
        JsonNode checkout = mapper.valueToTree(Map.of("customer_id", "cus_1"));

        String subscriptionId = service.subscriptionFromCheckoutCustomer(checkout, "co_1", "ws_1");

        assertThat(subscriptionId).isNull();
    }
}
