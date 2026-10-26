package com.monotron.support.widget.dto;

import java.util.List;

import com.monotron.support.ai.dto.CitationDto;

/**
 * A message as rendered inside the widget.
 */
public record WidgetMessageDto(
        String id,
        String authorType,
        String authorName,
        String body,
        String createdAt,
        List<CitationDto> citations) {
}
