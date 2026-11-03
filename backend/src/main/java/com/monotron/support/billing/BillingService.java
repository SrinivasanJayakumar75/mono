package com.monotron.support.billing;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monotron.support.billing.dto.BillingPlanDto;
import com.monotron.support.billing.dto.BillingSummaryDto;
import com.monotron.support.billing.dto.CheckoutResponse;
import com.monotron.support.billing.dto.VerifyCheckoutRequest;
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
 * Subscription lifecycle for Polar: local trial, checkout, verification,
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
            select id, workspace_id, provider, status, billing_interval, provider_plan_id,
                   provider_subscription_id, current_start, current_end, trial_ends_at,
                   cancel_at_period_end, short_url
            from billing_subscriptions
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PolarClient polar;
    private final AppProperties.Billing properties;

    public BillingService(JdbcTemplate jdbc, ObjectMapper mapper, PolarClient polar,
            AppProperties appProperties) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.polar = polar;
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
        if (!polar.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Polar is not configured yet. Add POLAR_ACCESS_TOKEN, "
                            + "POLAR_MONTHLY_PRODUCT_ID and POLAR_YEARLY_PRODUCT_ID to backend/.env.");
        }
        if (!polar.configuredFor(interval)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Polar " + label(interval).toLowerCase() + " product is not configured yet. Add "
                            + envName(interval) + " to backend/.env.");
        }

        BillingRow row = ensureRow(workspaceId);
        Instant now = Instant.now();
        if (Set.of("ACTIVE", "PAST_DUE", "CANCELLED").contains(effectiveStatus(row, now))
                && hasAccess(row, now)) {
            if (!"polar".equals(row.provider())) {
                throw new ConflictException(
                        "This workspace has an active Razorpay subscription. Cancel it in the "
                                + "Razorpay dashboard before subscribing with Polar.");
            }
            throw new ConflictException("A subscription is already active for this workspace");
        }

        int amount = amountFor(interval);
        String currency = properties.getCurrency();
        String productId = polar.productId(interval);
        Map<String, Object> checkout = polar.createCheckout(
                interval, principal.email(), workspaceId, principal.userId());
        String checkoutId = text(checkout.get("id"));
        String url = text(checkout.get("url"));
        if (checkoutId == null || url == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Polar did not return a checkout URL. Please try again.");
        }

        jdbc.update("""
                update billing_subscriptions
                set provider = 'polar', status = 'PENDING', billing_interval = ?,
                    provider_plan_id = ?, provider_subscription_id = null, short_url = ?,
                    cancel_at_period_end = false, updated_at = now()
                where workspace_id = ?
                """, interval, productId, url, workspaceId);

        return new CheckoutResponse(checkoutId, url, interval, amount, currency);
    }

    @Transactional
    public BillingSummaryDto verify(AuthPrincipal principal, VerifyCheckoutRequest request) {
        String workspaceId = requireWorkspace(principal);
        requireAdmin(principal);
        if (!polar.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Polar is not configured yet. Add POLAR_ACCESS_TOKEN, "
                            + "POLAR_MONTHLY_PRODUCT_ID and POLAR_YEARLY_PRODUCT_ID to backend/.env.");
        }

        Map<String, Object> checkout = polar.fetchCheckout(request.checkoutId());
        JsonNode checkoutNode = mapper.valueToTree(checkout);
        String checkoutStatus = text(checkoutNode.get("status"));
        if (!"succeeded".equalsIgnoreCase(checkoutStatus)) {
            throw new BadRequestException(
                    "Polar has not completed this checkout yet. Refresh in a moment.");
        }

        String checkoutWorkspace = workspaceFromCheckout(checkoutNode);
        if (checkoutWorkspace == null || !checkoutWorkspace.equals(workspaceId)) {
            throw new BadRequestException(
                    "This checkout does not belong to the current workspace");
        }

        BillingRow row = ensureRow(workspaceId);
        String subscriptionId = text(checkoutNode.get("subscription_id"));
        if (subscriptionId == null) {
            // A webhook may already have linked the subscription locally even
            // when the checkout endpoint has not populated subscription_id.
            subscriptionId = row.providerSubscriptionId();
        }
        if (subscriptionId == null) {
            subscriptionId = subscriptionFromCheckoutCustomer(
                    checkoutNode, request.checkoutId(), workspaceId);
        }
        if (subscriptionId == null) {
            throw new BadRequestException(
                    "Polar has not created a subscription for this checkout yet. Refresh in a moment.");
        }

        if (row.providerSubscriptionId() != null
                && !row.providerSubscriptionId().equals(subscriptionId)) {
            throw new BadRequestException(
                    "This subscription does not belong to the current workspace");
        }

        String interval = intervalFromCheckout(checkoutNode, row);
        if (interval != null && !interval.equals(row.interval())) {
            updateInterval(workspaceId, interval);
            row = loadRow(workspaceId);
        }

        Map<String, Object> subscription = polar.fetchSubscription(subscriptionId);
        updateFromSubscription(row, mapper.valueToTree(subscription), "checkout.verified");
        return summaryFor(loadRow(workspaceId), principal);
    }

    @Transactional
    public BillingSummaryDto cancel(AuthPrincipal principal) {
        String workspaceId = requireWorkspace(principal);
        requireAdmin(principal);
        if (!polar.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Polar is not configured yet. Add POLAR_ACCESS_TOKEN, "
                            + "POLAR_MONTHLY_PRODUCT_ID and POLAR_YEARLY_PRODUCT_ID to backend/.env.");
        }

        BillingRow row = ensureRow(workspaceId);
        if (row.providerSubscriptionId() == null) {
            throw new BadRequestException("There is no Polar subscription to cancel");
        }
        if (!"polar".equals(row.provider())) {
            throw new BadRequestException(
                    "This subscription was created with the previous payment provider. "
                            + "Cancel it in the Razorpay dashboard and subscribe again with Polar.");
        }
        if (row.cancelAtPeriodEnd()) {
            return summaryFor(row, principal);
        }
        if (!hasAccess(row, Instant.now())) {
            throw new BadRequestException("There is no active subscription to cancel");
        }

        Map<String, Object> subscription = polar.cancelSubscription(row.providerSubscriptionId());
        updateFromSubscription(row, mapper.valueToTree(subscription), "subscription.canceled");
        return summaryFor(loadRow(workspaceId), principal);
    }

    // ------------------------------------------------------------------
    // Webhook
    // ------------------------------------------------------------------

    @Transactional
    public void handleWebhook(String body, String eventId, String timestamp, String signature) {
        if (!polar.webhookConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "POLAR_WEBHOOK_SECRET is not configured");
        }
        if (!PolarWebhookVerifier.verify(body, eventId, timestamp, signature,
                properties.getWebhookSecret())) {
            throw new BadRequestException("Invalid Polar webhook signature");
        }

        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (Exception ex) {
            throw new BadRequestException("Invalid Polar webhook payload");
        }

        String event = root.path("type").asText("");
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

        JsonNode data = root.path("data");
        if (data.has("object") && data.path("object").isObject()) {
            data = data.path("object");
        }
        if (!data.isObject()) {
            log.warn("Polar webhook {} had no data object", event);
            return;
        }

        if (event.startsWith("subscription.")) {
            String subscriptionId = text(data.get("id"));
            String workspaceId = workspaceFromSubscription(data);
            BillingRow row = subscriptionId == null
                    ? null
                    : findRowByProviderSubscription(subscriptionId);
            if (row == null && workspaceId != null && !workspaceId.isBlank()) {
                BillingRow candidate = loadRow(workspaceId);
                // Only use the workspace when it does not point at a different
                // Polar subscription. A late webhook from an abandoned checkout
                // must not overwrite the current one.
                if (candidate != null && (candidate.providerSubscriptionId() == null
                        || candidate.providerSubscriptionId().equals(subscriptionId))) {
                    row = candidate;
                }
            }
            if (row == null) {
                log.warn("Polar webhook {} could not be matched to a workspace", event);
                return;
            }
            updateFromSubscription(row, data, event);
        } else if (event.startsWith("order.")) {
            String subscriptionId = text(data.get("subscription_id"));
            BillingRow row = subscriptionId == null
                    ? null
                    : findRowByProviderSubscription(subscriptionId);
            if (row == null) {
                String workspaceId = workspaceFromOrder(data);
                if (workspaceId != null && !workspaceId.isBlank()) {
                    row = loadRow(workspaceId);
                }
            }
            if (row == null) {
                log.warn("Polar webhook {} could not be matched to a workspace", event);
                return;
            }
            if ("order.paid".equals(event) || "order.refunded".equals(event)) {
                String paymentId = text(data.get("id"));
                Map<String, Object> payment = mapper.convertValue(data,
                        new TypeReference<Map<String, Object>>() {
                        });
                recordPayment(row.workspaceId(), subscriptionId, paymentId, payment,
                        amountFor(row.interval()), properties.getCurrency());
            }
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
                row.provider() == null ? properties.getProvider() : row.provider(),
                row.trialEndsAt(),
                row.currentEnd(),
                row.cancelAtPeriodEnd(),
                access,
                daysLeft,
                polar.configured(),
                polar.webhookConfigured(),
                polar.testMode(),
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
                    (id, workspace_id, provider, status, trial_ends_at, current_start,
                     current_end, created_at, updated_at)
                values (?, ?, 'polar', 'TRIALING', now() + make_interval(days => ?), now(),
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
                rs.getString("provider"),
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
        boolean cancelAtPeriodEnd = subscription.path("cancel_at_period_end").asBoolean(false);
        String status = mapStatus(providerStatus, row.status());

        if ("subscription.revoked".equals(event)) {
            status = "EXPIRED";
        } else if ("subscription.past_due".equals(event)) {
            status = "PAST_DUE";
        } else if ("subscription.paused".equals(event)) {
            status = "EXPIRED";
        } else if ("subscription.canceled".equals(event) && "ACTIVE".equals(status)) {
            status = "CANCELLED";
        }
        if (cancelAtPeriodEnd && "ACTIVE".equals(status)) {
            status = "CANCELLED";
        }

        String interval = intervalFromSubscription(subscription, row);
        String subscriptionId = firstNonNull(text(subscription.get("id")),
                row.providerSubscriptionId());

        Instant start = firstNonNull(instant(subscription.get("current_period_start")),
                row.currentStart());
        if (start == null) {
            start = Instant.now();
        }
        Instant end = firstNonNull(instant(subscription.get("current_period_end")),
                instant(subscription.get("ends_at")));
        if (end == null) {
            end = instant(subscription.get("ended_at"));
        }
        if (end == null) {
            Instant now = Instant.now();
            if ("ACTIVE".equals(status) || "PENDING".equals(status) || "CANCELLED".equals(status)) {
                end = intervalEnd(interval, now);
            } else if (row.currentEnd() != null) {
                end = row.currentEnd();
            } else if ("PAST_DUE".equals(status)) {
                end = intervalEnd(interval, now);
            }
        }

        String planId = firstNonNull(text(subscription.get("product_id")), row.providerPlanId());
        String shortUrl = row.shortUrl();
        Instant trialEndsAt = row.trialEndsAt();
        if ("TRIALING".equals(status)) {
            Instant providerTrialEnd = instant(subscription.get("trial_end"));
            if (providerTrialEnd != null) {
                trialEndsAt = providerTrialEnd;
            }
            if (trialEndsAt == null) {
                trialEndsAt = intervalEnd(interval, Instant.now());
            }
        } else if (!"PENDING".equals(status)) {
            trialEndsAt = null;
        }

        boolean cancelFlag = cancelAtPeriodEnd || "CANCELLED".equals(status);
        updateRow(row, status, interval, planId, subscriptionId, start, end, trialEndsAt,
                cancelFlag, shortUrl);
        return loadRow(row.workspaceId());
    }

    private void updateRow(BillingRow row, String status, String interval, String planId,
            String providerSubscriptionId, Instant currentStart, Instant currentEnd,
            Instant trialEndsAt, boolean cancelAtPeriodEnd, String shortUrl) {
        jdbc.update("""
                update billing_subscriptions
                set provider = 'polar', status = ?, billing_interval = ?, provider_plan_id = ?,
                    provider_subscription_id = ?, current_start = ?, current_end = ?,
                    trial_ends_at = ?, cancel_at_period_end = ?, short_url = ?, updated_at = now()
                where workspace_id = ?
                """, (PreparedStatementSetter) ps -> {
            ps.setString(1, status);
            ps.setString(2, interval);
            ps.setString(3, planId);
            ps.setString(4, providerSubscriptionId);
            setInstant(ps, 5, currentStart);
            setInstant(ps, 6, currentEnd);
            setInstant(ps, 7, trialEndsAt);
            ps.setBoolean(8, cancelAtPeriodEnd);
            ps.setString(9, shortUrl);
            ps.setString(10, row.workspaceId());
        });
    }

    private void updateInterval(String workspaceId, String interval) {
        jdbc.update("""
                update billing_subscriptions
                set provider = 'polar', billing_interval = ?, updated_at = now()
                where workspace_id = ?
                """, interval, workspaceId);
    }

    // ------------------------------------------------------------------
    // Polar helpers
    // ------------------------------------------------------------------

    private String intervalFromCheckout(JsonNode checkout, BillingRow row) {
        String plan = text(checkout.path("metadata").path("plan"));
        if (isInterval(plan)) {
            return plan;
        }
        String productId = text(checkout.get("product_id"));
        return intervalFromProduct(productId, row.interval());
    }

    private String intervalFromSubscription(JsonNode subscription, BillingRow row) {
        String plan = text(subscription.path("metadata").path("plan"));
        if (isInterval(plan)) {
            return plan;
        }
        String recurring = text(subscription.get("recurring_interval"));
        if ("year".equalsIgnoreCase(recurring)) {
            return "YEARLY";
        }
        if ("month".equalsIgnoreCase(recurring)) {
            return "MONTHLY";
        }
        String productId = text(subscription.get("product_id"));
        return intervalFromProduct(productId, row.interval());
    }

    private String intervalFromProduct(String productId, String fallback) {
        if (productId != null) {
            if (productId.equals(properties.getMonthlyProductId())) {
                return "MONTHLY";
            }
            if (productId.equals(properties.getYearlyProductId())) {
                return "YEARLY";
            }
        }
        return fallback;
    }

    /**
     * Polar has been observed returning a succeeded checkout with a null
     * {@code subscription_id} even though the subscription exists and points
     * back at the checkout. Recover it from the customer's subscriptions so
     * the return page does not depend on webhook delivery.
     */
    String subscriptionFromCheckoutCustomer(JsonNode checkout, String checkoutId,
            String workspaceId) {
        String customerId = text(checkout.get("customer_id"));
        if (customerId == null) {
            return null;
        }
        Map<String, Object> response = polar.listSubscriptionsByCustomer(customerId);
        Object items = response.get("items");
        if (!(items instanceof List<?> subscriptions)) {
            return null;
        }
        for (Object item : subscriptions) {
            JsonNode subscription = mapper.valueToTree(item);
            String itemCheckout = text(subscription.get("checkout_id"));
            String itemWorkspace = text(subscription.path("metadata").path("workspace_id"));
            if (checkoutId.equals(itemCheckout)
                    && (itemWorkspace == null || itemWorkspace.equals(workspaceId))) {
                return text(subscription.get("id"));
            }
        }
        return null;
    }

    private String workspaceFromCheckout(JsonNode checkout) {
        String metadata = text(checkout.path("metadata").path("workspace_id"));
        if (metadata != null) {
            return metadata;
        }
        return text(checkout.get("external_customer_id"));
    }

    private String workspaceFromSubscription(JsonNode subscription) {
        String metadata = text(subscription.path("metadata").path("workspace_id"));
        if (metadata != null) {
            return metadata;
        }
        return text(subscription.path("customer").path("external_id"));
    }

    private String workspaceFromOrder(JsonNode order) {
        String metadata = text(order.path("metadata").path("workspace_id"));
        if (metadata != null) {
            return metadata;
        }
        return text(order.path("customer").path("external_id"));
    }

    private void recordPayment(String workspaceId, String subscriptionId, String paymentId,
            Map<String, Object> payment, int fallbackAmount, String fallbackCurrency) {
        if (paymentId == null || paymentId.isBlank()) {
            return;
        }
        int amount = intValue(payment == null ? null : payment.get("amount"), fallbackAmount);
        String currency = firstNonNull(payment == null ? null : text(payment.get("currency")),
                fallbackCurrency);
        String status = firstNonNull(payment == null ? null : text(payment.get("status")), "paid");
        jdbc.update("""
                insert into billing_payments
                    (id, workspace_id, provider, provider_payment_id, provider_subscription_id,
                     amount, currency, status, created_at)
                values (?, ?, 'polar', ?, ?, ?, ?, ?, now())
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

    private static boolean isInterval(String value) {
        return "MONTHLY".equals(value) || "YEARLY".equals(value);
    }

    private static String mapStatus(String providerStatus, String fallback) {
        if (providerStatus == null) {
            return fallback == null ? "PENDING" : fallback;
        }
        return switch (providerStatus.toLowerCase(Locale.ROOT)) {
            case "active" -> "ACTIVE";
            case "trialing" -> "TRIALING";
            case "incomplete" -> "PENDING";
            case "incomplete_expired" -> "EXPIRED";
            case "past_due", "unpaid" -> "PAST_DUE";
            case "canceled" -> "CANCELLED";
            case "paused" -> "EXPIRED";
            default -> fallback == null ? "PENDING" : fallback;
        };
    }

    private static String label(String interval) {
        return "YEARLY".equals(interval) ? "Yearly" : "Monthly";
    }

    private static String envName(String interval) {
        return "YEARLY".equals(interval) ? "POLAR_YEARLY_PRODUCT_ID" : "POLAR_MONTHLY_PRODUCT_ID";
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
                return parseInstant(node.asText());
            }
            return null;
        }
        if (value instanceof Number number) {
            long epoch = number.longValue();
            return epoch > 0 ? Instant.ofEpochSecond(epoch) : null;
        }
        if (value instanceof String text) {
            return parseInstant(text);
        }
        return null;
    }

    private static Instant parseInstant(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            long epoch = Long.parseLong(text);
            return epoch > 0 ? Instant.ofEpochSecond(epoch) : null;
        } catch (NumberFormatException ignored) {
            // Polar returns ISO-8601 timestamps.
        }
        try {
            return Instant.parse(text);
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (Exception alsoIgnored) {
                return null;
            }
        }
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
            String provider,
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
}
