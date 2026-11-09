package com.monotron.support.channels.dto;

import jakarta.validation.constraints.Size;

/**
 * Payload for changing one channel's automation settings. Every field is
 * optional; existing values are kept when a field is omitted.
 */
public record UpdateChannelAutomationRequest(
        @Size(max = 200, message = "Account label must be at most 200 characters")
        String accountLabel,

        Boolean enabled,
        Boolean autoReply,
        Boolean handoffToHuman) {
}
