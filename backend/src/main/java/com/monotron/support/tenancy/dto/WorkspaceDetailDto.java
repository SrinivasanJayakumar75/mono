package com.monotron.support.tenancy.dto;

import java.time.Instant;

/**
 * Full detail of the caller's workspace.
 */
public record WorkspaceDetailDto(
        String id,
        String name,
        String slug,
        String role,
        String appId,
        Instant createdAt,
        AppDto app) {
}
