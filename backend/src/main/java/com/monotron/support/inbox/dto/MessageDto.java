package com.monotron.support.inbox.dto;

import java.time.Instant;
import java.util.List;

import com.monotron.support.ai.dto.CitationDto;

/**
 * A message inside a conversation.
 */
public record MessageDto(
        String id,
        String authorType,
        String authorId,
        String authorName,
        String authorAvatarUrl,
        String body,
        Instant createdAt,
        List<CitationDto> citations,
        String feedback) {
}
