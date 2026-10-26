package com.monotron.support.tenancy.dto;

/**
 * Branding and behaviour of the messenger widget.
 */
public record WidgetSettingsDto(
        String appId,
        String workspaceName,
        String greeting,
        String subtitle,
        String color,
        String introMessage,
        String inputPlaceholder,
        boolean showBranding,
        boolean aiEnabled,
        boolean autoReply) {
}
