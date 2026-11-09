package com.monotron.support.channels;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.monotron.support.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Signs the OAuth {@code state} parameter so the callback can recover the
 * workspace and channel without trusting query-string input.
 */
@Component
public class ChannelOAuthStateService {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Duration TTL = Duration.ofMinutes(10);

    private final SecretKeySpec key;

    public ChannelOAuthStateService(AppProperties properties) {
        String configured = properties.getChannels().getStateSecret();
        String secret = configured == null || configured.isBlank()
                ? properties.getJwt().getSecret()
                : configured;
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String issue(String workspaceId, String channel) {
        long expiresAt = Instant.now().plus(TTL).getEpochSecond();
        String payload = workspaceId + "|" + channel + "|" + expiresAt;
        String encoded = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + ENCODER.encodeToString(sign(encoded));
    }

    public Optional<State> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String[] parts = value.split("\\.", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            byte[] expected = sign(parts[0]);
            byte[] actual = DECODER.decode(parts[1]);
            if (!MessageDigest.isEqual(expected, actual)) {
                return Optional.empty();
            }
            String payload = new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            String[] fields = payload.split("\\|", 3);
            if (fields.length != 3) {
                return Optional.empty();
            }
            long expiresAt = Long.parseLong(fields[2]);
            if (Instant.now().getEpochSecond() > expiresAt) {
                return Optional.empty();
            }
            return Optional.of(new State(fields[0], fields[1], Instant.ofEpochSecond(expiresAt)));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign the OAuth state", ex);
        }
    }

    public record State(String workspaceId, String channel, Instant expiresAt) {
    }
}
