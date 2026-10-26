package com.monotron.support.knowledge.dto;

import java.time.Instant;
import java.util.List;

/**
 * A document together with the chunks that were extracted from it.
 */
public record DocumentDetailDto(
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
        Instant updatedAt,
        List<ChunkDto> chunks) {
}
