package com.monotron.support.billing;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolarWebhookVerifierTest {

    private static final String BODY = "{\"type\":\"subscription.created\",\"data\":{\"id\":\"sub_1\"}}";

    @Test
    void acceptsStandardWebhooksSignature() throws Exception {
        String key = "standard-webhooks-test-key-32-bytes";
        String secret = "whsec_" + Base64.getEncoder()
                .encodeToString(key.getBytes(StandardCharsets.UTF_8));
        String id = UUID.randomUUID().toString();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = "v1," + hmacBase64(id + "." + timestamp + "." + BODY, key);

        assertTrue(PolarWebhookVerifier.verify(BODY, id, timestamp, signature, secret));
        assertFalse(PolarWebhookVerifier.verify(BODY + " ", id, timestamp, signature, secret));
    }

    @Test
    void acceptsLegacyPolarSecret() throws Exception {
        String secret = "polar_whs_legacy-secret";
        String id = UUID.randomUUID().toString();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = "v1," + hmacBase64(id + "." + timestamp + "." + BODY, secret);

        assertTrue(PolarWebhookVerifier.verify(BODY, id, timestamp, signature, secret));
    }

    @Test
    void rejectsStaleTimestamp() throws Exception {
        String key = "standard-webhooks-test-key-32-bytes";
        String secret = "whsec_" + Base64.getEncoder()
                .encodeToString(key.getBytes(StandardCharsets.UTF_8));
        String id = UUID.randomUUID().toString();
        String timestamp = String.valueOf(Instant.now().minusSeconds(3600).getEpochSecond());
        String signature = "v1," + hmacBase64(id + "." + timestamp + "." + BODY, key);

        assertFalse(PolarWebhookVerifier.verify(BODY, id, timestamp, signature, secret));
    }

    private static String hmacBase64(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
