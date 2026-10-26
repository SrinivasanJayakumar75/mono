package com.monotron.support.ai.dto;

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
}
