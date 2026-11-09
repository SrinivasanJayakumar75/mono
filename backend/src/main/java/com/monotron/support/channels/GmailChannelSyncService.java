package com.monotron.support.channels;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monotron.support.ai.AnswerText;
import com.monotron.support.ai.RagService;
import com.monotron.support.ai.dto.AnswerDto;
import com.monotron.support.ai.dto.CitationDto;
import com.monotron.support.channels.GmailApiClient.GmailMessage;
import com.monotron.support.channels.GmailApiClient.MessageRef;
import com.monotron.support.common.BadRequestException;
import com.monotron.support.common.Ids;
import com.monotron.support.config.AppProperties;
import com.monotron.support.realtime.RealtimePublisher;
import com.monotron.support.security.TenantBinding;
import com.monotron.support.security.TenantContext;
import com.monotron.support.tenancy.AppService;
import com.monotron.support.tenancy.dto.AppDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Imports unread Gmail messages into the shared inbox and, when the workspace
 * has enabled Gmail auto-reply, sends the AI answer back through Gmail.
 *
 * <p>Each email is committed in its own transaction. That matters because the
 * reply is sent to Gmail before the transaction commits: if another email in
 * the same poll fails, it must not roll back the bookkeeping for replies that
 * were already delivered.
 */
@Service
public class GmailChannelSyncService {

    private static final Logger log = LoggerFactory.getLogger(GmailChannelSyncService.class);
    private static final int MAX_BODY_LENGTH = 20_000;

    private final JdbcTemplate jdbc;
    private final AppService appService;
    private final RagService ragService;
    private final GmailApiClient gmail;
    private final ChannelCredentialCrypto crypto;
    private final RealtimePublisher realtime;
    private final ObjectMapper mapper;
    private final AppProperties properties;
    private final TenantBinding tenantBinding;
    private final TransactionTemplate transaction;
    private final TransactionTemplate messageTransaction;

