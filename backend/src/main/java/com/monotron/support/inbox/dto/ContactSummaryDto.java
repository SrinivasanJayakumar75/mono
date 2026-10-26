package com.monotron.support.inbox.dto;

import java.time.Instant;
import java.util.List;

/**
 * A contact in the contacts list.
 */
public record ContactSummaryDto(
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
        String lastSeenAt,
        String createdAt,
        long conversationCount) {
}
