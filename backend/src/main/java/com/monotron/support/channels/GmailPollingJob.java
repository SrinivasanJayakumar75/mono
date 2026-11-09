package com.monotron.support.channels;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.monotron.support.common.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls Gmail for every workspace that has connected a mailbox.
 *
 * <p>The query that discovers workspaces deliberately runs without a tenant
 * binding: the application database role can see the small list of workspaces
 * with credentials. Each workspace is then processed in its own transaction
 * with the non-superuser tenant role bound, so all reads and writes of inbox
 * data stay subject to Row Level Security.
 *
 * <p>Polling is used instead of Gmail push notifications because it works with
 * a normal OAuth client without requiring a Google Cloud Pub/Sub topic and a
 * public HTTPS webhook. The interval can be changed with
 * {@code GMAIL_POLL_INTERVAL_MS}; set {@code GMAIL_POLLING_ENABLED=false} to
 * disable the background job.
 */
@Component
@ConditionalOnProperty(prefix = "monotron.channels.gmail", name = "polling-enabled",
        havingValue = "true", matchIfMissing = true)
public class GmailPollingJob {

    private static final Logger log = LoggerFactory.getLogger(GmailPollingJob.class);

    private final JdbcTemplate jdbc;
    private final GmailChannelSyncService syncService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<String> warnedWorkspaces = ConcurrentHashMap.newKeySet();

    public GmailPollingJob(JdbcTemplate jdbc, GmailChannelSyncService syncService) {
        this.jdbc = jdbc;
        this.syncService = syncService;
    }

    @Scheduled(
            fixedDelayString = "${monotron.channels.gmail.poll-interval-ms:30000}",
            initialDelayString = "${monotron.channels.gmail.initial-delay-ms:15000}")
    public void poll() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            List<String> workspaces = jdbc.queryForList("""
                    select workspace_id from channel_credentials
                    where channel = 'GMAIL'
                    """, String.class);
            for (String workspaceId : workspaces) {
                try {
                    int imported = syncService.syncWorkspace(workspaceId);
                    warnedWorkspaces.remove(workspaceId);
                    if (imported > 0) {
                        log.info("Imported {} Gmail message(s) for workspace {}", imported, workspaceId);
                    }
                } catch (BadRequestException ex) {
                    if (warnedWorkspaces.add(workspaceId)) {
                        log.warn("Gmail sync skipped for workspace {}: {}", workspaceId, ex.getMessage());
                    }
                } catch (RuntimeException ex) {
                    if (warnedWorkspaces.add(workspaceId)) {
                        log.warn("Gmail sync failed for workspace {}", workspaceId, ex);
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Gmail polling failed", ex);
        } finally {
            running.set(false);
        }
    }
}
