package com.monotron.support.tenancy;

import com.monotron.support.security.AuthPrincipal;
import com.monotron.support.tenancy.dto.UpdateWidgetSettingsRequest;
import com.monotron.support.tenancy.dto.WidgetSettingsDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Branding and behaviour of the messenger widget.
 */
@RestController
@RequestMapping("/api/widget-settings")
public class WidgetSettingsController {

    private final WorkspaceService workspaceService;

    public WidgetSettingsController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public WidgetSettingsDto get(@AuthenticationPrincipal AuthPrincipal principal) {
        return workspaceService.widgetSettings(principal);
    }

    @PutMapping
    public WidgetSettingsDto update(@AuthenticationPrincipal AuthPrincipal principal,
                                    @Valid @RequestBody UpdateWidgetSettingsRequest request) {
        return workspaceService.updateWidgetSettings(principal, request);
    }
}
