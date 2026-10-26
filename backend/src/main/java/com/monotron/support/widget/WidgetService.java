package com.monotron.support.widget;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monotron.support.ai.RagService;
import com.monotron.support.ai.dto.AnswerDto;
import com.monotron.support.ai.dto.CitationDto;
import com.monotron.support.common.BadRequestException;
import com.monotron.support.common.Ids;
import com.monotron.support.common.NotFoundException;
import com.monotron.support.realtime.RealtimePublisher;
import com.monotron.support.security.TenantContext;
import com.monotron.support.widget.dto.WidgetConfigDto;
import com.monotron.support.widget.dto.WidgetConversationDto;
import com.monotron.support.widget.dto.WidgetMessageDto;
import com.monotron.support.widget.dto.WidgetSendRequest;
import com.monotron.support.widget.dto.WidgetSendResponseDto;
import com.monotron.support.widget.dto.WidgetSessionRequest;
import com.monotron.support.widget.dto.WidgetSessionResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The public messenger widget.
 *
 * <p>These endpoints are called by anonymous visitors, so they carry no JWT.
 * The tenant is instead resolved from the messenger app id by
 * {@code TenantTransactionFilter}, which binds the database session to the
 * owning workspace before the request reaches this service. Every statement
 * below therefore runs under the tenant's Row Level Security policies.
 */
@Service
public class WidgetService {

    private static final Logger log = LoggerFactory.getLogger(WidgetService.class);

    private static final int MAX_SUBJECT_LENGTH = 120;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final RagService ragService;
    private final ObjectMapper mapper;
    private final RealtimePublisher realtime;

