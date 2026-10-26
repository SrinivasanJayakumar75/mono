package com.monotron.support.inbox.dto;

/**
 * Counters shown above the inbox.
 */
public record ConversationStatsDto(
        int open,
        int closed,
        int unread,
        int aiHandled,
        int total) {
}
