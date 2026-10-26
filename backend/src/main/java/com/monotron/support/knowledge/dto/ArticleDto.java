package com.monotron.support.knowledge.dto;

import java.time.Instant;

/**
 * A knowledge base article.
 */
public record ArticleDto(
        String id,
        String title,
        String slug,
        String body,
        boolean published,
        String categoryId,
        String categoryName,
        String authorName,
        Instant createdAt,
        Instant updatedAt) {
}
