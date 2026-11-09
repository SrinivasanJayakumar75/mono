package com.monotron.support.channels;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.monotron.support.channels.dto.ChannelConnectionDto;
import com.monotron.support.common.BadRequestException;
import com.monotron.support.common.ForbiddenException;
import com.monotron.support.config.AppProperties;
import com.monotron.support.security.AuthPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and revokes the credentials a workspace has connected.
 */
@Service
public class ChannelConnectionService {

    static final List<String> CHANNELS = List.of("GMAIL", "WHATSAPP", "INSTAGRAM");

    private final JdbcTemplate jdbc;
    private final AppProperties properties;

    public ChannelConnectionService(JdbcTemplate jdbc, AppProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<ChannelConnectionDto> list(AuthPrincipal principal) {
        requireWorkspace(principal);
        Map<String, ChannelConnectionDto> stored = new LinkedHashMap<>();
        jdbc.query("""
                select channel, account_label, external_account_id, token_expires_at, scopes, connected_at
                from channel_credentials
                """, rs -> {
            String channel = rs.getString("channel");
            stored.put(channel, new ChannelConnectionDto(
                    channel,
                    true,
                    rs.getString("account_label"),
                    rs.getString("external_account_id"),
                    rs.getTimestamp("token_expires_at") == null
                            ? null
                            : rs.getTimestamp("token_expires_at").toInstant(),
                    rs.getString("scopes"),
                    rs.getTimestamp("connected_at") == null
                            ? null
                            : rs.getTimestamp("connected_at").toInstant(),
                    oauthAvailable(channel)));
        });
        return CHANNELS.stream()
                .map(channel -> stored.getOrDefault(channel, disconnected(channel)))
                .toList();
    }

    @Transactional
    public void disconnect(AuthPrincipal principal, String channel) {
        requireWorkspace(principal);
        requireEditor(principal);
        jdbc.update("delete from channel_credentials where channel = ?", normalizeChannel(channel));
    }

    /**
     * Guards actions that fetch from or send to a provider.
     */
    public void requireManage(AuthPrincipal principal) {
        requireWorkspace(principal);
        requireEditor(principal);
    }

    private ChannelConnectionDto disconnected(String channel) {
        return new ChannelConnectionDto(channel, false, "", null, null, "", null, oauthAvailable(channel));
    }

    private boolean oauthAvailable(String channel) {
        return switch (channel) {
            case "GMAIL" -> properties.getChannels().getGmail().isConfigured();
            case "WHATSAPP", "INSTAGRAM" -> properties.getChannels().getMeta().isConfigured();
            default -> false;
        };
    }

    static String normalizeChannel(String channel) {
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
        throw new ForbiddenException("You do not have permission to change channel connections");
    }
}
