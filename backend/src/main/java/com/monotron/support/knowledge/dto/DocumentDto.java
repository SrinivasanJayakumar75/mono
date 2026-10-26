package com.monotron.support.knowledge.dto;

import java.time.Instant;

/**
 * A knowledge base document.
 */
public record DocumentDto(
        String id,
        String filename,
        String title,
        String status,
        String error,
        Integer pageCount,
        Integer chunkCount,
        Long sizeBytes,
        String mimeType,
        Instant createdAt,
        Instant updatedAt) {
}
