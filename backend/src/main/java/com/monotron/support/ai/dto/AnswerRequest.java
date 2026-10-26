package com.monotron.support.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for asking the AI a question.
 */
public record AnswerRequest(
        @NotBlank(message = "A question is required")
        @Size(max = 4000, message = "A question must be at most 4000 characters")
        String question,
        String conversationId) {
}
