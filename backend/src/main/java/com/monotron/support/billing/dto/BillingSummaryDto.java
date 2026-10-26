package com.monotron.support.billing.dto;

import java.time.Instant;
import java.util.List;

/**
 * Everything the billing screen needs to render the current state and plans.
 */
public record BillingSummaryDto(
        String workspaceId,
        String status,
        String interval,
        String provider,
        Instant trialEndsAt,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        boolean access,
        Long daysLeft,
        boolean configured,
        boolean webhookConfigured,
        boolean testMode,
        boolean canManage,
        String shortUrl,
        int trialDays,
        List<BillingPlanDto> plans) {
}
