package com.monotron.support.widget.dto;

import java.util.List;

/**
 * A conversation thread as rendered inside the widget.
 */
public record WidgetConversationDto(
        String id,
        String subject,
        String status,
        String createdAt,
        String lastActivityAt,
        List<WidgetMessageDto> messages) {
}
