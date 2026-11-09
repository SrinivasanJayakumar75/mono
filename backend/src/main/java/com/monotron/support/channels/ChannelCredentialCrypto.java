package com.monotron.support.channels;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.monotron.support.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Encrypts OAuth tokens before they are written to PostgreSQL.
 *
 * <p>AES-256-GCM is used with a fresh 12-byte IV for every value. The stored
 * format is {@code v1:<base64url(iv)>:<base64url(ciphertext)>}. The key is
 * derived from {@code monotron.channels.encryption-key}; when that is blank the
 * JWT secret is used so local development still works.
 */
@Component
public class ChannelCredentialCrypto {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec key;

    public ChannelCredentialCrypto(AppProperties properties) {
        String configured = properties.getChannels().getEncryptionKey();
        String secret = configured == null || configured.isBlank()
                ? properties.getJwt().getSecret()
                : configured;
        this.key = new SecretKeySpec(sha256(secret), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + ENCODER.encodeToString(iv) + ":" + ENCODER.encodeToString(encrypted);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not encrypt a channel credential", ex);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!value.startsWith(PREFIX)) {
            throw new IllegalStateException("Unsupported encrypted credential format");
        }
        String[] parts = value.substring(PREFIX.length()).split(":", 2);
        if (parts.length != 2) {
            throw new IllegalStateException("Malformed encrypted credential");
        }
        try {
            byte[] iv = DECODER.decode(parts[0]);
            byte[] encrypted = DECODER.decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not decrypt a channel credential", ex);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
