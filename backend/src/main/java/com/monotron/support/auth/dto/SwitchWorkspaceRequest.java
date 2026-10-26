package com.monotron.support.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SwitchWorkspaceRequest(
        @NotBlank(message = "Workspace id is required")
        String workspaceId) {
}
