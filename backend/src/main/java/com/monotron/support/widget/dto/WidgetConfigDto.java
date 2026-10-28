package com.monotron.support.widget.dto;

import java.util.List;

/**
 * The public branding configuration of the messenger widget.
 */
public record WidgetConfigDto(
        String appId,
        String workspaceName,
        String greeting,
        String subtitle,
        String color,
        String introMessage,
        String inputPlaceholder,
        boolean showBranding,
        boolean autoOpen) {
}
