package com.monotron.support.security;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds every HTTP request to a tenant and publishes that tenant to PostgreSQL.
 *
 * <p>The whole request runs inside a single database transaction. At the start of
 * that transaction the session variable {@code app.workspace_id} is set with
 * {@code set_config(..., true)}, i.e. <em>transaction locally</em>. The Row Level
 * Security policies of every tenant table then evaluate against that value, so
 *
 * <ul>
 *   <li>a query can never read another tenant's rows, and</li>
 *   <li>an insert can never land in another tenant's slice — the
 *       {@code force_row_workspace()} trigger stamps the tenant column from the
 *       very same session variable.</li>
 * </ul>
 *
 * <p>Because the setting is transaction-local it is discarded on commit and
 * cannot leak into the next request that reuses the pooled connection.
 *
 * <p>The tenant is resolved from the signed JWT ({@code wid} claim) for
 * authenticated calls, or from the messenger app id for the public widget
 * endpoints.
 */
@Component
public class TenantTransactionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantTransactionFilter.class);

    /** Public widget endpoints: {@code /api/widget/{appId}/...} */
    private static final Pattern WIDGET_PATH = Pattern.compile("^/api/widget/([A-Za-z0-9_-]{1,32})(/.*)?$");

    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final TenantBinding tenantBinding;

    public TenantTransactionFilter(TransactionTemplate transactionTemplate, JdbcTemplate jdbcTemplate,
            TenantBinding tenantBinding) {
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.tenantBinding = tenantBinding;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String workspaceId = resolveWorkspaceId(request);

        if (workspaceId == null) {
            // Public / unauthenticated endpoint: run without a tenant binding.
            // Any query touching a tenant table will be filtered out by RLS.
            chain.doFilter(request, response);
            return;
        }

        try {
            runInTenant(workspaceId, request, response, chain);
        } catch (UnexpectedRollbackException ex) {
            // The chain already rendered a handled error (e.g. 403/404): the
            // tenant transaction is rollback-only by design, but the response
            // is complete, so do not let the rollback escape into an /error
            // dispatch that would append a second body to the payload.
            if (!response.isCommitted()) {
                throw ex;
            }
            log.debug("Tenant transaction rolled back after the response was committed", ex);
        } catch (FilterChainException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof ServletException se) {
                throw se;
            }
            throw ex;
        }
    }

    private void runInTenant(String workspaceId, HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) {
        transactionTemplate.executeWithoutResult(status -> {
            tenantBinding.bind(workspaceId);
            try {
                chain.doFilter(request, response);
            } catch (IOException | ServletException ex) {
                throw new FilterChainException(ex);
            } finally {
                tenantBinding.clear();
            }
        });
    }

    /** Carries a checked exception out of the transaction callback. */
    private static final class FilterChainException extends RuntimeException {
        FilterChainException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Resolves the tenant for the current request, or {@code null} when the
     * request is not bound to a tenant.
     */
    private String resolveWorkspaceId(HttpServletRequest request) {
        Matcher widgetMatcher = WIDGET_PATH.matcher(request.getRequestURI());
        if (widgetMatcher.matches()) {
            String appId = widgetMatcher.group(1);
            request.setAttribute(TenantTransactionFilter.class.getName() + ".appId", appId);
            return lookupWorkspaceByAppId(appId);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthPrincipal principal = AuthPrincipal.from(authentication);
        if (principal == null || !principal.hasWorkspace()) {
            return null;
        }
        return principal.workspaceId();
    }

    /**
     * Resolves the tenant owning a messenger app. The lookup table is not
     * protected by RLS on purpose: it is the bridge between a public app id and
     * a tenant, and it exposes nothing but an identifier.
     */
    private String lookupWorkspaceByAppId(String appId) {
        try {
            return jdbcTemplate.query(
                    "select workspace_id from app_workspace_lookup where app_id = ?",
                    rs -> rs.next() ? rs.getString(1) : null,
                    appId);
        } catch (RuntimeException ex) {
            log.warn("Unable to resolve workspace for app {}", appId, ex);
            return null;
        }
    }
}
