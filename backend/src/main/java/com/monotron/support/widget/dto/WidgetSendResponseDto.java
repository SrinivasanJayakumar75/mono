package com.monotron.support.widget.dto;

/**
 * The result of sending a message from the widget, including the AI answer
 * when the workspace has the auto reply enabled.
 */
public record WidgetSendResponseDto(
        String conversationId,
        String contactId,
        WidgetMessageDto message,
        WidgetMessageDto aiReply) {
}
