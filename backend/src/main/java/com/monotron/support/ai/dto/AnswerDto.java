package com.monotron.support.ai.dto;

import java.util.List;

/**
 * An AI generated answer together with the sources it is grounded in.
 */
public record AnswerDto(
        String answer,
        String model,
        String provider,
        List<CitationDto> citations) {
}
