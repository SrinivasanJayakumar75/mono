package com.monotron.support.tenancy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Payload for adding a member to the workspace.
 */
public record AddMemberRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        String email,

        @NotBlank(message = "Role is required")
        @Pattern(regexp = "ADMIN|AGENT|VIEWER", message = "Role must be ADMIN, AGENT or VIEWER")
        String role) {
}
