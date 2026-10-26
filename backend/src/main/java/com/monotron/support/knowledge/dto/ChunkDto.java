package com.monotron.support.knowledge.dto;

/**
 * A single chunk of a knowledge base source.
 */
public record ChunkDto(
        String id,
        String chunkIndex,
        String title,
        String content,
        String url) {
}
