package com.monotron.support.dashboard;

import com.monotron.support.dashboard.dto.DashboardDto;
import com.monotron.support.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dashboard summary.
 */
@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard")
    public DashboardDto summary(@AuthenticationPrincipal AuthPrincipal principal) {
        return dashboardService.summary(principal);
    }
}
