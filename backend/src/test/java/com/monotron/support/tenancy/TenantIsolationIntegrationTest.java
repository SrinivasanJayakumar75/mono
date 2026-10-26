package com.monotron.support.tenancy;

import com.monotron.support.common.Ids;
import com.monotron.support.security.TenantExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that PostgreSQL Row Level Security really isolates tenants through
 * the production code path (a transaction downgraded to {@code monotron_app}
 * with {@code app.workspace_id} bound).
 *
 * <p>Requires the local PostgreSQL instance, so it only runs when
 * {@code MONOTRON_IT=true} is set:
 *
 * <pre>
 *   $env:MONOTRON_IT='true'; ./gradlew test
 * </pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MONOTRON_IT", matches = "true")
class TenantIsolationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private TenantExecutor tenantExecutor;

    private String workspaceA;
    private String workspaceB;
    private String documentA;
    private String documentB;

    @AfterEach
    void cleanUp() {
        if (documentA != null) {
            jdbc.update("delete from content_chunks where source_type = 'DOCUMENT' and source_id = ?", documentA);
            jdbc.update("delete from documents where id = ?", documentA);
        }
        if (documentB != null) {
            jdbc.update("delete from content_chunks where source_type = 'DOCUMENT' and source_id = ?", documentB);
            jdbc.update("delete from documents where id = ?", documentB);
        }
        if (workspaceA != null) {
            jdbc.update("delete from workspaces where id = ?", workspaceA);
        }
        if (workspaceB != null) {
            jdbc.update("delete from workspaces where id = ?", workspaceB);
        }
    }

    @Test
    @DisplayName("tenant transactions really run as the restricted role")
    void transactionsRunAsRestrictedRole() {
        seed();

        String currentUser = tenantExecutor.call(workspaceA,
                () -> jdbc.queryForObject("select current_user", String.class));

        assertThat(currentUser).isEqualTo("monotron_app");
    }

    @Test
    @DisplayName("a tenant can read its own documents but not another tenant's")
    void readsAreIsolated() {
        seed();

        long ownVisible = tenantExecutor.call(workspaceA,
                () -> jdbc.queryForObject("select count(*) from documents where id = ?", Long.class, documentA));
        long foreignVisible = tenantExecutor.call(workspaceA,
                () -> jdbc.queryForObject("select count(*) from documents where id = ?", Long.class, documentB));
        long allVisible = tenantExecutor.call(workspaceA,
                () -> jdbc.queryForObject("select count(*) from documents", Long.class));

        assertThat(ownVisible).isEqualTo(1);
        assertThat(foreignVisible).isZero();
        assertThat(allVisible).isEqualTo(1);
    }

    @Test
    @DisplayName("a tenant cannot update another tenant's document")
    void writesAreIsolated() {
        seed();

        int updated = tenantExecutor.call(workspaceA, () -> jdbc.update(
                "update documents set title = 'hijacked' where id = ?", documentB));

        assertThat(updated).isZero();
        String title = jdbc.queryForObject("select title from documents where id = ?", String.class, documentB);
        assertThat(title).isEqualTo("Document B");
    }

    @Test
    @DisplayName("an insert is stamped with the bound tenant, never with a supplied foreign one")
    void insertsAreStampedWithTheBoundTenant() {
        seed();
        String injectedId = Ids.uuid();

        tenantExecutor.run(workspaceA, () -> jdbc.update("""
                insert into documents (id, filename, title, file_path, status, workspace_id)
                values (?, 'injected.pdf', 'Injected', '/tmp/injected.pdf', 'READY', ?)
                """, injectedId, workspaceB));

        try {
            String stampedWorkspace = jdbc.queryForObject(
                    "select workspace_id from documents where id = ?", String.class, injectedId);
            assertThat(stampedWorkspace).isEqualTo(workspaceA);
        } finally {
            jdbc.update("delete from documents where id = ?", injectedId);
        }
    }

    // ------------------------------------------------------------------

    private void seed() {
        workspaceA = createWorkspace("Isolation test A");
        workspaceB = createWorkspace("Isolation test B");
        documentA = createDocument(workspaceA, "Document A");
        documentB = createDocument(workspaceB, "Document B");
    }

    private String createWorkspace(String name) {
        String id = Ids.uuid();
        jdbc.update("""
                insert into workspaces (id, name, slug, created_at, updated_at)
                values (?, ?, ?, now(), now())
                """, id, name, Ids.slug(name, true));
        return id;
    }

    /**
     * Seeds a document as the privileged owner role. The workspace stamping
     * trigger reads {@code app.workspace_id}, so it is set transaction-locally
     * for the duration of the insert only.
     */
    private String createDocument(String workspaceId, String title) {
        String id = Ids.uuid();
        transactionTemplate.executeWithoutResult(status -> {
            jdbc.queryForObject("select set_config('app.workspace_id', ?, true)", String.class, workspaceId);
            jdbc.update("""
                    insert into documents (id, filename, title, file_path, status, workspace_id)
                    values (?, ?, ?, ?, 'READY', ?)
                    """, id, title.toLowerCase().replace(' ', '-') + ".pdf", title,
                    "/tmp/" + id + ".pdf", workspaceId);
        });
        return id;
    }
}