    public WidgetService(JdbcTemplate jdbc, RagService ragService, ObjectMapper mapper, RealtimePublisher realtime) {
        this.jdbc = jdbc;
        this.ragService = ragService;
        this.mapper = mapper;
        this.realtime = realtime;
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public WidgetConfigDto config(String appId) {
        List<WidgetConfigDto> rows = jdbc.query("""
                select app_id, workspace_name, greeting, subtitle, color, intro_message,
                       input_placeholder, show_branding
                from widget_settings
                where app_id = ?
                """, (rs, i) -> new WidgetConfigDto(
                rs.getString("app_id"),
                rs.getString("workspace_name"),
                rs.getString("greeting"),
                rs.getString("subtitle"),
                rs.getString("color"),
                rs.getString("intro_message"),
                rs.getString("input_placeholder"),
                rs.getBoolean("show_branding")), appId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Unknown messenger app");
        }
        return rows.get(0);
    }

    // ------------------------------------------------------------------
    // Session
    // ------------------------------------------------------------------

    @Transactional
    public WidgetSessionResponseDto session(String appId, WidgetSessionRequest request) {
        App app = loadApp(appId);
        Contact contact = resolveContact(app, request.visitorId(), request.name(), request.email());
        return new WidgetSessionResponseDto(
                contact.id(),
                contact.visitorId(),
                contact.name(),
                contact.email(),
                conversationsOf(contact.id()));
    }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    @Transactional
    public WidgetSendResponseDto send(String appId, WidgetSendRequest request) {
        App app = loadApp(appId);

        String body = request.body() == null ? "" : request.body().trim();
        if (body.isEmpty()) {
            throw new BadRequestException("A message cannot be empty");
        }

        Contact contact = resolveContact(app, request.visitorId(), request.name(), request.email());
        String conversationId = resolveConversation(app, contact, request.conversationId(), body);

        WidgetMessageDto message = insertContactMessage(conversationId, contact.name(), body);
        touchConversation(conversationId);

        WidgetMessageDto aiReply = null;
        if (app.aiEnabled() && app.autoReply() && !escalated(conversationId)) {
            aiReply = answer(app, conversationId, body);
        }

        // The visitor already receives the contact message and any automatic AI
        // reply in this response. The inbox needs its own signal so an agent
        // watching the thread sees both without refreshing.
        String workspaceId = TenantContext.get();
        if (workspaceId != null) {
            realtime.messageCreated(workspaceId, conversationId, false);
        }

        return new WidgetSendResponseDto(conversationId, contact.id(), message, aiReply);
    }

    /**
     * Loads a visitor's conversation after checking that the visitor really
     * owns it. Used when the widget reconnects or receives a live signal.
     */
    @Transactional(readOnly = true)
    public WidgetConversationDto conversationForVisitor(String appId, String conversationId, String visitorId) {
        assertVisitorConversation(appId, conversationId, visitorId);
        return conversation(conversationId);
    }

    /**
     * Validates the public stream subscription without loading the history.
     */
    @Transactional(readOnly = true)
    public void assertVisitorConversation(String appId, String conversationId, String visitorId) {
        if (conversationId == null || conversationId.isBlank()
                || visitorId == null || visitorId.isBlank()) {
            throw new NotFoundException("Conversation not found");
        }
        List<String> rows = jdbc.query("""
                select c.id
                from conversations c
                join contacts ct on ct.id = c.contact_id
                where c.id = ? and c.app_id = ? and ct.visitor_id = ?
                """, (rs, i) -> rs.getString(1), conversationId, appId, visitorId.trim());
        if (rows.isEmpty()) {
            throw new NotFoundException("Conversation not found");
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Loads the messenger app and makes sure it belongs to the tenant that the
     * current transaction is bound to.
     */
    private App loadApp(String appId) {
        List<App> rows = jdbc.query("""
                select id, name, ai_enabled, auto_reply, ai_model, ai_temperature, ai_system_prompt
                from apps
                where id = ?
                """, (rs, i) -> new App(
                rs.getString("id"),
                rs.getString("name"),
                rs.getBoolean("ai_enabled"),
                rs.getBoolean("auto_reply"),
                rs.getString("ai_model"),
                rs.getDouble("ai_temperature"),
                rs.getString("ai_system_prompt")), appId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Unknown messenger app");
        }
        return rows.get(0);
    }

    /**
     * Finds the visitor, creating it on first contact.
     *
     * <p>The visitor id is the stable identity of a browser. When it is
     * unknown but an email address is known, the existing contact for that
     * address is reused so a returning visitor keeps a single history.
     */
    private Contact resolveContact(App app, String visitorId, String name, String email) {
        String normalisedEmail = trimToNull(email);

        if (visitorId != null && !visitorId.isBlank()) {
            Optional<Contact> existing = findByVisitor(app.id(), visitorId.trim());
            if (existing.isPresent()) {
                return updateContact(existing.get(), name, normalisedEmail);
            }
        }

        if (normalisedEmail != null) {
            Optional<Contact> byEmail = findByEmail(app.id(), normalisedEmail);
            if (byEmail.isPresent()) {
                return updateContact(byEmail.get(), name, normalisedEmail);
            }
        }

        String resolvedVisitorId = visitorId == null || visitorId.isBlank() ? Ids.uuid() : visitorId.trim();
        String contactId = Ids.uuid();
        String contactName = name == null || name.isBlank() ? guestName() : name.trim();

        jdbc.update("""
                insert into contacts (id, app_id, visitor_id, email, name, online, last_seen_at,
                                      created_at, updated_at)
                values (?, ?, ?, ?, ?, true, now(), now(), now())
                """, contactId, app.id(), resolvedVisitorId, normalisedEmail, contactName);

        return new Contact(contactId, resolvedVisitorId, contactName, normalisedEmail);
    }

    private Optional<Contact> findByVisitor(String appId, String visitorId) {
        List<Contact> rows = jdbc.query("""
                select id, visitor_id, name, email from contacts
                where app_id = ? and visitor_id = ?
                """, (rs, i) -> new Contact(
                rs.getString("id"),
                rs.getString("visitor_id"),
                rs.getString("name"),
                rs.getString("email")), appId, visitorId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private Optional<Contact> findByEmail(String appId, String email) {
        List<Contact> rows = jdbc.query("""
                select id, visitor_id, name, email from contacts
                where app_id = ? and lower(email) = ?
                """, (rs, i) -> new Contact(
                rs.getString("id"),
                rs.getString("visitor_id"),
                rs.getString("name"),
                rs.getString("email")), appId, email.toLowerCase(Locale.ROOT));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private Contact updateContact(Contact contact, String name, String email) {
        String newName = name == null || name.isBlank() ? contact.name() : name.trim();
        String newEmail = email == null ? contact.email() : email;

        jdbc.update("""
                update contacts
                set name = ?, email = ?, online = true, last_seen_at = now(), updated_at = now()
                where id = ?
                """, newName, newEmail, contact.id());

        return new Contact(contact.id(), contact.visitorId(), newName, newEmail);
    }

    /**
     * Returns the conversation to post into, opening a new one when needed.
     */
    private String resolveConversation(App app, Contact contact, String conversationId, String body) {
        if (conversationId != null && !conversationId.isBlank()) {
            List<String> rows = jdbc.query("""
                    select id from conversations
                    where id = ? and contact_id = ? and app_id = ?
                    """, (rs, i) -> rs.getString(1), conversationId.trim(), contact.id(), app.id());
            if (rows.isEmpty()) {
                throw new NotFoundException("Conversation not found");
            }
            return rows.get(0);
        }

        String id = Ids.uuid();
        jdbc.update("""
                insert into conversations (id, app_id, contact_id, subject, status, channel,
                                          ai_handled, unread_count, created_at, updated_at, last_activity_at)
                values (?, ?, ?, ?, 'OPEN', 'WIDGET', false, 0, now(), now(), now())
                """, id, app.id(), contact.id(), subjectOf(body));

        return id;
    }

    private WidgetMessageDto insertContactMessage(String conversationId, String authorName, String body) {
        String id = Ids.uuid();
        jdbc.update("""
                insert into messages (id, conversation_id, author_type, author_name, body, created_at, citations)
                values (?, ?, 'CONTACT', ?, ?, now(), '[]'::jsonb)
                """, id, conversationId, authorName, body);

        return new WidgetMessageDto(id, "CONTACT", authorName, body, nowIso(), List.of());
    }

    /**
     * Bumps the activity timestamp and the unread counter of the thread.
     */
    private void touchConversation(String conversationId) {
        jdbc.update("""
                update conversations
                set last_activity_at = now(), unread_count = unread_count + 1, updated_at = now()
                where id = ?
                """, conversationId);
    }

    /**
     * A conversation is escalated when a human agent has taken it over; the
     * assistant must stay quiet so the two do not talk over each other.
     */
    private boolean escalated(String conversationId) {
        List<Boolean> rows = jdbc.query("""
                select escalated from conversations where id = ?
                """, (rs, i) -> rs.getBoolean(1), conversationId);
        return !rows.isEmpty() && rows.get(0);
    }

    /**
     * Lets the AI answer the visitor straight away.
     */
    private WidgetMessageDto answer(App app, String conversationId, String question) {
        AnswerDto answer;
        try {
            answer = ragService.answer(question, app.aiSystemPrompt(), app.aiTemperature());
        } catch (RuntimeException ex) {
            log.warn("The AI auto reply failed for conversation {}", conversationId, ex);
            return null;
        }

        String id = Ids.uuid();
        jdbc.update("""
                insert into messages (id, conversation_id, author_type, author_name, body, created_at, citations)
                values (?, ?, 'AI', ?, ?, now(), ?::jsonb)
                """, id, conversationId, app.name(), answer.answer(), writeCitations(answer.citations()));

        jdbc.update("""
                update conversations
                set ai_handled = true, unread_count = 0, last_activity_at = now(), updated_at = now()
                where id = ?
                """, conversationId);

        return new WidgetMessageDto(id, "AI", app.name(), answer.answer(), nowIso(),
                answer.citations() == null ? List.of() : answer.citations());
    }

    private List<WidgetConversationDto> conversationsOf(String contactId) {
        List<String> conversationIds = jdbc.query("""
                select id from conversations
                where contact_id = ?
                order by last_activity_at desc
                """, (rs, i) -> rs.getString(1), contactId);

        List<WidgetConversationDto> conversations = new ArrayList<>(conversationIds.size());
        for (String conversationId : conversationIds) {
            conversations.add(conversation(conversationId));
        }
        return conversations;
    }

    private WidgetConversationDto conversation(String conversationId) {
        List<WidgetConversationDto> rows = jdbc.query("""
                select id, subject, status, created_at, last_activity_at
                from conversations where id = ?
                """, (rs, i) -> new WidgetConversationDto(
                rs.getString("id"),
                rs.getString("subject"),
                rs.getString("status"),
                iso(rs.getTimestamp("created_at")),
                iso(rs.getTimestamp("last_activity_at")),
                List.of()), conversationId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Conversation not found");
        }

        WidgetConversationDto base = rows.get(0);
        List<WidgetMessageDto> messages = jdbc.query("""
                select id, author_type, author_name, body, created_at, citations
                from messages
                where conversation_id = ?
                order by created_at asc
                """, (rs, i) -> new WidgetMessageDto(
                rs.getString("id"),
                rs.getString("author_type"),
                rs.getString("author_name"),
                rs.getString("body"),
                iso(rs.getTimestamp("created_at")),
                readCitations(rs.getString("citations"))), conversationId);

        return new WidgetConversationDto(
                base.id(), base.subject(), base.status(), base.createdAt(), base.lastActivityAt(), messages);
    }

    // ------------------------------------------------------------------

    private String writeCitations(List<CitationDto> citations) {
        try {
            return mapper.writeValueAsString(citations == null ? List.of() : citations);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private List<CitationDto> readCitations(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, mapper.getTypeFactory()
                    .constructCollectionType(List.class, CitationDto.class));
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private static String subjectOf(String body) {
        String single = body.replaceAll("\\s+", " ").trim();
        return single.length() <= MAX_SUBJECT_LENGTH ? single : single.substring(0, MAX_SUBJECT_LENGTH) + "…";
    }

    private static String guestName() {
        return "Guest " + (1000 + RANDOM.nextInt(9000));
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String iso(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    private static String nowIso() {
        return java.time.Instant.now().toString();
    }

    /**
     * The messenger app of the current tenant.
     */
    private record App(
            String id,
            String name,
            boolean aiEnabled,
            boolean autoReply,
            String aiModel,
            double aiTemperature,
            String aiSystemPrompt) {
    }

    /**
     * A visitor.
     */
    private record Contact(String id, String visitorId, String name, String email) {
    }
}
