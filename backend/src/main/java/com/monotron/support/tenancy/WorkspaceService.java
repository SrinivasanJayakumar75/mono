package com.monotron.support.tenancy;

import java.util.List;
import java.util.Locale;

import com.monotron.support.common.ConflictException;
import com.monotron.support.common.ForbiddenException;
import com.monotron.support.common.NotFoundException;
import com.monotron.support.security.AuthPrincipal;
import com.monotron.support.tenancy.dto.AppDto;
import com.monotron.support.tenancy.dto.MemberDto;
import com.monotron.support.tenancy.dto.UpdateWidgetSettingsRequest;
import com.monotron.support.tenancy.dto.WidgetSettingsDto;
import com.monotron.support.tenancy.dto.WorkspaceDetailDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workspace administration: profile, members and widget settings.
 */
@Service
public class WorkspaceService {

    private final JdbcTemplate jdbc;
    private final AppService appService;

    public WorkspaceService(JdbcTemplate jdbc, AppService appService) {
        this.jdbc = jdbc;
        this.appService = appService;
    }

    // ------------------------------------------------------------------
    // Workspace
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public WorkspaceDetailDto get(AuthPrincipal principal) {
        String workspaceId = requireWorkspace(principal);
        WorkspaceDetailDto detail = load(workspaceId);
        return detail;
    }

    @Transactional
    public WorkspaceDetailDto update(AuthPrincipal principal, String name) {
        String workspaceId = requireWorkspace(principal);
        jdbc.update("update workspaces set name = ?, updated_at = now() where id = ?", name.trim(), workspaceId);
        // Keep the messenger app and the widget in sync with the new name.
        jdbc.update("update apps set name = ? where workspace_id = ?", name.trim(), workspaceId);
        jdbc.update("update widget_settings set workspace_name = ?, updated_at = now() "
                + "where app_id in (select id from apps where workspace_id = ?)", name.trim(), workspaceId);
        return load(workspaceId);
    }

