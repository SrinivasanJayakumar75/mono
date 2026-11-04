package com.monotron.support.ai.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A source referenced by an AI answer.
 */
public record CitationDto(
        String title,
        String sourceType,
        String sourceId,
        String url,
        double score,
        String snippet) {

    /**
     * Collapses multiple chunks from the same document/article into one
     * citation, keeping the best-scoring snippet. Retrieval works on chunks, so
     * without this a single PDF can appear several times under an answer.
     */
    public static List<CitationDto> dedupe(List<CitationDto> citations) {
        if (citations == null || citations.size() <= 1) {
            return citations == null ? List.of() : citations;
        }
        Map<String, CitationDto> bySource = new LinkedHashMap<>();
        for (CitationDto citation : citations) {
            String key = keyOf(citation);
            CitationDto current = bySource.get(key);
            if (current == null || citation.score() > current.score()) {
                bySource.put(key, citation);
            }
        }
        return new ArrayList<>(bySource.values());
    }

    private static String keyOf(CitationDto citation) {
        if (citation.sourceId() != null && !citation.sourceId().isBlank()) {
            return citation.sourceType() + "|" + citation.sourceId();
        }
        if (citation.url() != null && !citation.url().isBlank()) {
            return citation.sourceType() + "|" + citation.url();
        }
        return citation.sourceType() + "|" + citation.title();
    }
}
