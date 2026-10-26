package com.monotron.support.billing.dto;

import jakarta.validation.constraints.NotBlank;

/** Request to start a Razorpay subscription checkout. */
public record CheckoutRequest(@NotBlank(message = "Choose monthly or yearly billing") String interval) {
}
