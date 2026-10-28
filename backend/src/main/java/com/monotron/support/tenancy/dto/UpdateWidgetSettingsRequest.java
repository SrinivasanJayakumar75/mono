package com.monotron.support.tenancy.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for updating the messenger widget settings. Every field is optional;
 * only the fields that are present are written.
 */
public record UpdateWidgetSettingsRequest(
        @Size(max = 120, message = "Workspace name must be at most 120 characters")
        String workspaceName,

        @Size(max = 500, message = "Greeting must be at most 500 characters")
        String greeting,

        @Size(max = 500, message = "Subtitle must be at most 500 characters")
        String subtitle,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Colour must be a hex value such as #4F46E5")
        String color,

        @Size(max = 1000, message = "Intro message must be at most 1000 characters")
        String introMessage,

        @Size(max = 200, message = "Input placeholder must be at most 200 characters")
        String inputPlaceholder,

        Boolean showBranding,
        Boolean autoOpen,
        Boolean aiEnabled,
        Boolean autoReply) {
}
