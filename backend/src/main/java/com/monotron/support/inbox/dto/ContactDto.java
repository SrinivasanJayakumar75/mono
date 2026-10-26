package com.monotron.support.inbox.dto;

import java.time.Instant;

/**
 * A contact as shown in the inbox.
 */
public record ContactDto(
        String id,
        String name,
        String email,
        String avatarUrl,
        String visitorId,
        boolean online,
        String browser,
        String os,
        String location,
        String locale,
        Instant lastSeenAt,
        Instant createdAt) {
}
