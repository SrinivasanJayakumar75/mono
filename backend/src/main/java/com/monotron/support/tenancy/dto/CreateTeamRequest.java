package com.monotron.support.tenancy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating a team.
 */
public record CreateTeamRequest(
        @NotBlank(message = "Team name is required")
        @Size(max = 120, message = "Team name must be at most 120 characters")
        String name,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description) {
}
