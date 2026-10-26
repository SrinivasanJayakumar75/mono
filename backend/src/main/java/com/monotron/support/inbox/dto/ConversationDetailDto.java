package com.monotron.support.inbox.dto;

import java.util.List;

/**
 * A conversation with its contact and the full message thread.
 */
public record ConversationDetailDto(
        ConversationDto conversation,
        ContactDetailDto contact,
        List<MessageDto> messages) {
}
