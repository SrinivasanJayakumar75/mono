package com.monotron.support.auth.dto;

/**
 * A newly minted access/refresh token pair.
 */
public record TokenPair(
        String accessToken,
        String refreshToken,
        long expiresIn) {
}
