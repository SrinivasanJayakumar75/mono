package com.monotron.support.channels.dto;

import java.time.Instant;

/**
 * Connection state for one external channel. Tokens are never returned to the
 * browser; only non-secret metadata is exposed.
 */
public record ChannelConnectionDto(
        String channel,
        boolean connected,
        String accountLabel,
        String externalAccountId,
        Instant tokenExpiresAt,
        String scopes,
        Instant connectedAt,
        boolean oauthAvailable) {
}
