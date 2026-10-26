package com.monotron.support.inbox.dto;

import java.time.Instant;
import java.util.Map;

/**
 * A contact with the details shown in the contact drawer.
 */
public record ContactDetailDto(
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
        Instant signedUpAt,
        Instant createdAt,
        Map<String, Object> customAttributes,
        long conversationCount,
        String crmLeadId) {
}
