package com.monotron.support.billing;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.monotron.support.billing.dto.BillingPlanDto;
import com.monotron.support.billing.dto.BillingSummaryDto;
import com.monotron.support.billing.dto.CheckoutResponse;
import com.monotron.support.billing.dto.VerifyPaymentRequest;
import com.monotron.support.common.ApiException;
import com.monotron.support.common.BadRequestException;
import com.monotron.support.common.ConflictException;
import com.monotron.support.common.ForbiddenException;
import com.monotron.support.common.Ids;
import com.monotron.support.config.AppProperties;
import com.monotron.support.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscription lifecycle for Razorpay: local trial, checkout, verification,
 * cancellation, and webhook reconciliation.
 *
 * <p>Billing rows are intentionally global so webhooks can find a workspace
 * without a JWT. Every workspace-facing method filters on the authenticated
 * workspace id; no caller can read another tenant's billing row.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private static final String SELECT_COLUMNS = """
            select id, workspace_id, status, billing_interval, provider_plan_id,
                   provider_subscription_id, current_start, current_end, trial_ends_at,
                   cancel_at_period_end, short_url
            from billing_subscriptions
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RazorpayClient razorpay;
    private final AppProperties.Billing properties;

    public BillingService(JdbcTemplate jdbc, ObjectMapper mapper, RazorpayClient razorpay,
            AppProperties appProperties) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.razorpay = razorpay;
        this.properties = appProperties.getBilling();
    }

    // ------------------------------------------------------------------
    // Trial and access
    // ------------------------------------------------------------------

    @Transactional
    public void startTrial(String workspaceId) {
        insertTrial(workspaceId);
    }

    @Transactional
    public boolean hasAccess(String workspaceId) {
        BillingRow row = ensureRow(workspaceId);
        return hasAccess(row, Instant.now());
    }

    @Transactional
    public BillingSummaryDto summary(AuthPrincipal principal) {
        String workspaceId = requireWorkspace(principal);
        BillingRow row = ensureRow(workspaceId);
        return summaryFor(row, principal);
    }

    // ------------------------------------------------------------------
    // Checkout / verification
    // ------------------------------------------------------------------

    @Transactional
    public CheckoutResponse checkout(AuthPrincipal principal, String requestedInterval) {
        String workspaceId = requireWorkspace(principal);
        requireAdmin(principal);
        String interval = normaliseInterval(requestedInterval);
        if (!razorpay.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay is not configured yet. Add RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET to backend/.env.");
        }

        BillingRow row = ensureRow(workspaceId);
        Instant now = Instant.now();
        if (Set.of("ACTIVE", "PAST_DUE", "CANCELLED").contains(effectiveStatus(row, now))
                && hasAccess(row, now)) {
            throw new ConflictException("A subscription is already active for this workspace");
        }

        int amount = amountFor(interval);
        String currency = properties.getCurrency();
        String planId = ensurePlan(interval, amount, currency);
        int cycles = "YEARLY".equals(interval) ? properties.getYearlyCycles() : properties.getMonthlyCycles();

        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("workspace_id", workspaceId);
        notes.put("user_id", principal.userId());
        notes.put("plan", interval);
        Map<String, Object> subscription = razorpay.createSubscription(
                planId, cycles, principal.email(), notes);
        String subscriptionId = text(subscription.get("id"));
        String shortUrl = text(subscription.get("short_url"));
        if (subscriptionId == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Razorpay did not return a subscription id. Please try again.");
        }

        jdbc.update("""
                update billing_subscriptions
                set status = 'PENDING', billing_interval = ?, provider_plan_id = ?,
                    provider_subscription_id = ?, short_url = ?, cancel_at_period_end = false,
                    updated_at = now()
                where workspace_id = ?
                """, interval, planId, subscriptionId, shortUrl, workspaceId);

        return new CheckoutResponse(
                subscriptionId,
                properties.getKeyId(),
                shortUrl,
                "Monotron",
                "Monotron " + label(interval) + " subscription",
                principal.name(),
                principal.email(),
                interval,
                amount,
                currency);
    }

    @Transactional
    public BillingSummaryDto verify(AuthPrincipal principal, VerifyPaymentRequest request) {
        String workspaceId = requireWorkspace(principal);
        requireAdmin(principal);
        if (!razorpay.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay is not configured yet. Add RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET to backend/.env.");
        }

        String expected = BillingCrypto.hmacSha256Hex(
                request.razorpayPaymentId() + "|" + request.razorpaySubscriptionId(),
                properties.getKeySecret());
        if (!BillingCrypto.constantTimeEquals(expected, request.razorpaySignature())) {
            throw new BadRequestException("The Razorpay payment signature is invalid");
        }

        BillingRow row = ensureRow(workspaceId);
        if (row.providerSubscriptionId() == null
                || !row.providerSubscriptionId().equals(request.razorpaySubscriptionId())) {
            throw new BadRequestException("This subscription does not belong to the current workspace");
        }

        Map<String, Object> subscriptionMap = null;
        try {
            subscriptionMap = razorpay.fetchSubscription(request.razorpaySubscriptionId());
        } catch (ApiException ex) {
            log.warn("Could not fetch Razorpay subscription {} after checkout; using local fallback",
                    request.razorpaySubscriptionId(), ex);
        }

        JsonNode subscriptionNode;
        if (subscriptionMap != null) {
            subscriptionNode = mapper.valueToTree(subscriptionMap);
            String providerStatus = text(subscriptionMap.get("status"));
            if (providerStatus == null || "created".equalsIgnoreCase(providerStatus)
                    || "pending".equalsIgnoreCase(providerStatus)) {
                ((ObjectNode) subscriptionNode).put("status", "active");
            }
        } else {
            ObjectNode fallback = mapper.createObjectNode();
            fallback.put("id", request.razorpaySubscriptionId());
            fallback.put("status", "active");
            subscriptionNode = fallback;
        }

        BillingRow updated = updateFromSubscription(row, subscriptionNode, "checkout.verified");

        Map<String, Object> payment = null;
        try {
            payment = razorpay.fetchPayment(request.razorpayPaymentId());
        } catch (ApiException ex) {
            log.warn("Could not fetch Razorpay payment {} after checkout; recording the verified payment",
                    request.razorpayPaymentId(), ex);
        }
        recordPayment(workspaceId, request.razorpaySubscriptionId(), request.razorpayPaymentId(),
                payment, amountFor(updated.interval()), properties.getCurrency());

        return summaryFor(loadRow(workspaceId), principal);
    }

    @Transactional
    public BillingSummaryDto cancel(AuthPrincipal principal) {
        String workspaceId = requireWorkspace(principal);
        requireAdmin(principal);
        if (!razorpay.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay is not configured yet. Add RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET to backend/.env.");
        }

        BillingRow row = ensureRow(workspaceId);
        if (row.providerSubscriptionId() == null) {
            throw new BadRequestException("There is no Razorpay subscription to cancel");
        }
        if (row.cancelAtPeriodEnd()) {
            return summaryFor(row, principal);
        }
        if (!hasAccess(row, Instant.now())) {
            throw new BadRequestException("There is no active subscription to cancel");
        }

        Map<String, Object> subscription = razorpay.fetchSubscription(row.providerSubscriptionId());
        JsonNode subscriptionNode = mapper.valueToTree(subscription);
        BillingRow refreshed = updateFromSubscription(row, subscriptionNode, "checkout.refresh");
        Instant currentEnd = refreshed.currentEnd() != null
                ? refreshed.currentEnd()
                : intervalEnd(refreshed.interval(), Instant.now());

        razorpay.cancelSubscription(row.providerSubscriptionId(), true);
        updateRow(refreshed, "CANCELLED", refreshed.interval(), refreshed.providerPlanId(),
                refreshed.currentStart(), currentEnd, refreshed.trialEndsAt(), true,
                refreshed.shortUrl());
        return summaryFor(loadRow(workspaceId), principal);
    }

    // ------------------------------------------------------------------
    // Webhook
    // ------------------------------------------------------------------

    @Transactional
    public void handleWebhook(String body, String signature, String eventId) {
        if (!razorpay.webhookConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "RAZORPAY_WEBHOOK_SECRET is not configured");
        }
        String expected = BillingCrypto.hmacSha256Hex(body, properties.getWebhookSecret());
        if (!BillingCrypto.constantTimeEquals(expected, signature)) {
            throw new BadRequestException("Invalid Razorpay webhook signature");
        }

        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (Exception ex) {
            throw new BadRequestException("Invalid Razorpay webhook payload");
        }

        String event = root.path("event").asText("");
        String dedupeId = eventId != null && !eventId.isBlank()
                ? eventId
                : BillingCrypto.sha256Hex(body);
        int inserted = jdbc.update("""
                insert into billing_webhook_events (event_id, event_type)
                values (?, ?)
                on conflict (event_id) do nothing
                """, dedupeId, event.isBlank() ? "unknown" : event);
        if (inserted == 0) {
            return;
        }

        JsonNode subscription = root.path("payload").path("subscription").path("entity");
        JsonNode payment = root.path("payload").path("payment").path("entity");
        String subscriptionId = subscription.path("id").asText(null);
        String workspaceId = subscription.path("notes").path("workspace_id").asText(null);

        if (subscriptionId == null && payment.hasNonNull("subscription_id")) {
            subscriptionId = payment.path("subscription_id").asText();
        }

        BillingRow row = subscriptionId == null ? null : findRowByProviderSubscription(subscriptionId);
        if (row == null && workspaceId != null && !workspaceId.isBlank()) {
            BillingRow candidate = ensureRow(workspaceId);
            // Only use the workspace note when it does not point at a different
            // Razorpay subscription. A late webhook from an abandoned checkout
            // must not overwrite the current one.
            if (candidate.providerSubscriptionId() == null
                    || candidate.providerSubscriptionId().equals(subscriptionId)) {
                row = candidate;
            }
        }
        if (row == null) {
            log.warn("Razorpay webhook {} could not be matched to a workspace", event);
            return;
        }

        if (subscription.isObject()) {
            row = updateFromSubscription(row, subscription, event);
        } else if (subscriptionId != null && event.startsWith("payment.")) {
            try {
                Map<String, Object> fetched = razorpay.fetchSubscription(subscriptionId);
                row = updateFromSubscription(row, mapper.valueToTree(fetched), event);
            } catch (ApiException ex) {
                log.warn("Could not refresh subscription {} from payment webhook {}",
                        subscriptionId, event, ex);
            }
        }

        if (payment.isObject()) {
            String paymentId = payment.path("id").asText(null);
            Map<String, Object> paymentMap = mapper.convertValue(payment,
                    new TypeReference<Map<String, Object>>() {
                    });
            recordPayment(row.workspaceId(), subscriptionId, paymentId, paymentMap,
                    amountFor(row.interval()), properties.getCurrency());
        }
    }

    // ------------------------------------------------------------------
    // Summary helpers
    // ------------------------------------------------------------------

    private BillingSummaryDto summaryFor(BillingRow row, AuthPrincipal principal) {
        Instant now = Instant.now();
        String effective = effectiveStatus(row, now);
        boolean access = hasAccess(row, now);
        Long daysLeft = daysLeft(row, effective, access, now);

        List<BillingPlanDto> plans = List.of(
                new BillingPlanDto("MONTHLY", "Monthly", properties.getMonthlyAmount(),
                        properties.getCurrency(), "Billed every month", false),
                new BillingPlanDto("YEARLY", "Yearly", properties.getYearlyAmount(),
                        properties.getCurrency(), "Billed once a year", true));

        return new BillingSummaryDto(
                row.workspaceId(),
                effective,
                row.interval(),
                properties.getProvider(),
                row.trialEndsAt(),
                row.currentEnd(),
                row.cancelAtPeriodEnd(),
                access,
                daysLeft,
                razorpay.configured(),
                razorpay.webhookConfigured(),
                razorpay.testMode(),
                principal != null && principal.isAdmin(),
                row.shortUrl(),
                properties.getTrialDays(),
                plans);
    }

    private String effectiveStatus(BillingRow row, Instant now) {
        String status = row.status();
        if ("TRIALING".equals(status)
                && (row.trialEndsAt() == null || !row.trialEndsAt().isAfter(now))) {
            return "EXPIRED";
        }
        if (Set.of("ACTIVE", "PAST_DUE", "CANCELLED").contains(status)
                && row.currentEnd() != null && !row.currentEnd().isAfter(now)) {
            return "EXPIRED";
        }
        return status;
    }

    private boolean hasAccess(BillingRow row, Instant now) {
        if (!properties.isEnforce()) {
            return true;
        }
        String status = row.status();
        if (row.trialEndsAt() != null && row.trialEndsAt().isAfter(now)
                && ("TRIALING".equals(status) || "PENDING".equals(status))) {
            return true;
        }
        if ("ACTIVE".equals(status) || "PAST_DUE".equals(status)) {
            return row.currentEnd() == null || row.currentEnd().isAfter(now);
        }
        if ("CANCELLED".equals(status)) {
            return row.currentEnd() != null && row.currentEnd().isAfter(now);
        }
        return false;
    }

    private Long daysLeft(BillingRow row, String effective, boolean access, Instant now) {
        Instant end = null;
        if (("TRIALING".equals(effective) || "PENDING".equals(effective))
                && row.trialEndsAt() != null && row.trialEndsAt().isAfter(now)) {
            end = row.trialEndsAt();
        } else if (access && row.currentEnd() != null && row.currentEnd().isAfter(now)) {
            end = row.currentEnd();
        }
        if (end == null) {
            return null;
        }
        long minutes = Math.max(0, Duration.between(now, end).toMinutes());
        return (long) Math.ceil(minutes / 1440.0);
    }

    // ------------------------------------------------------------------
    // Row persistence
    // ------------------------------------------------------------------

    private void insertTrial(String workspaceId) {
        int trialDays = Math.max(1, properties.getTrialDays());
        jdbc.update("""
                insert into billing_subscriptions
                    (id, workspace_id, status, trial_ends_at, current_start, current_end, created_at, updated_at)
                values (?, ?, 'TRIALING', now() + make_interval(days => ?), now(),
                        now() + make_interval(days => ?), now(), now())
                on conflict (workspace_id) do nothing
                """, Ids.uuid(), workspaceId, trialDays, trialDays);
    }

    private BillingRow ensureRow(String workspaceId) {
        BillingRow row = loadRow(workspaceId);
        if (row != null) {
            return row;
        }
        insertTrial(workspaceId);
        row = loadRow(workspaceId);
        if (row == null) {
            throw new IllegalStateException("Unable to create billing row for workspace " + workspaceId);
        }
        return row;
    }

    private BillingRow loadRow(String workspaceId) {
        List<BillingRow> rows = jdbc.query(
                SELECT_COLUMNS + " where workspace_id = ?",
                this::mapRow,
                workspaceId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private BillingRow findRowByProviderSubscription(String subscriptionId) {
        List<BillingRow> rows = jdbc.query(
                SELECT_COLUMNS + " where provider_subscription_id = ?",
                this::mapRow,
                subscriptionId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private BillingRow mapRow(ResultSet rs, int index) throws SQLException {
        return new BillingRow(
                rs.getString("id"),
                rs.getString("workspace_id"),
                rs.getString("status"),
                rs.getString("billing_interval"),
                rs.getString("provider_plan_id"),
                rs.getString("provider_subscription_id"),
                instant(rs.getTimestamp("current_start")),
                instant(rs.getTimestamp("current_end")),
                instant(rs.getTimestamp("trial_ends_at")),
                rs.getBoolean("cancel_at_period_end"),
                rs.getString("short_url"));
    }

    private BillingRow updateFromSubscription(BillingRow row, JsonNode subscription, String event) {
        String providerStatus = text(subscription.get("status"));
        String status = mapStatus(providerStatus, row.status());

        if ("subscription.cancelled".equals(event)) {
            status = "CANCELLED";
        } else if ("subscription.completed".equals(event) || "subscription.expired".equals(event)) {
            status = "EXPIRED";
        } else if ("subscription.halted".equals(event)) {
            status = "PAST_DUE";
        } else if ("subscription.pending".equals(event)) {
            status = "PENDING";
        } else if ("subscription.activated".equals(event) || "subscription.charged".equals(event)
                || "subscription.authenticated".equals(event)) {
            if (!"CANCELLED".equals(status) && !"EXPIRED".equals(status)) {
                status = "ACTIVE";
            }
        }

        Instant start = firstNonNull(instant(subscription.get("current_start")), row.currentStart());
        if (start == null) {
            start = Instant.now();
        }
        Instant end = instant(subscription.get("current_end"));
        if (end == null) {
            Instant now = Instant.now();
            if ("ACTIVE".equals(status) || "PENDING".equals(status)) {
                // Razorpay can return an authenticated subscription before it
                // fills in current_end. Never reuse the old trial end here, or
                // a workspace that pays after the trial would stay locked out.
                end = intervalEnd(row.interval(), now);
            } else if (row.currentEnd() != null) {
                end = row.currentEnd();
            } else if ("PAST_DUE".equals(status) || "CANCELLED".equals(status)) {
                end = intervalEnd(row.interval(), now);
            }
        }

        String planId = firstNonNull(text(subscription.get("plan_id")), row.providerPlanId());
        String shortUrl = firstNonNull(text(subscription.get("short_url")), row.shortUrl());
        boolean cancelAtPeriodEnd = row.cancelAtPeriodEnd();
        if ("subscription.cancelled".equals(event)) {
            cancelAtPeriodEnd = true;
        } else if ("subscription.activated".equals(event) || "subscription.charged".equals(event)) {
            cancelAtPeriodEnd = false;
        }
        Instant trialEndsAt = "ACTIVE".equals(status) ? null : row.trialEndsAt();

        updateRow(row, status, row.interval(), planId, start, end, trialEndsAt,
                cancelAtPeriodEnd, shortUrl);
        return loadRow(row.workspaceId());
    }

    private void updateRow(BillingRow row, String status, String interval, String planId,
            Instant currentStart, Instant currentEnd, Instant trialEndsAt,
            boolean cancelAtPeriodEnd, String shortUrl) {
        jdbc.update("""
                update billing_subscriptions
                set status = ?, billing_interval = ?, provider_plan_id = ?, current_start = ?,
                    current_end = ?, trial_ends_at = ?, cancel_at_period_end = ?, short_url = ?,
                    updated_at = now()
                where workspace_id = ?
                """, (PreparedStatementSetter) ps -> {
            ps.setString(1, status);
            ps.setString(2, interval);
            ps.setString(3, planId);
            setInstant(ps, 4, currentStart);
            setInstant(ps, 5, currentEnd);
            setInstant(ps, 6, trialEndsAt);
            ps.setBoolean(7, cancelAtPeriodEnd);
            ps.setString(8, shortUrl);
            ps.setString(9, row.workspaceId());
        });
    }

    // ------------------------------------------------------------------
    // Razorpay plan / payment helpers
    // ------------------------------------------------------------------

    private String ensurePlan(String interval, int amount, String currency) {
        List<PlanRow> rows = jdbc.query("""
                select provider_plan_id, amount, currency
                from billing_plans where plan_key = ?
                """, (rs, index) -> new PlanRow(
                rs.getString("provider_plan_id"),
                rs.getInt("amount"),
                rs.getString("currency")), interval);

        if (!rows.isEmpty()) {
            PlanRow existing = rows.get(0);
            if (existing.amount() == amount && currency.equals(existing.currency())) {
                return existing.providerPlanId();
            }
        }

        Map<String, Object> plan = razorpay.createPlan(interval, amount, currency);
        String planId = text(plan.get("id"));
        if (planId == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Razorpay did not return a plan id. Please try again.");
        }
        jdbc.update("""
                insert into billing_plans
                    (plan_key, provider, provider_plan_id, amount, currency, created_at, updated_at)
                values (?, 'razorpay', ?, ?, ?, now(), now())
                on conflict (plan_key) do update
                set provider_plan_id = excluded.provider_plan_id,
                    amount = excluded.amount,
                    currency = excluded.currency,
                    updated_at = now()
                """, interval, planId, amount, currency);
        return planId;
    }

    private void recordPayment(String workspaceId, String subscriptionId, String paymentId,
            Map<String, Object> payment, int fallbackAmount, String fallbackCurrency) {
        if (paymentId == null || paymentId.isBlank()) {
            return;
        }
        int amount = intValue(payment == null ? null : payment.get("amount"), fallbackAmount);
        String currency = firstNonNull(payment == null ? null : text(payment.get("currency")),
                fallbackCurrency);
        String status = firstNonNull(payment == null ? null : text(payment.get("status")), "captured");
        jdbc.update("""
                insert into billing_payments
                    (id, workspace_id, provider, provider_payment_id, provider_subscription_id,
                     amount, currency, status, created_at)
                values (?, ?, 'razorpay', ?, ?, ?, ?, ?, now())
                on conflict (provider_payment_id) do update set status = excluded.status
                """, (PreparedStatementSetter) ps -> {
            ps.setString(1, Ids.uuid());
            ps.setString(2, workspaceId);
            ps.setString(3, paymentId);
            ps.setString(4, subscriptionId);
            ps.setInt(5, amount);
            ps.setString(6, currency);
            ps.setString(7, status);
        });
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private String requireWorkspace(AuthPrincipal principal) {
        if (principal == null || !principal.hasWorkspace()) {
            throw new ForbiddenException("Select a workspace before managing billing");
        }
        return principal.workspaceId();
    }

    private void requireAdmin(AuthPrincipal principal) {
        if (principal == null || !principal.isAdmin()) {
            throw new ForbiddenException("Only workspace admins can manage billing");
        }
    }

    private String normaliseInterval(String interval) {
        if (interval == null) {
            throw new BadRequestException("Choose monthly or yearly billing");
        }
        String clean = interval.trim().toUpperCase(Locale.ROOT);
        if (!"MONTHLY".equals(clean) && !"YEARLY".equals(clean)) {
            throw new BadRequestException("Billing interval must be MONTHLY or YEARLY");
        }
        return clean;
    }

    private int amountFor(String interval) {
        return "YEARLY".equals(interval) ? properties.getYearlyAmount() : properties.getMonthlyAmount();
    }

    private Instant intervalEnd(String interval, Instant from) {
        ZonedDateTime zoned = from.atZone(ZoneOffset.UTC);
        return "YEARLY".equals(interval)
                ? zoned.plusYears(1).toInstant()
                : zoned.plusMonths(1).toInstant();
    }

    private static String mapStatus(String providerStatus, String fallback) {
        if (providerStatus == null) {
            return fallback;
        }
        return switch (providerStatus.toLowerCase(Locale.ROOT)) {
            case "active", "authenticated" -> "ACTIVE";
            case "created", "pending" -> "PENDING";
            case "halted" -> "PAST_DUE";
            case "cancelled" -> "CANCELLED";
            case "completed", "expired" -> "EXPIRED";
            default -> fallback == null ? "PENDING" : fallback;
        };
    }

    private static String label(String interval) {
        return "YEARLY".equals(interval) ? "Yearly" : "Monthly";
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode node) {
            if (node.isNull() || node.isMissingNode()) {
                return null;
            }
            String text = node.asText();
            return text.isBlank() ? null : text;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof JsonNode node) {
            return node.isNumber() ? node.asInt() : fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Instant instant(Object value) {
        if (value instanceof JsonNode node) {
            if (node.isNumber()) {
                long epoch = node.asLong();
                return epoch > 0 ? Instant.ofEpochSecond(epoch) : null;
            }
            if (node.isTextual()) {
                try {
                    long epoch = Long.parseLong(node.asText());
                    return epoch > 0 ? Instant.ofEpochSecond(epoch) : null;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
        if (value instanceof Number number) {
            long epoch = number.longValue();
            return epoch > 0 ? Instant.ofEpochSecond(epoch) : null;
        }
        if (value instanceof String text) {
            try {
                long epoch = Long.parseLong(text);
                return epoch > 0 ? Instant.ofEpochSecond(epoch) : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private static void setInstant(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.from(value));
        }
    }

    private record BillingRow(
            String id,
            String workspaceId,
            String status,
            String interval,
            String providerPlanId,
            String providerSubscriptionId,
            Instant currentStart,
            Instant currentEnd,
            Instant trialEndsAt,
            boolean cancelAtPeriodEnd,
            String shortUrl) {
    }

    private record PlanRow(String providerPlanId, int amount, String currency) {
    }
}