    @Transactional(readOnly = true)
    public List<MemberDto> members(AuthPrincipal principal) {
        String workspaceId = requireWorkspace(principal);
        return jdbc.query("""
                select u.id as user_id, u.email, u.name, wm.role, u.status, wm.created_at
                from workspace_memberships wm
                join users u on u.id = wm.user_id
                where wm.workspace_id = ?
                order by wm.created_at asc
                """, (rs, i) -> new MemberDto(
                rs.getString("user_id"),
                rs.getString("email"),
                rs.getString("name"),
                rs.getString("role"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant()), workspaceId);
    }

    @Transactional
    public MemberDto addMember(AuthPrincipal principal, String email, String role) {
        String workspaceId = requireWorkspace(principal);
        requireAdmin(principal);

        String normalised = email.trim().toLowerCase(Locale.ROOT);
        List<String> userIds = jdbc.query("select id from users where lower(email) = ?", (rs, i) -> rs.getString(1),
                normalised);
        if (userIds.isEmpty()) {
            throw new NotFoundException("No account exists for " + normalised
                    + ". Ask them to sign up first, then add them to this workspace.");
        }
        String userId = userIds.get(0);

        Integer existing = jdbc.queryForObject("""
                select count(*) from workspace_memberships where workspace_id = ? and user_id = ?
                """, Integer.class, workspaceId, userId);
        if (existing != null && existing > 0) {
            throw new ConflictException("That person is already a member of this workspace");
        }

        jdbc.update("""
                insert into workspace_memberships (workspace_id, user_id, role, created_at)
                values (?, ?, ?, now())
                """, workspaceId, userId, role);

        return memberOf(workspaceId, userId);
    }

    @Transactional
    public void removeMember(AuthPrincipal principal, String userId) {
        String workspaceId = requireWorkspace(principal);
        requireAdmin(principal);
        if (userId.equals(principal.userId())) {
            throw new ConflictException("You cannot remove yourself from the workspace");
        }
        jdbc.update("delete from workspace_memberships where workspace_id = ? and user_id = ?", workspaceId, userId);
    }

    // ------------------------------------------------------------------
    // Widget settings
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public WidgetSettingsDto widgetSettings(AuthPrincipal principal) {
        requireWorkspace(principal);
        String appId = appService.requireAppId();
        return readWidgetSettings(appId);
    }

    @Transactional
    public WidgetSettingsDto updateWidgetSettings(AuthPrincipal principal, UpdateWidgetSettingsRequest request) {
        requireWorkspace(principal);
        requireEditor(principal);
        String appId = appService.requireAppId();

        WidgetSettingsDto current = readWidgetSettings(appId);
        jdbc.update("""
                update widget_settings
                set workspace_name = ?, greeting = ?, subtitle = ?, color = ?, intro_message = ?,
                    input_placeholder = ?, show_branding = ?, auto_open = ?, updated_at = now()
                where app_id = ?
                """,
                request.workspaceName() != null ? request.workspaceName() : current.workspaceName(),
                request.greeting() != null ? request.greeting() : current.greeting(),
                request.subtitle() != null ? request.subtitle() : current.subtitle(),
                request.color() != null ? request.color() : current.color(),
                request.introMessage() != null ? request.introMessage() : current.introMessage(),
                request.inputPlaceholder() != null ? request.inputPlaceholder() : current.inputPlaceholder(),
                request.showBranding() != null ? request.showBranding() : current.showBranding(),
                request.autoOpen() != null ? request.autoOpen() : current.autoOpen(),
                appId);

        if (request.aiEnabled() != null || request.autoReply() != null) {
            AppDto app = appService.requireCurrent();
            jdbc.update("""
                    update apps
                    set ai_enabled = ?, auto_reply = ?
                    where id = ?
                    """,
                    request.aiEnabled() != null ? request.aiEnabled() : app.aiEnabled(),
                    request.autoReply() != null ? request.autoReply() : app.autoReply(),
                    appId);
        }
        return readWidgetSettings(appId);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private WidgetSettingsDto readWidgetSettings(String appId) {
        AppDto app = appService.requireCurrent();
        List<WidgetSettingsDto> rows = jdbc.query("""
                select app_id, workspace_name, greeting, subtitle, color, intro_message,
                       input_placeholder, show_branding, auto_open
                from widget_settings
                where app_id = ?
                """, (rs, i) -> new WidgetSettingsDto(
                rs.getString("app_id"),
                rs.getString("workspace_name"),
                rs.getString("greeting"),
                rs.getString("subtitle"),
                rs.getString("color"),
                rs.getString("intro_message"),
                rs.getString("input_placeholder"),
                rs.getBoolean("show_branding"),
                rs.getBoolean("auto_open"),
                app.aiEnabled(),
                app.autoReply()), appId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Widget settings are missing for this workspace");
        }
        return rows.get(0);
    }

    private WorkspaceDetailDto load(String workspaceId) {
        List<WorkspaceDetailDto> rows = jdbc.query("""
                select id, name, slug, created_at from workspaces where id = ?
                """, (rs, i) -> new WorkspaceDetailDto(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("slug"),
                null,
                null,
                rs.getTimestamp("created_at").toInstant(),
                null), workspaceId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Workspace not found");
        }
        WorkspaceDetailDto base = rows.get(0);
        AppDto app = appService.findCurrent().orElse(null);
        String appId = app == null ? null : app.id();
        String role = jdbc.query("select role from workspace_memberships where workspace_id = ?",
                (rs, i) -> rs.getString(1), workspaceId).stream().findFirst().orElse(null);
        return new WorkspaceDetailDto(base.id(), base.name(), base.slug(), role, appId, base.createdAt(), app);
    }

    private MemberDto memberOf(String workspaceId, String userId) {
        List<MemberDto> rows = jdbc.query("""
                select u.id as user_id, u.email, u.name, wm.role, u.status, wm.created_at
                from workspace_memberships wm
                join users u on u.id = wm.user_id
                where wm.workspace_id = ? and wm.user_id = ?
                """, (rs, i) -> new MemberDto(
                rs.getString("user_id"),
                rs.getString("email"),
                rs.getString("name"),
                rs.getString("role"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant()), workspaceId, userId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Member not found");
        }
        return rows.get(0);
    }

    private String requireWorkspace(AuthPrincipal principal) {
        if (principal == null || !principal.hasWorkspace()) {
            throw new ForbiddenException("No workspace is selected for this session");
        }
        return principal.workspaceId();
    }

    private void requireAdmin(AuthPrincipal principal) {
        if (principal == null || !principal.isAdmin()) {
            throw new ForbiddenException("Only workspace administrators can perform this action");
        }
    }

    private void requireEditor(AuthPrincipal principal) {
        if (principal == null || principal.isAdmin()
                || AuthPrincipal.ROLE_AGENT.equalsIgnoreCase(principal.role())) {
            return;
        }
        throw new ForbiddenException("You do not have permission to change the widget settings");
    }

    /** Exposed for the widget bootstrap. */
    @Transactional(readOnly = true)
    public WidgetSettingsDto widgetSettingsByAppId(String appId) {
        return readWidgetSettings(appId);
    }
}
