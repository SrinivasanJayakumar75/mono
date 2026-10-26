package com.monotron.support.security;

import java.util.regex.Pattern;

import com.monotron.support.config.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Binds a database transaction to a tenant.
 *
 * <p>Two things happen, in order:
 *
 * <ol>
 *   <li>the transaction is downgraded to the configured non-superuser role
 *       with {@code SET LOCAL ROLE}. This is what makes Row Level Security
 *       effective: superusers and roles with {@code BYPASSRLS} ignore
 *       policies, so the application never keeps those privileges while
 *       handling tenant data;</li>
 *   <li>{@code app.workspace_id} is published transaction-locally. The RLS
 *       policies read it through {@code app_workspace_id()}.</li>
 * </ol>
 *
 * <p>{@code SET LOCAL} is scoped to the current transaction, so both settings
 * disappear on commit and can never leak to the next request that reuses the
 * pooled connection.
 */
@Component
public class TenantBinding {

    /** Role names are interpolated into DDL, so they are validated first. */
    private static final Pattern ROLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,62}");

    private final JdbcTemplate jdbcTemplate;
    private final AppProperties properties;

    public TenantBinding(JdbcTemplate jdbcTemplate, AppProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    /**
     * Must be called inside an active transaction.
     */
    public void bind(String workspaceId) {
        String role = properties.getTenancy().getRole();
        if (role != null && !role.isBlank()) {
            if (!ROLE_NAME.matcher(role).matches()) {
                throw new IllegalStateException("monotron.tenancy.role is not a valid role name: " + role);
            }
            jdbcTemplate.execute("set local role " + role);
        }
        jdbcTemplate.queryForObject("select set_config('app.workspace_id', ?, true)", String.class, workspaceId);
        TenantContext.set(workspaceId);
    }

    public void clear() {
        TenantContext.clear();
    }
}
