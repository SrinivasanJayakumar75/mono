package com.monotron.support.auth.dto;

/**
 * Public Google sign-in configuration for the auth pages.
 *
 * @param enabled  true when the backend has a Google OAuth client id
 * @param clientId public OAuth 2.0 Web client id, or null when disabled
 */
public record GoogleAuthConfig(boolean enabled, String clientId) {
}
