package com.monotron.support.ai;

import java.util.List;

/**
 * Turns text into dense vectors suitable for pgvector storage and cosine search.
 *
 * <p>Two implementations exist: a remote one talking to any OpenAI compatible
 * endpoint, and a deterministic local one so that the platform stays fully
 * functional without an external API key.
 */
public interface EmbeddingService {

    /** Dimensionality of the produced vectors. Must match the {@code vector} column width. */
    int dimensions();

    float[] embed(String text);

    List<float[]> embedAll(List<String> texts);

    /** Identifier of the backing provider, e.g. {@code openai} or {@code local}. */
    String provider();

    /** Name of the model in use. */
    String model();
}
