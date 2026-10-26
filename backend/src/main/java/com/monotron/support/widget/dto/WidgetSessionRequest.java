package com.monotron.support.widget.dto;

import jakarta.validation.constraints.Size;

/**
 * Payload used to open (or resume) a widget session.
 */
public record WidgetSessionRequest(
        @Size(max = 36, message = "An invalid visitor id was supplied")
        String visitorId,
        @Size(max = 255, message = "A name must be at most 255 characters")
        String name,
        @Size(max = 255, message = "An email must be at most 255 characters")
        String email) {
}
