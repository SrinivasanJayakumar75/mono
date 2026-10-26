package com.monotron.support.inbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.monotron.support.ai.RagService;
import com.monotron.support.ai.dto.AnswerDto;
import com.monotron.support.ai.dto.CitationDto;
import com.monotron.support.common.BadRequestException;
import com.monotron.support.common.ForbiddenException;
import com.monotron.support.common.Ids;
import com.monotron.support.common.NotFoundException;
import com.monotron.support.common.PageResponse;
import com.monotron.support.inbox.dto.AssignRequest;
import com.monotron.support.inbox.dto.ConversationDetailDto;
import com.monotron.support.inbox.dto.ConversationDto;
import com.monotron.support.inbox.dto.ConversationStatsDto;
import com.monotron.support.inbox.dto.ConversationSummaryDto;
import com.monotron.support.inbox.dto.ContactDetailDto;
import com.monotron.support.inbox.dto.MessageDto;
import com.monotron.support.realtime.RealtimePublisher;
import com.monotron.support.security.AuthPrincipal;
import com.monotron.support.tenancy.AppService;
import com.monotron.support.tenancy.dto.AppDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shared inbox: conversations, messages and the AI agent.
 */
@Service
public class ConversationService {

    private static final String AUTHOR_CONTACT = "CONTACT";
    private static final String AUTHOR_AGENT = "AGENT";
    private static final String AUTHOR_AI = "AI";

    private final JdbcTemplate jdbc;
    private final RagService ragService;
    private final AppService appService;
    private final ObjectMapper mapper;
    private final RealtimePublisher realtime;

