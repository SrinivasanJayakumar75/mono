package com.monotron.support.inbox.dto;

/**
 * Payload for asking the AI agent to answer a conversation.
 */
public record AiReplyRequest(String question) {
}
