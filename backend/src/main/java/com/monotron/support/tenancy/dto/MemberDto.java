package com.monotron.support.tenancy.dto;

import java.time.Instant;

/**
 * A member of the workspace.
 */
public record MemberDto(
        String userId,
        String email,
        String name,
        String role,
        String status,
        Instant joinedAt) {
}
