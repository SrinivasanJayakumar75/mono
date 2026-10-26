package com.monotron.support.tenancy.dto;

import java.time.Instant;

/**
 * The messenger app that customers embed on their website.
 */
public record AppDto(
        String id,
        String name,
        String hmacSecret,
        boolean aiEnabled,
        boolean autoReply,
        String aiModel,
        double aiTemperature,
        String aiSystemPrompt) {
}
