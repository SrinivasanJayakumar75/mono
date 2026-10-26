package com.monotron.support.auth.dto;

import java.util.List;

import com.monotron.support.tenancy.dto.UserDto;
import com.monotron.support.tenancy.dto.WorkspaceDto;

/**
 * The result of a successful authentication.
 *
 * @param expiresIn lifetime of the access token in seconds
 */
public record AuthResponse(
        UserDto user,
        List<WorkspaceDto> workspaces,
        WorkspaceDto activeWorkspace,
        String accessToken,
        String refreshToken,
        long expiresIn) {
}
