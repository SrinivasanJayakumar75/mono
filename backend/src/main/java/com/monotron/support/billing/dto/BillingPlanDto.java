package com.monotron.support.billing.dto;

/**
 * A purchasable subscription interval. {@code amount} is in the smallest
 * currency unit (paise for INR).
 */
public record BillingPlanDto(
        String interval,
        String label,
        int amount,
        String currency,
        String description,
        boolean recommended) {
}
