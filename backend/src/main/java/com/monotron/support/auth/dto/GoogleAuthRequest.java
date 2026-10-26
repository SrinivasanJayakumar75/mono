package com.monotron.support.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload from the Google Identity Services button. The credential is a signed
 * Google ID token; the backend verifies it before creating or finding a user.
 */
public record GoogleAuthRequest(
        @NotBlank(message = "Google credential is required")
        String credential,

        @NotBlank(message = "Choose whether to sign in or register")
        @Pattern(regexp = "LOGIN|REGISTER", message = "Intent must be LOGIN or REGISTER")
        String intent,

        @Size(max = 120, message = "Workspace name must be at most 120 characters")
        String workspaceName) {
}
