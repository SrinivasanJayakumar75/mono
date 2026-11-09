package com.monotron.support.channels;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.monotron.support.channels.dto.ChannelAutomationDto;
import com.monotron.support.channels.dto.UpdateChannelAutomationRequest;
import com.monotron.support.common.BadRequestException;
import com.monotron.support.common.ForbiddenException;
import com.monotron.support.common.Ids;
import com.monotron.support.security.AuthPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-workspace automation preferences for external chat channels.
 *
 * <p>The settings live in the shared inbox: a connected Gmail, WhatsApp or
 * Instagram conversation can be answered by the same AI and handed to the same
 * team as a website widget conversation.
 */
@Service
public class ChannelAutomationService {

    private static final List<String> CHANNELS = List.of("GMAIL", "WHATSAPP", "INSTAGRAM");

    private final JdbcTemplate jdbc;

    public ChannelAutomationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ChannelAutomationDto> list(AuthPrincipal principal) {
        requireWorkspace(principal);
        Map<String, ChannelAutomationDto> stored = new LinkedHashMap<>();
        jdbc.query("""
                select channel, account_label, enabled, auto_reply, handoff_to_human, updated_at
                from channel_automations
                """, rs -> {
            ChannelAutomationDto dto = map(rs);
            stored.put(dto.channel(), dto);
        });
        return CHANNELS.stream()
                .map(channel -> stored.getOrDefault(channel, defaults(channel)))
                .toList();
    }

    @Transactional
    public ChannelAutomationDto update(AuthPrincipal principal, String channel,
                                       UpdateChannelAutomationRequest request) {
        requireWorkspace(principal);
        requireEditor(principal);
        String normalized = normalizeChannel(channel);
        ChannelAutomationDto current = find(normalized).orElseGet(() -> defaults(normalized));

        String accountLabel = request.accountLabel() != null
                ? request.accountLabel().trim()
                : current.accountLabel();
        boolean enabled = request.enabled() != null ? request.enabled() : current.enabled();
        boolean autoReply = request.autoReply() != null ? request.autoReply() : current.autoReply();
        boolean handoffToHuman = request.handoffToHuman() != null
                ? request.handoffToHuman()
                : current.handoffToHuman();

        jdbc.update("""
                insert into channel_automations
                    (id, channel, account_label, enabled, auto_reply, handoff_to_human, updated_at)
                values (?, ?, ?, ?, ?, ?, now())
                on conflict (workspace_id, channel) do update
                set account_label = excluded.account_label,
                    enabled = excluded.enabled,
                    auto_reply = excluded.auto_reply,
                    handoff_to_human = excluded.handoff_to_human,
                    updated_at = now()
                """, Ids.uuid(), normalized, accountLabel, enabled, autoReply, handoffToHuman);

        return find(normalized).orElseThrow(() ->
                new IllegalStateException("Channel automation was not saved"));
    }

    private java.util.Optional<ChannelAutomationDto> find(String channel) {
        List<ChannelAutomationDto> rows = jdbc.query("""
                select channel, account_label, enabled, auto_reply, handoff_to_human, updated_at
                from channel_automations
                where channel = ?
                """, (rs, i) -> map(rs), channel);
        return rows.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(rows.get(0));
    }

    private ChannelAutomationDto map(ResultSet rs) throws SQLException {
        return new ChannelAutomationDto(
                rs.getString("channel"),
                rs.getString("account_label"),
                rs.getBoolean("enabled"),
                rs.getBoolean("auto_reply"),
                rs.getBoolean("handoff_to_human"),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant());
    }

    private ChannelAutomationDto defaults(String channel) {
        return new ChannelAutomationDto(channel, "", false, true, true, null);
    }

    private String normalizeChannel(String channel) {
        String normalized = channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
        if (!CHANNELS.contains(normalized)) {
            throw new BadRequestException("Unsupported channel. Use GMAIL, WHATSAPP or INSTAGRAM.");
        }
        return normalized;
    }

    private String requireWorkspace(AuthPrincipal principal) {
        if (principal == null || !principal.hasWorkspace()) {
            throw new ForbiddenException("No workspace is selected for this session");
        }
        return principal.workspaceId();
    }

    private void requireEditor(AuthPrincipal principal) {
        if (principal != null && (principal.isAdmin()
                || AuthPrincipal.ROLE_AGENT.equalsIgnoreCase(principal.role()))) {
            return;
        }
        throw new ForbiddenException("You do not have permission to change channel automation");
    }
}
