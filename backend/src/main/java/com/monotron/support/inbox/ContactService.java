package com.monotron.support.inbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.monotron.support.common.ForbiddenException;
import com.monotron.support.common.NotFoundException;
import com.monotron.support.common.PageResponse;
import com.monotron.support.inbox.dto.ContactDetailDto;
import com.monotron.support.inbox.dto.ContactSummaryDto;
import com.monotron.support.inbox.dto.ConversationDto;
import com.monotron.support.security.AuthPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Visitors that started a conversation with the widget.
 */
@Service
public class ContactService {

    private final JdbcTemplate jdbc;

    public ContactService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<ContactSummaryDto> list(AuthPrincipal principal, String query, int page, int size) {
        requireWorkspace(principal);
        int limit = Math.max(1, Math.min(size, 100));
        int offset = Math.max(0, page) * limit;

        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            where.append(" and (lower(coalesce(ct.name, '')) like ? or lower(coalesce(ct.email, '')) like ?)");
            String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            params.add(like);
            params.add(like);
        }

        Long total = jdbc.queryForObject("select count(*) from contacts ct" + where, Long.class, params.toArray());
        params.add(limit);
        params.add(offset);
        List<ContactSummaryDto> contacts = jdbc.query("""
                select ct.id, ct.name, ct.email, ct.avatar_url, ct.visitor_id, ct.online, ct.browser, ct.os,
                       ct.location, ct.locale, ct.last_seen_at, ct.created_at,
                       (select count(*) from conversations c where c.contact_id = ct.id) as conversation_count
                from contacts ct
                """ + where + " order by ct.created_at desc limit ? offset ?",
                (rs, i) -> new ContactSummaryDto(
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
                        iso(rs.getTimestamp("last_seen_at")),
                        iso(rs.getTimestamp("created_at")),
                        rs.getLong("conversation_count")),
                params.toArray());

        return PageResponse.of(contacts, page, limit, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public ContactDetailDto get(AuthPrincipal principal, String contactId) {
        requireWorkspace(principal);
        List<ContactDetailDto> rows = jdbc.query("""
                select ct.id, ct.name, ct.email, ct.avatar_url, ct.visitor_id, ct.online, ct.browser, ct.os,
                       ct.location, ct.locale, ct.last_seen_at, ct.signed_up_at, ct.created_at,
                       ct.custom_attributes,
                       (select count(*) from conversations c where c.contact_id = ct.id) as conversation_count,
                       (select l.id from leads l where l.source_contact_id = ct.id
                        order by l.created_at desc limit 1) as crm_lead_id
                from contacts ct where ct.id = ?
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
                rs.getString("crm_lead_id")), contactId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Contact not found");
        }
        return rows.get(0);
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> conversationsOf(AuthPrincipal principal, String contactId) {
        requireWorkspace(principal);
        return jdbc.query("""
                select c.id, c.subject, c.status, c.channel, c.assignee_id, u.name as assignee_name,
                       c.team_id, t.name as team_name, c.ai_handled, c.escalated, c.unread_count,
                       c.created_at, c.last_activity_at, c.closed_at
                from conversations c
                left join users u on u.id = c.assignee_id
                left join teams t on t.id = c.team_id
                where c.contact_id = ?
                order by c.last_activity_at desc
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
                contactId);
    }

    private java.util.Map<String, Object> readAttributes(String json) {
        if (json == null || json.isBlank()) {
            return java.util.Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
                    });
        } catch (Exception ex) {
            return java.util.Map.of();
        }
    }

    private static String iso(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    private void requireWorkspace(AuthPrincipal principal) {
        if (principal == null || !principal.hasWorkspace()) {
            throw new ForbiddenException("No workspace is selected for this session");
        }
    }
}
