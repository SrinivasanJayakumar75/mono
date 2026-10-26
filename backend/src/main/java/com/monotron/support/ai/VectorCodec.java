package com.monotron.support.ai;

import java.util.Locale;

/**
 * Serialises vectors into the textual literal understood by pgvector.
 */
public final class VectorCodec {

    private VectorCodec() {
    }

    /**
     * Renders a vector as {@code [0.1,0.2,...]}. The value is bound as a
     * parameter and cast with {@code ::vector} on the SQL side.
     */
    public static String toLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 12 + 2);
        builder.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(format(vector[i]));
        }
        builder.append(']');
        return builder.toString();
    }

    private static String format(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return "0";
        }
        return String.format(Locale.ROOT, "%.7f", value);
    }
}
