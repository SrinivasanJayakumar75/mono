package com.monotron.support.channels.dto;

import java.time.Instant;

/**
 * Automation settings for one external chat channel.
 */
public record ChannelAutomationDto(
        String channel,
        String accountLabel,
        boolean enabled,
        boolean autoReply,
        boolean handoffToHuman,
        Instant updatedAt) {
}
