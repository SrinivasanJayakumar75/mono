package com.monotron.support.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for a semantic search over the knowledge base.
 */
public record SearchRequest(
        @NotBlank(message = "Query is required")
        @Size(max = 1000, message = "Query must be at most 1000 characters")
        String query,

        Integer limit) {
}
