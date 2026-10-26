package com.monotron.support.common;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Identifier and slug helpers.
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    /** Standard UUID string, used for every 36 character primary key. */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /** 32 character hex identifier — the format used by {@code apps.id}. */
    public static String appId() {
        return HexFormat.of().formatHex(randomBytes(16));
    }

    /** Opaque high entropy refresh token. */
    public static String refreshToken() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(48));
    }

    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * Builds a URL friendly slug and appends a short random suffix so that
     * slugs stay unique across tenants.
     */
    public static String slug(String input, boolean unique) {
        String base = slugify(input);
        if (base.isEmpty()) {
            base = "item";
        }
        if (!unique) {
            return base;
        }
        return base + "-" + HexFormat.of().formatHex(randomBytes(3));
    }

    public static String slugify(String input) {
        if (input == null) {
            return "";
        }
        String normalised = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalised.length() > 180 ? normalised.substring(0, 180) : normalised;
    }
}
