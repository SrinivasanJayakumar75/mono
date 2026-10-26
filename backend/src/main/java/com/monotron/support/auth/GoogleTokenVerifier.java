package com.monotron.support.auth;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.monotron.support.common.ApiException;
import com.monotron.support.common.UnauthorizedException;
import com.monotron.support.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Verifies the ID token produced by Google Identity Services.
 *
 * <p>The browser receives a signed JWT from Google and posts it to
 * {@code POST /api/auth/google}. We validate the signature, audience, issuer
 * and expiry here before trusting the email address. The client id is public,
 * so it is also exposed to the frontend through the auth config endpoint.
 */
@Component
public class GoogleTokenVerifier {

    private final String clientId;
    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(AppProperties properties) {
        String configured = properties.getGoogle().getClientId();
        this.clientId = configured == null || configured.isBlank() ? null : configured.trim();
        this.verifier = this.clientId == null
                ? null
                : new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(Collections.singletonList(this.clientId))
                        .build();
    }

    public boolean isConfigured() {
        return verifier != null;
    }

    public String clientId() {
        return clientId;
    }

    /**
     * @return the verified Google identity
     * @throws ApiException 503 when Google sign-in is not configured, 401 when
     *                      the token is invalid or the email is unverified
     */
    public GoogleUser verify(String credential) {
        if (verifier == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google sign-in is not configured. Set GOOGLE_CLIENT_ID on the backend.");
        }
        try {
            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw new UnauthorizedException("Google sign-in could not be verified");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new UnauthorizedException("Your Google email address is not verified");
            }
            String email = payload.getEmail();
            if (email == null || email.isBlank()) {
                throw new UnauthorizedException("Google did not return an email address");
            }
            String name = payload.get("name") instanceof String value && !value.isBlank()
                    ? value.trim()
                    : defaultName(email);
            return new GoogleUser(email, name);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Google sign-in could not be verified");
        }
    }

    private static String defaultName(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    public record GoogleUser(String email, String name) {
    }
}
