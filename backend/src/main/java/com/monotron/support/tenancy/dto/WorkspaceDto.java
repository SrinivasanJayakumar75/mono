package com.monotron.support.tenancy.dto;

/**
 * A workspace (tenant) as seen by the caller.
 */
public record WorkspaceDto(
        String id,
        String name,
        String slug,
        String role,
        String appId) {
}
