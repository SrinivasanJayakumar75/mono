package com.monotron.support.billing;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies Polar webhook signatures.
 *
 * <p>Secrets created on or after 8 September 2026 use the Standard Webhooks
 * scheme: the secret after {@code whsec_} is base64, and the signed content is
 * {@code webhook-id + "." + webhook-timestamp + "." + body}. Older Polar
 * secrets signed the same content with the UTF-8 bytes of the full secret. We
 * accept both so an existing endpoint keeps working after upgrading.
 */
final class PolarWebhookVerifier {

    private static final long TOLERANCE_SECONDS = 300;

    private PolarWebhookVerifier() {
    }

    static boolean verify(String body, String webhookId, String timestamp,
            String signatureHeader, String secret) {
        if (!hasText(body) || !hasText(webhookId) || !hasText(timestamp)
                || !hasText(signatureHeader) || !hasText(secret)) {
            return false;
        }

        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException ex) {
            return false;
        }
        if (Math.abs(Instant.now().getEpochSecond() - timestampSeconds) > TOLERANCE_SECONDS) {
            return false;
        }

        String signedContent = webhookId + "." + timestamp + "." + body;
        List<String> expected = new ArrayList<>();

        String rawSecret = secret.startsWith("whsec_")
                ? secret.substring("whsec_".length())
                : secret;
        byte[] decoded = decodeBase64(rawSecret);
        if (decoded != null) {
            expected.add(hmacBase64(signedContent, decoded));
        }
        expected.add(hmacBase64(signedContent, secret.getBytes(StandardCharsets.UTF_8)));
        if (!rawSecret.equals(secret)) {
            expected.add(hmacBase64(signedContent, rawSecret.getBytes(StandardCharsets.UTF_8)));
        }

        for (String part : signatureHeader.trim().split("\\s+")) {
            int comma = part.indexOf(',');
            if (comma <= 0) {
                continue;
            }
            String version = part.substring(0, comma);
            if (!"v1".equals(version)) {
                continue;
            }
            String actual = part.substring(comma + 1);
            for (String candidate : expected) {
                if (BillingCrypto.constantTimeEquals(candidate, actual)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String hmacBase64(String payload, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to verify the Polar webhook signature", ex);
        }
    }

    private static byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException standard) {
            try {
                return Base64.getUrlDecoder().decode(value);
            } catch (IllegalArgumentException urlSafe) {
                return null;
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