    public GmailChannelSyncService(JdbcTemplate jdbc,
                                   AppService appService,
                                   RagService ragService,
                                   GmailApiClient gmail,
                                   ChannelCredentialCrypto crypto,
                                   RealtimePublisher realtime,
                                   ObjectMapper mapper,
                                   AppProperties properties,
                                   TenantBinding tenantBinding,
                                   PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.appService = appService;
        this.ragService = ragService;
        this.gmail = gmail;
        this.crypto = crypto;
        this.realtime = realtime;
        this.mapper = mapper;
        this.properties = properties;
        this.tenantBinding = tenantBinding;
        this.transaction = new TransactionTemplate(transactionManager);
        this.messageTransaction = new TransactionTemplate(transactionManager);
        this.messageTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Polls the Gmail mailbox connected to one workspace.
     *
     * @return how many new customer messages were imported
     */
    public int syncWorkspace(String workspaceId) {
        SyncContext context = inTenant(workspaceId, transaction, this::loadContext);
        if (context == null) {
            return 0;
        }

        int imported = 0;
        for (MessageRef reference : context.messages()) {
            try {
                boolean added = Boolean.TRUE.equals(inTenant(
                        workspaceId, messageTransaction, () -> importOne(context, reference)));
                if (added) {
                    imported++;
                }
            } catch (RuntimeException ex) {
                log.warn("Could not import Gmail message {} for workspace {}",
                        reference.id(), workspaceId, ex);
            }
        }
        return imported;
    }

    private SyncContext loadContext() {
        List<CredentialRow> credentials = jdbc.query("""
                select account_label, encrypted_access_token, encrypted_refresh_token,
                       token_expires_at, scopes
                from channel_credentials
                where channel = 'GMAIL'
                """, (rs, i) -> new CredentialRow(
                rs.getString("account_label"),
                rs.getString("encrypted_access_token"),
                rs.getString("encrypted_refresh_token"),
                rs.getTimestamp("token_expires_at") == null
                        ? null
                        : rs.getTimestamp("token_expires_at").toInstant(),
                rs.getString("scopes")));
        if (credentials.isEmpty()) {
            return null;
        }

        Automation automation = automation();
        if (automation == null || !automation.enabled()) {
            return null;
        }

        AppDto app = appService.findCurrent().orElse(null);
        if (app == null) {
            return null;
        }

        CredentialRow credential = credentials.get(0);
        if (credential.scopes() == null
                || !credential.scopes().contains("https://www.googleapis.com/auth/gmail.readonly")) {
            throw new BadRequestException(
                    "Gmail is connected without permission to read email. "
                            + "Reconnect Gmail and allow read access to your messages.");
        }
        if (automation.autoReply()
                && !credential.scopes().contains("https://www.googleapis.com/auth/gmail.send")) {
            throw new BadRequestException(
                    "Gmail is connected without permission to send replies. "
                            + "Reconnect Gmail and allow sending email.");
        }
        String accessToken = accessToken(credential);
        String fromEmail = credential.accountLabel();
        if (fromEmail == null || !fromEmail.contains("@")) {
            fromEmail = gmail.profileEmail(accessToken);
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("Skipping Gmail sync for the active workspace: the connected account has no email address");
            return null;
        }

        int limit = properties.getChannels().getGmail().getMaxPerPoll();
        List<MessageRef> messages = gmail.unreadMessages(accessToken, limit <= 0 ? 10 : limit);
        return new SyncContext(app, automation, accessToken, fromEmail, messages);
    }

    private boolean importOne(SyncContext context, MessageRef reference) {
        if (alreadyImported(reference.id())) {
            markReadQuietly(context.accessToken(), reference.id());
            return false;
        }

        GmailMessage message;
        try {
            message = gmail.message(context.accessToken(), reference.id());
        } catch (RuntimeException ex) {
            log.warn("Could not read Gmail message {}", reference.id(), ex);
            return false;
        }
        if (message.fromEmail() == null || message.fromEmail().isBlank()
                || message.fromEmail().equalsIgnoreCase(context.fromEmail())) {
            markReadQuietly(context.accessToken(), message.id());
            return false;
        }

        importMessage(context, message);
        return true;
    }

    private void importMessage(SyncContext context, GmailMessage message) {
        ContactRow contact = resolveContact(context.app().id(), message);
        ConversationRow conversation = resolveConversation(context.app().id(), contact.id(), message);

        insertContactMessage(conversation.id(), contact.name(), message.body());
        touchConversation(conversation.id());
        insertInboundMarker(message.id(), conversation.id());

        if (context.automation().autoReply() && context.app().aiEnabled() && !conversation.escalated()) {
            String question = message.body() == null || message.body().isBlank()
                    ? message.subject()
                    : message.body();
            AnswerDto answer = ragService.answer(
                    question, context.app().aiSystemPrompt(), context.app().aiTemperature());
            String body = AnswerText.forAiMessage(answer.answer(), answer.citations());
            if (body == null || body.isBlank()) {
                body = "Thanks for your message. A teammate will follow up shortly.";
            }

            jdbc.update("""
                    insert into messages (id, conversation_id, author_type, author_name, body, created_at, citations)
                    values (?, ?, 'AI', ?, ?, now(), ?::jsonb)
                    """, Ids.uuid(), conversation.id(), context.app().name(), body,
                    writeCitations(answer.citations()));

            jdbc.update("""
                    update conversations
                    set ai_handled = true, unread_count = 0, last_activity_at = now(), updated_at = now()
                    where id = ?
                    """, conversation.id());

            gmail.sendReply(
                    context.accessToken(),
                    context.fromEmail(),
                    message.fromEmail(),
                    replySubject(message.subject()),
                    message.rfcMessageId(),
                    references(message),
                    message.threadId(),
                    body);
        }

        String workspaceId = TenantContext.get();
        afterCommit(() -> {
            markReadQuietly(context.accessToken(), message.id());
            if (workspaceId != null) {
                try {
                    realtime.messageCreated(workspaceId, conversation.id(), false);
                } catch (RuntimeException ex) {
                    log.debug("Could not publish the Gmail message event", ex);
                }
            }
        });
    }

    private ContactRow resolveContact(String appId, GmailMessage message) {
        String email = message.fromEmail().trim().toLowerCase(Locale.ROOT);
        List<ContactRow> existing = jdbc.query("""
                select id, name from contacts
                where app_id = ? and lower(email) = ?
                limit 1
                """, (rs, i) -> new ContactRow(rs.getString("id"), rs.getString("name")),
                appId, email);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        String name = message.fromName();
        if (name == null || name.isBlank()) {
            int at = email.indexOf('@');
            name = at > 0 ? email.substring(0, at) : email;
        }
        String contactId = Ids.uuid();
        jdbc.update("""
                insert into contacts (id, app_id, email, name, online, last_seen_at, created_at, updated_at)
                values (?, ?, ?, ?, false, now(), now(), now())
                """, contactId, appId, email, truncate(name, 255));
        return new ContactRow(contactId, truncate(name, 255));
    }

    private ConversationRow resolveConversation(String appId, String contactId, GmailMessage message) {
        if (message.threadId() != null && !message.threadId().isBlank()) {
            List<ConversationRow> existing = jdbc.query("""
                    select id, escalated from conversations
                    where app_id = ? and channel = 'GMAIL' and external_thread_id = ?
                    limit 1
                    """, (rs, i) -> new ConversationRow(rs.getString("id"), rs.getBoolean("escalated")),
                    appId, message.threadId());
            if (!existing.isEmpty()) {
                return existing.get(0);
            }
        }

        String conversationId = Ids.uuid();
        jdbc.update("""
                insert into conversations (id, app_id, contact_id, subject, status, channel,
                                          external_thread_id, ai_handled, unread_count, escalated,
                                          created_at, updated_at, last_activity_at)
                values (?, ?, ?, ?, 'OPEN', 'GMAIL', ?, false, 0, false, now(), now(), now())
                """, conversationId, appId, contactId, subject(message.subject()), message.threadId());
        return new ConversationRow(conversationId, false);
    }

    private void insertContactMessage(String conversationId, String authorName, String body) {
        jdbc.update("""
                insert into messages (id, conversation_id, author_type, author_name, body, created_at, citations)
                values (?, ?, 'CONTACT', ?, ?, now(), '[]'::jsonb)
                """, Ids.uuid(), conversationId, authorName, truncate(body, MAX_BODY_LENGTH));
    }

    private void touchConversation(String conversationId) {
        jdbc.update("""
                update conversations
                set status = 'OPEN', closed_at = null, last_activity_at = now(),
                    unread_count = unread_count + 1, updated_at = now()
                where id = ?
                """, conversationId);
    }

    private void insertInboundMarker(String providerMessageId, String conversationId) {
        jdbc.update("""
                insert into channel_inbound_messages (id, channel, provider_message_id, conversation_id)
                values (?, 'GMAIL', ?, ?)
                """, Ids.uuid(), providerMessageId, conversationId);
    }

    private boolean alreadyImported(String providerMessageId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from channel_inbound_messages
                where channel = 'GMAIL' and provider_message_id = ?
                """, Integer.class, providerMessageId);
        return count != null && count > 0;
    }

    /**
     * Runs provider side effects only after the inbox rows commit.
     *
     * <p>If marking a message read fails, the committed
     * {@code channel_inbound_messages} row still prevents the same email from
     * being answered twice on the next poll.
     */
    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void markReadQuietly(String accessToken, String messageId) {
        try {
            gmail.markRead(accessToken, messageId);
        } catch (RuntimeException ex) {
            log.debug("Could not mark Gmail message {} as read", messageId, ex);
        }
    }

    private String accessToken(CredentialRow credential) {
        String accessToken = crypto.decrypt(credential.encryptedAccessToken());
        if (accessToken != null && !accessToken.isBlank()
                && credential.expiresAt() != null
                && credential.expiresAt().isAfter(Instant.now().plusSeconds(120))) {
            return accessToken;
        }

        String refreshToken = crypto.decrypt(credential.encryptedRefreshToken());
        if (refreshToken == null || refreshToken.isBlank()) {
            if (accessToken != null && !accessToken.isBlank() && credential.expiresAt() == null) {
                return accessToken;
            }
            throw new BadRequestException(
                    "The Gmail connection has no refresh token. Reconnect Gmail.");
        }

        GmailApiClient.Token refreshed;
        try {
            refreshed = gmail.refresh(refreshToken);
        } catch (RuntimeException ex) {
            throw new BadRequestException(
                    "Gmail could not refresh the connection. Reconnect Gmail and allow read access.");
        }
        jdbc.update("""
                update channel_credentials
                set encrypted_access_token = ?, token_expires_at = ?, updated_at = now()
                where channel = 'GMAIL'
                """, crypto.encrypt(refreshed.accessToken()),
                refreshed.expiresAt() == null ? null : Timestamp.from(refreshed.expiresAt()));
        return refreshed.accessToken();
    }

    private Automation automation() {
        List<Automation> rows = jdbc.query("""
                select enabled, auto_reply, handoff_to_human
                from channel_automations
                where channel = 'GMAIL'
                """, (rs, i) -> new Automation(
                rs.getBoolean("enabled"),
                rs.getBoolean("auto_reply"),
                rs.getBoolean("handoff_to_human")));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Binds a tenant around one transaction. The previous tenant context is
     * restored because the manual sync endpoint already runs inside a
     * tenant-bound request transaction.
     */
    private <T> T inTenant(String workspaceId, TransactionTemplate template, Supplier<T> action) {
        return template.execute(status -> {
            String previous = TenantContext.get();
            tenantBinding.bind(workspaceId);
            try {
                return action.get();
            } finally {
                if (previous != null) {
                    TenantContext.set(previous);
                } else {
                    TenantContext.clear();
                }
            }
        });
    }

    private String references(GmailMessage message) {
        String references = message.references();
        if (message.rfcMessageId() == null || message.rfcMessageId().isBlank()) {
            return references;
        }
        return references == null || references.isBlank()
                ? message.rfcMessageId()
                : references + " " + message.rfcMessageId();
    }

    private static String replySubject(String subject) {
        String value = subject == null || subject.isBlank() ? "your message" : subject.strip();
        return value.toLowerCase(Locale.ROOT).startsWith("re:") ? value : "Re: " + value;
    }

    private static String subject(String subject) {
        return subject == null || subject.isBlank() ? "Gmail conversation" : truncate(subject, 500);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String writeCitations(List<CitationDto> citations) {
        try {
            return mapper.writeValueAsString(citations == null ? List.of() : citations);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private record SyncContext(
            AppDto app,
            Automation automation,
            String accessToken,
            String fromEmail,
            List<MessageRef> messages) {
    }

    private record CredentialRow(
            String accountLabel,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            Instant expiresAt,
            String scopes) {
    }

    private record ContactRow(String id, String name) {
    }

    private record ConversationRow(String id, boolean escalated) {
    }

    private record Automation(boolean enabled, boolean autoReply, boolean handoffToHuman) {
    }
}
