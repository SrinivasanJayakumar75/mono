package com.monotron.support.inbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for replying to a conversation.
 */
public record ReplyRequest(
        @NotBlank(message = "A message cannot be empty")
        @Size(max = 8000, message = "A message must be at most 8000 characters")
        String body) {
}
