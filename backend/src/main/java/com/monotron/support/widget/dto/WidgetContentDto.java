package com.monotron.support.widget.dto;

import java.time.Instant;

/**
 * A blog post, FAQ or news announcement shown in the widget help center.
 */
public record WidgetContentDto(
        String id,
        String kind,
        String title,
        String summary,
        String body,
        boolean published,
        Instant createdAt,
        Instant updatedAt) {
}
