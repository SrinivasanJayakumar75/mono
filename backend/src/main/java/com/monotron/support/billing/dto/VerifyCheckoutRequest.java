package com.monotron.support.billing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** Polar checkout id returned on the success redirect. */
public record VerifyCheckoutRequest(
        @JsonProperty("checkout_id") @NotBlank String checkoutId) {
}
