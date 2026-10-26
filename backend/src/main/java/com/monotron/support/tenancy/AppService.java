package com.monotron.support.tenancy;

import java.util.List;
import java.util.Optional;

import com.monotron.support.common.NotFoundException;
import com.monotron.support.tenancy.dto.AppDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Reads the messenger app of the tenant that is currently bound to the thread.
 *
 * <p>The query targets {@code apps}, which is protected by Row Level Security,
 * so it can only ever return the app of the active workspace.
 */
@Service
public class AppService {

    private final JdbcTemplate jdbc;

    public AppService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppDto> findCurrent() {
        List<AppDto> apps = jdbc.query("""
                select id, name, hmac_secret, ai_enabled, auto_reply, ai_model, ai_temperature, ai_system_prompt
                from apps
                order by created_at asc
                limit 1
                """, (rs, i) -> new AppDto(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("hmac_secret"),
                rs.getBoolean("ai_enabled"),
                rs.getBoolean("auto_reply"),
                rs.getString("ai_model"),
                rs.getDouble("ai_temperature"),
                rs.getString("ai_system_prompt")));
        return apps.isEmpty() ? Optional.empty() : Optional.of(apps.get(0));
    }

    public AppDto requireCurrent() {
        return findCurrent().orElseThrow(() -> new NotFoundException("This workspace has no messenger app"));
    }

    public String requireAppId() {
        return requireCurrent().id();
    }

    /**
     * Resolves the workspace that owns a messenger app. The lookup table is not
     * protected by RLS because it only bridges a public app id to a tenant.
     */
    public Optional<String> workspaceOfApp(String appId) {
        List<String> rows = jdbc.query("select workspace_id from app_workspace_lookup where app_id = ?",
                (rs, i) -> rs.getString(1), appId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
