package com.monotron.support.billing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** Razorpay Checkout success payload, verified server-side before activation. */
public record VerifyPaymentRequest(
        @JsonProperty("razorpay_payment_id") @NotBlank String razorpayPaymentId,
        @JsonProperty("razorpay_subscription_id") @NotBlank String razorpaySubscriptionId,
        @JsonProperty("razorpay_signature") @NotBlank String razorpaySignature) {
}
