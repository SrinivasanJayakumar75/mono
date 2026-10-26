package com.monotron.support.inbox.dto;

import java.time.Instant;

/**
 * A conversation, as shown in the inbox list.
 */
public record ConversationDto(
        String id,
        String subject,
        String status,
        String channel,
        String assigneeId,
        String assigneeName,
        String teamId,
        String teamName,
        boolean aiHandled,
        boolean escalated,
        int unreadCount,
        Instant createdAt,
        Instant lastActivityAt,
        Instant closedAt) {
}
