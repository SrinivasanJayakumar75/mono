package com.monotron.support.inbox.dto;

/**
 * A conversation as shown in the inbox list, enriched with the contact and the
 * most recent message so the list can be rendered without further requests.
 */
public record ConversationSummaryDto(
        String id,
        String subject,
        String status,
        String channel,
        String createdAt,
        String lastActivityAt,
        String closedAt,
        String assigneeId,
        String assigneeName,
        String teamId,
        String teamName,
        boolean aiHandled,
        boolean escalated,
        int unreadCount,
        ContactDto contact,
        LastMessageDto lastMessage) {

    /**
     * @param body      last message body
     * @param createdAt last message timestamp
     */
    public record LastMessageDto(
            String id,
            String authorType,
            String authorName,
            String body,
            String createdAt) {
    }
}
