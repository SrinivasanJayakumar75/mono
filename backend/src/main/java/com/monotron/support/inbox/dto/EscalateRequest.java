package com.monotron.support.inbox.dto;

/**
 * Payload for pausing or resuming the automatic AI replies of a conversation.
 * Omitting the field (or the whole body) escalates the conversation.
 */
public record EscalateRequest(Boolean escalated) {
}
