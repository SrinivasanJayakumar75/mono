package com.monotron.support.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating or updating an article.
 */
public record ArticleRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 400, message = "Title must be at most 400 characters")
        String title,

        @NotBlank(message = "Body is required")
        String body,

        String categoryId,

        Boolean published) {
}
