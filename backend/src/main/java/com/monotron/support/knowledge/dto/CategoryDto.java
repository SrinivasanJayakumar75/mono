package com.monotron.support.knowledge.dto;

/**
 * A category used to group knowledge base articles.
 */
public record CategoryDto(
        String id,
        String name,
        String slug,
        int sortOrder) {
}
