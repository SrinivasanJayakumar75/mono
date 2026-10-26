package com.monotron.support;

import com.monotron.support.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Monotron — multi-tenant AI customer support platform.
 *
 * <p>Tenancy is enforced at the database level with PostgreSQL Row Level Security.
 * The active tenant is published to the database as the session variable
 * {@code app.workspace_id}, set transaction-locally on every request by
 * {@link com.monotron.support.security.TenantTransactionFilter}.
 */
@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
public class MonotronApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonotronApplication.class, args);
    }
}
