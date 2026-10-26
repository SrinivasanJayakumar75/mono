package com.monotron.support.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic, dependency free embedding.
 *
 * <p>Text is tokenised into words and word bigrams; every token is hashed into
 * one of the 1536 dimensions of the vector space (the hashing trick) and the
 * resulting vector is L2 normalised so that a plain dot product equals the
 * cosine similarity.
 *
 * <p>The result is a lexical — not semantic — similarity. It is good enough to
 * run the whole retrieval pipeline offline and makes the platform usable
 * without an external model provider, and it is replaced transparently by
 * {@link OpenAiEmbeddingService} as soon as an API key is configured.
 */
public class LocalEmbeddingService implements EmbeddingService {

    private static final int DIMENSIONS = 1536;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "can", "do", "does", "for", "from", "had", "has",
            "have", "he", "her", "his", "how", "i", "if", "in", "into", "is", "it", "its", "me", "my", "no", "not",
            "of", "on", "or", "our", "out", "she", "so", "than", "that", "the", "their", "them", "then", "there",
            "these", "they", "this", "to", "up", "was", "we", "were", "what", "when", "where", "which", "who", "will",
            "with", "would", "you", "your");

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vector;
        }
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return vector;
        }

        // Unigrams carry the bulk of the signal.
        for (String token : tokens) {
            if (STOP_WORDS.contains(token)) {
                continue;
            }
            add(vector, token, 1.0f);
        }

        // Bigrams add a little word order sensitivity.
        for (int i = 0; i + 1 < tokens.size(); i++) {
            add(vector, tokens.get(i) + "_" + tokens.get(i + 1), 0.35f);
        }

        normalise(vector);
        return vector;
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }

    @Override
    public String provider() {
        return "local";
    }

    @Override
    public String model() {
        return "local-hash-1536";
    }

    // ------------------------------------------------------------------

    private static void add(float[] vector, String token, float weight) {
        long hash = fnv1a(token);
        int index = (int) Math.floorMod(hash, (long) vector.length);
        vector[index] += weight;
    }

    private static void normalise(float[] vector) {
        double sumOfSquares = 0;
        for (float value : vector) {
            sumOfSquares += (double) value * value;
        }
        if (sumOfSquares <= 0) {
            return;
        }
        float norm = (float) Math.sqrt(sumOfSquares);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }

    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                current.append(c);
            } else if (current.length() > 0) {
                if (current.length() >= 2) {
                    tokens.add(current.toString());
                }
                current.setLength(0);
            }
        }
        if (current.length() >= 2) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /** FNV-1a, 64 bit. Stable across JVMs, unlike {@link String#hashCode()}. */
    static long fnv1a(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /** Exposed for tests and for the extractive answer composer. */
    public static Set<String> stopWords() {
        return new HashSet<>(STOP_WORDS);
    }
}
