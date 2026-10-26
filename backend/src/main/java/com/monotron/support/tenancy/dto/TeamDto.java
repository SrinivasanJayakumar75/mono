package com.monotron.support.tenancy.dto;

import java.time.Instant;
import java.util.List;

/**
 * A team of agents.
 */
public record TeamDto(
        String id,
        String name,
        String description,
        Instant createdAt,
        List<String> memberIds) {
}
