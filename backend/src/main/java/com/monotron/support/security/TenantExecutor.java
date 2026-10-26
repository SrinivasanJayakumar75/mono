package com.monotron.support.security;

import java.util.function.Supplier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a unit of work inside its own transaction with a tenant bound to it.
 *
 * <p>Used by background jobs (PDF ingestion, AI replies) which run outside of an
 * HTTP request and therefore have to establish their own tenant scope.
 */
@Component
public class TenantExecutor {

    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final TenantBinding tenantBinding;

    public TenantExecutor(TransactionTemplate transactionTemplate, JdbcTemplate jdbcTemplate,
            TenantBinding tenantBinding) {
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.tenantBinding = tenantBinding;
    }

    public <T> T call(String workspaceId, Supplier<T> action) {
        return transactionTemplate.execute(status -> {
            tenantBinding.bind(workspaceId);
            try {
                return action.get();
            } finally {
                tenantBinding.clear();
            }
        });
    }

    public void run(String workspaceId, Runnable action) {
        call(workspaceId, () -> {
            action.run();
            return null;
        });
    }
}