    public ConversationService(JdbcTemplate jdbc, RagService ragService, AppService appService, ObjectMapper mapper,
            RealtimePublisher realtime) {
        this.jdbc = jdbc;
        this.ragService = ragService;
        this.appService = appService;
        this.mapper = mapper;
        this.realtime = realtime;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ConversationSummaryDto> list(AuthPrincipal principal, String status, String query,
            String assigneeId, String teamId, int page, int size) {
        requireWorkspace(principal);
        int limit = Math.max(1, Math.min(size, 100));
        int offset = Math.max(0, page) * limit;

        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" and c.status = ?");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (query != null && !query.isBlank()) {
            where.append(" and (lower(coalesce(c.subject, '')) like ? or lower(coalesce(ct.name, '')) like ? "
                    + "or lower(coalesce(ct.email, '')) like ?)");
            String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (assigneeId != null && !assigneeId.isBlank()) {
            where.append(" and c.assignee_id = ?");
            params.add(assigneeId);
        }
        if (teamId != null && !teamId.isBlank()) {
            where.append(" and c.team_id = ?");
            params.add(teamId);
        }

        Long total = jdbc.queryForObject("""
                select count(*) from conversations c join contacts ct on ct.id = c.contact_id
                """ + where, Long.class, params.toArray());

        params.add(limit);
        params.add(offset);
        List<ConversationSummaryDto> conversations = jdbc.query("""
                select c.id, c.subject, c.status, c.channel, c.created_at, c.last_activity_at, c.closed_at,
                       c.assignee_id, u.name as assignee_name, c.team_id, t.name as team_name,
                       c.ai_handled, c.escalated, c.unread_count,
                       ct.id as contact_id, ct.name as contact_name, ct.email as contact_email,
                       ct.avatar_url as contact_avatar, ct.visitor_id, ct.online, ct.browser, ct.os, ct.location,
                       ct.locale, ct.last_seen_at,
                       m.id as message_id, m.author_type, m.author_name, m.body, m.created_at as message_created_at
                from conversations c
                join contacts ct on ct.id = c.contact_id
                left join users u on u.id = c.assignee_id
                left join teams t on t.id = c.team_id
                left join lateral (
                    select id, author_type, author_name, body, created_at
                    from messages
                    where conversation_id = c.id
                    order by created_at desc
                    limit 1
                ) m on true
                """ + where + " order by c.last_activity_at desc limit ? offset ?",
                (rs, i) -> new ConversationSummaryDto(
                        rs.getString("id"),
                        rs.getString("subject"),
                        rs.getString("status"),
                        rs.getString("channel"),
                        iso(rs.getTimestamp("created_at")),
                        iso(rs.getTimestamp("last_activity_at")),
                        iso(rs.getTimestamp("closed_at")),
                        rs.getString("assignee_id"),
                        rs.getString("assignee_name"),
                        rs.getString("team_id"),
                        rs.getString("team_name"),
                        rs.getBoolean("ai_handled"),
                        rs.getBoolean("escalated"),
                        rs.getInt("unread_count"),
                        new com.monotron.support.inbox.dto.ContactDto(
                                rs.getString("contact_id"),
                                rs.getString("contact_name"),
                                rs.getString("contact_email"),
                                rs.getString("contact_avatar"),
                                rs.getString("visitor_id"),
                                rs.getBoolean("online"),
                                rs.getString("browser"),
                                rs.getString("os"),
                                rs.getString("location"),
                                rs.getString("locale"),
                                rs.getTimestamp("last_seen_at") == null ? null
                                        : rs.getTimestamp("last_seen_at").toInstant(),
                                rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant()),
                        rs.getString("message_id") == null ? null : new ConversationSummaryDto.LastMessageDto(
                                rs.getString("message_id"),
                                rs.getString("author_type"),
                                rs.getString("author_name"),
                                rs.getString("body"),
                                iso(rs.getTimestamp("message_created_at")))),
                params.toArray());

        return PageResponse.of(conversations, page, limit, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public ConversationDetailDto get(AuthPrincipal principal, String conversationId) {
        requireWorkspace(principal);
        ConversationDto conversation = load(conversationId);
        ContactDetailDto contact = loadContact(conversationId);
        List<MessageDto> messages = jdbc.query("""
                select id, author_type, author_id, author_name, author_avatar_url, body, created_at, citations, feedback
                from messages
                where conversation_id = ?
                order by created_at asc
                """, (rs, i) -> new MessageDto(
                rs.getString("id"),
                rs.getString("author_type"),
                rs.getString("author_id"),
                rs.getString("author_name"),
                rs.getString("author_avatar_url"),
                rs.getString("body"),
                rs.getTimestamp("created_at").toInstant(),
                readCitations(rs.getString("citations")),
                rs.getString("feedback")), conversationId);
        return new ConversationDetailDto(conversation, contact, messages);
    }

    @Transactional(readOnly = true)
    public ConversationStatsDto stats(AuthPrincipal principal) {
        requireWorkspace(principal);
        List<ConversationStatsDto> rows = jdbc.query("""
                select
                  count(*) filter (where status = 'OPEN') as open_count,
                  count(*) filter (where status = 'CLOSED') as closed_count,
                  count(*) filter (where unread_count > 0) as unread_count,
                  count(*) filter (where ai_handled) as ai_count,
                  count(*) as total_count
                from conversations
                """, (rs, i) -> new ConversationStatsDto(
                rs.getInt("open_count"),
                rs.getInt("closed_count"),
                rs.getInt("unread_count"),
                rs.getInt("ai_count"),
                rs.getInt("total_count")));
        return rows.isEmpty() ? new ConversationStatsDto(0, 0, 0, 0, 0) : rows.get(0);
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    @Transactional
    public MessageDto reply(AuthPrincipal principal, String conversationId, String body) {
        String workspaceId = requireWorkspace(principal);
        load(conversationId);
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("A message cannot be empty");
        }

        String messageId = Ids.uuid();
        jdbc.update("""
                insert into messages (id, conversation_id, author_type, author_id, author_name, body,
                                      created_at, citations, feedback)
                values (?, ?, 'AGENT', ?, ?, ?, now(), '[]'::jsonb, null)
                """, messageId, conversationId, principal.userId(), principal.name(), trimmed);

        jdbc.update("""
                update conversations
                set last_activity_at = now(), unread_count = 0, updated_at = now()
                where id = ?
                """, conversationId);

        MessageDto message = message(messageId);
        realtime.messageCreated(workspaceId, conversationId, true);
        return message;
    }

    @Transactional
    public ConversationDto assign(AuthPrincipal principal, String conversationId, String assigneeId) {
        String workspaceId = requireWorkspace(principal);
        load(conversationId);
        jdbc.update("update conversations set assignee_id = ?, updated_at = now() where id = ?",
                blankToNull(assigneeId), conversationId);
        ConversationDto updated = load(conversationId);
        realtime.conversationUpdated(workspaceId, conversationId);
        return updated;
    }

    @Transactional
    public ConversationDto routeToTeam(AuthPrincipal principal, String conversationId, String teamId) {
        String workspaceId = requireWorkspace(principal);
        load(conversationId);
        jdbc.update("update conversations set team_id = ?, updated_at = now() where id = ?",
                blankToNull(teamId), conversationId);
        ConversationDto updated = load(conversationId);
        realtime.conversationUpdated(workspaceId, conversationId);
        return updated;
    }

    @Transactional
    public ConversationDto close(AuthPrincipal principal, String conversationId) {
        String workspaceId = requireWorkspace(principal);
        load(conversationId);
        jdbc.update("""
                update conversations
                set status = 'CLOSED', closed_at = now(), last_activity_at = now(), updated_at = now()
                where id = ?
                """, conversationId);
        ConversationDto updated = load(conversationId);
        realtime.conversationUpdated(workspaceId, conversationId);
        return updated;
    }

    @Transactional
    public ConversationDto reopen(AuthPrincipal principal, String conversationId) {
        String workspaceId = requireWorkspace(principal);
        load(conversationId);
        jdbc.update("""
                update conversations
                set status = 'OPEN', closed_at = null, last_activity_at = now(), updated_at = now()
                where id = ?
                """, conversationId);
        ConversationDto updated = load(conversationId);
        realtime.conversationUpdated(workspaceId, conversationId);
        return updated;
    }

    /**
     * Pauses (or resumes) the widget's automatic AI replies for a conversation.
     *
     * <p>While escalated, a human is handling the thread: new visitor messages
     * are still recorded but the assistant no longer answers them on its own.
     * Escalating also takes ownership of an unassigned conversation.
     */
    @Transactional
    public ConversationDto escalate(AuthPrincipal principal, String conversationId, boolean escalated) {
        String workspaceId = requireWorkspace(principal);
        load(conversationId);

        if (escalated) {
            jdbc.update("""
                    update conversations
                    set escalated = true,
                        assignee_id = coalesce(assignee_id, ?),
                        last_activity_at = now(),
                        updated_at = now()
                    where id = ?
                    """, principal.userId(), conversationId);
        } else {
            jdbc.update("""
                    update conversations
                    set escalated = false, updated_at = now()
                    where id = ?
                    """, conversationId);
        }
        ConversationDto updated = load(conversationId);
        realtime.conversationUpdated(workspaceId, conversationId);
        return updated;
    }

    /**
     * Asks the AI agent to answer a conversation and posts its answer.
     */
    @Transactional
    public MessageDto aiReply(AuthPrincipal principal, String conversationId, String question) {
        String workspaceId = requireWorkspace(principal);
        load(conversationId);

        String prompt = question;
        if (prompt == null || prompt.isBlank()) {
            prompt = lastCustomerMessage(conversationId);
        }
        if (prompt == null || prompt.isBlank()) {
            throw new BadRequestException("There is no question to answer yet");
        }

        AppDto app = appService.findCurrent().orElse(null);
        double temperature = app == null ? 0.2 : app.aiTemperature();
        String systemPrompt = app == null ? null : app.aiSystemPrompt();

        AnswerDto answer = ragService.answer(prompt, systemPrompt, temperature);

        String messageId = Ids.uuid();
        jdbc.update("""
                insert into messages (id, conversation_id, author_type, author_name, body, created_at, citations)
                values (?, ?, 'AI', ?, ?, now(), ?::jsonb)
                """, messageId, conversationId, app == null ? "Assistant" : app.name(), answer.answer(),
                writeCitations(answer.citations()));

        jdbc.update("""
                update conversations
                set ai_handled = true, last_activity_at = now(), updated_at = now()
                where id = ?
                """, conversationId);

        MessageDto message = message(messageId);
        realtime.messageCreated(workspaceId, conversationId, true);
        return message;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    public ConversationDto load(String conversationId) {
        List<ConversationDto> rows = jdbc.query("""
                select c.id, c.subject, c.status, c.channel, c.assignee_id, u.name as assignee_name,
                       c.team_id, t.name as team_name, c.ai_handled, c.escalated, c.unread_count,
                       c.created_at, c.last_activity_at, c.closed_at
                from conversations c
                left join users u on u.id = c.assignee_id
                left join teams t on t.id = c.team_id
                where c.id = ?
                """, (rs, i) -> new ConversationDto(
                rs.getString("id"),
                rs.getString("subject"),
                rs.getString("status"),
                rs.getString("channel"),
                rs.getString("assignee_id"),
                rs.getString("assignee_name"),
                rs.getString("team_id"),
                rs.getString("team_name"),
                rs.getBoolean("ai_handled"),
                rs.getBoolean("escalated"),
                rs.getInt("unread_count"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_activity_at").toInstant(),
                rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toInstant()),
                conversationId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Conversation not found");
        }
        return rows.get(0);
    }

    private ContactDetailDto loadContact(String conversationId) {
        List<ContactDetailDto> rows = jdbc.query("""
                select ct.id, ct.name, ct.email, ct.avatar_url, ct.visitor_id, ct.online, ct.browser, ct.os,
                       ct.location, ct.locale, ct.last_seen_at, ct.signed_up_at, ct.created_at,
                       ct.custom_attributes,
                       (select count(*) from conversations c2 where c2.contact_id = ct.id) as conversation_count,
                       (select l.id from leads l where l.source_contact_id = ct.id
                        order by l.created_at desc limit 1) as crm_lead_id
                from contacts ct
                join conversations c on c.contact_id = ct.id
                where c.id = ?
                """, (rs, i) -> new ContactDetailDto(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("avatar_url"),
                rs.getString("visitor_id"),
                rs.getBoolean("online"),
                rs.getString("browser"),
                rs.getString("os"),
                rs.getString("location"),
                rs.getString("locale"),
                rs.getTimestamp("last_seen_at") == null ? null : rs.getTimestamp("last_seen_at").toInstant(),
                rs.getTimestamp("signed_up_at") == null ? null : rs.getTimestamp("signed_up_at").toInstant(),
                rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant(),
                readAttributes(rs.getString("custom_attributes")),
                rs.getLong("conversation_count"),
                rs.getString("crm_lead_id")), conversationId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Contact not found");
        }
        return rows.get(0);
    }

    private MessageDto message(String messageId) {
        List<MessageDto> rows = jdbc.query("""
                select id, author_type, author_id, author_name, author_avatar_url, body, created_at, citations, feedback
                from messages where id = ?
                """, (rs, i) -> new MessageDto(
                rs.getString("id"),
                rs.getString("author_type"),
                rs.getString("author_id"),
                rs.getString("author_name"),
                rs.getString("author_avatar_url"),
                rs.getString("body"),
                rs.getTimestamp("created_at").toInstant(),
                readCitations(rs.getString("citations")),
                rs.getString("feedback")), messageId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Message not found");
        }
        return rows.get(0);
    }

    private String lastCustomerMessage(String conversationId) {
        List<String> bodies = jdbc.query("""
                select body from messages
                where conversation_id = ? and author_type = 'CONTACT'
                order by created_at desc limit 1
                """, (rs, i) -> rs.getString(1), conversationId);
        return bodies.isEmpty() ? null : bodies.get(0);
    }

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

    private java.util.Map<String, Object> readAttributes(String json) {
        if (json == null || json.isBlank()) {
            return java.util.Map.of();
        }
        try {
            return mapper.readValue(json, mapper.getTypeFactory()
                    .constructMapType(java.util.Map.class, String.class, Object.class));
        } catch (JsonProcessingException ex) {
            return java.util.Map.of();
        }
    }

    private static String iso(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String requireWorkspace(AuthPrincipal principal) {
        if (principal == null || !principal.hasWorkspace()) {
            throw new ForbiddenException("No workspace is selected for this session");
        }
        return principal.workspaceId();
    }

    /** Exposed so the widget can reuse the same author constants. */
    public static String authorContact() {
        return AUTHOR_CONTACT;
    }

    public static String authorAgent() {
        return AUTHOR_AGENT;
    }

    public static String authorAi() {
        return AUTHOR_AI;
    }
}
