package com.monotron.support.widget.dto;

import java.util.List;

/**
 * The visitor session returned by the widget bootstrap.
 */
public record WidgetSessionResponseDto(
        String contactId,
        String visitorId,
        String name,
        String email,
        List<WidgetConversationDto> conversations) {
}
