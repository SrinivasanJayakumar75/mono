package com.monotron.support.ai.dto;

import java.util.List;

/**
 * A knowledge base passage that matched a query.
 */
public record SearchHitDto(
        String sourceType,
        String sourceId,
        String title,
        String url,
        String content,
        double score,
        Integer chunkIndex) {
}
