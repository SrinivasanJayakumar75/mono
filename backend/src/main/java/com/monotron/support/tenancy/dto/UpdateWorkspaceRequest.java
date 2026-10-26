package com.monotron.support.tenancy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for updating the workspace name.
 */
public record UpdateWorkspaceRequest(
        @NotBlank(message = "Workspace name is required")
        @Size(max = 120, message = "Workspace name must be at most 120 characters")
        String name) {
}
