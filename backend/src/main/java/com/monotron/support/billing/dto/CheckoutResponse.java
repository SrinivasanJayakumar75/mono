package com.monotron.support.billing.dto;

/**
 * Public checkout configuration returned to the browser. The Razorpay key id
 * is safe to expose; the key secret never leaves the server.
 */
public record CheckoutResponse(
        String subscriptionId,
        String keyId,
        String shortUrl,
        String name,
        String description,
        String prefillName,
        String prefillEmail,
        String interval,
        int amount,
        String currency) {
}
