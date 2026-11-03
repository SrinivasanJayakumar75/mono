package com.monotron.support.billing.dto;

/**
 * Public checkout data returned to the browser. The browser is redirected to
 * {@code url}; the Polar access token never leaves the server.
 */
public record CheckoutResponse(
        String checkoutId,
        String url,
        String interval,
        int amount,
        String currency) {
}
