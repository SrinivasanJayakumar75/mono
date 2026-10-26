package com.monotron.support.ai;

import java.util.ArrayList;
import java.util.List;

import com.monotron.support.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * Embeddings from any OpenAI compatible {@code /embeddings} endpoint.
 */
public class OpenAiEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingService.class);

    private final RestClient restClient;
    private final String model;
    private final int dimensions;

    public OpenAiEmbeddingService(AppProperties properties) {
        AppProperties.Ai.OpenAi config = properties.getAi().getOpenai();
        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.model = config.getEmbeddingModel();
        this.dimensions = 1536;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public float[] embed(String text) {
        List<float[]> all = embedAll(List.of(text == null ? "" : text));
        return all.isEmpty() ? new float[dimensions] : all.get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        EmbeddingRequest request = new EmbeddingRequest(model, texts);
        EmbeddingResponse response;
        try {
            response = restClient.post()
                    .uri("/embeddings")
                    .body(request)
                    .retrieve()
                    .body(EmbeddingResponse.class);
        } catch (RuntimeException ex) {
            log.warn("Embedding request failed, falling back to the local embedder: {}", ex.getMessage());
            return new LocalEmbeddingService().embedAll(texts);
        }
        if (response == null || response.data() == null || response.data().isEmpty()) {
            return new LocalEmbeddingService().embedAll(texts);
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (EmbeddingData data : response.data()) {
            vectors.add(toArray(data.embedding()));
        }
        while (vectors.size() < texts.size()) {
            vectors.add(new float[dimensions]);
        }
        return vectors;
    }

    @Override
    public String provider() {
        return "openai";
    }

    @Override
    public String model() {
        return model;
    }

    private static float[] toArray(List<Double> values) {
        float[] vector = new float[1536];
        if (values == null) {
            return vector;
        }
        for (int i = 0; i < Math.min(values.size(), vector.length); i++) {
            vector[i] = values.get(i).floatValue();
        }
        return vector;
    }

    private record EmbeddingRequest(String model, List<String> input) {
    }

    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(Integer index, List<Double> embedding) {
    }
}
