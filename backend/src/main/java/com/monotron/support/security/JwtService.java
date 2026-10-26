package com.monotron.support.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import com.monotron.support.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies HS256 JSON Web Tokens.
 *
 * <p>Access tokens are short lived and carry the tenant scope in the {@code wid}
 * claim. The tenant scope is what the {@link TenantTransactionFilter} publishes
 * to PostgreSQL, so it is signed and cannot be tampered with by a client.
 */
@Service
public class JwtService {

    public static final String CLAIM_WORKSPACE = "wid";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NAME = "name";

    private final AppProperties properties;
    private final SecretKey key;

    public JwtService(AppProperties properties) {
        this.properties = properties;
        byte[] secret = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "monotron.jwt.secret must be at least 32 bytes for HS256 signing");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String issueAccessToken(String userId, String email, String name, String workspaceId, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getJwt().getAccessTokenTtl());
        var builder = Jwts.builder()
                .issuer(properties.getJwt().getIssuer())
                .subject(userId)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_NAME, name)
                .claim(CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry));
        if (workspaceId != null && !workspaceId.isBlank()) {
            builder.claim(CLAIM_WORKSPACE, workspaceId);
        }
        return builder.signWith(key).compact();
    }

    /**
     * Parses and verifies a token.
     *
     * @return the claims, or {@code null} when the token is missing, malformed, expired or tampered with
     */
    public Claims parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getJwt().getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    public Duration accessTokenTtl() {
        return properties.getJwt().getAccessTokenTtl();
    }

    public Duration refreshTokenTtl() {
        return properties.getJwt().getRefreshTokenTtl();
    }
}
