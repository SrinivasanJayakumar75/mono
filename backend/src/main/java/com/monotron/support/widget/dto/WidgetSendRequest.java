package com.monotron.support.widget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload used to send a message from the widget.
 */
public record WidgetSendRequest(
        @Size(max = 36, message = "An invalid visitor id was supplied")
        String visitorId,
        @Size(max = 36, message = "An invalid conversation id was supplied")
        String conversationId,
        @NotBlank(message = "A message cannot be empty")
        @Size(max = 8000, message = "A message must be at most 8000 characters")
        String body,
        @Size(max = 255, message = "A name must be at most 255 characters")
        String name,
        @Size(max = 255, message = "An email must be at most 255 characters")
        String email) {
}
