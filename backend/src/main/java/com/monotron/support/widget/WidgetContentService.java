package com.monotron.support.widget;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.monotron.support.common.BadRequestException;
import com.monotron.support.common.ForbiddenException;
import com.monotron.support.common.Ids;
import com.monotron.support.common.NotFoundException;
import com.monotron.support.security.AuthPrincipal;
import com.monotron.support.tenancy.AppService;
import com.monotron.support.widget.dto.WidgetContentDto;
import com.monotron.support.widget.dto.WidgetContentRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Help-center content that is authored in settings and published in the widget.
 *
 * <p>The public widget only ever sees published rows. Workspace members can see
 * drafts in settings, while agents and administrators can create and edit them.
 * RLS remains the final tenant boundary for both paths.
 */
@Service
public class WidgetContentService {

    private static final Set<String> KINDS = Set.of("BLOG", "FAQ", "ANNOUNCEMENT");

    private static final RowMapper<WidgetContentDto> MAPPER = (rs, i) -> new WidgetContentDto(
            rs.getString("id"),
            rs.getString("kind"),
            rs.getString("title"),
            rs.getString("summary"),
            rs.getString("body"),
            rs.getBoolean("published"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private static final String COLUMNS = """
            select id, kind, title, summary, body, published, created_at, updated_at
            from widget_content
            """;

    private final JdbcTemplate jdbc;
    private final AppService appService;

    public WidgetContentService(JdbcTemplate jdbc, AppService appService) {
        this.jdbc = jdbc;
        this.appService = appService;
    }

    /**
     * Published content for the public messenger. The caller has already bound
     * the tenant from the app id, so the query only needs the app filter.
     */
    @Transactional(readOnly = true)
    public List<WidgetContentDto> published(String appId) {
        return jdbc.query(COLUMNS + """
                where app_id = ? and published = true
                order by case kind
                    when 'ANNOUNCEMENT' then 0
                    when 'FAQ' then 1
                    else 2
                end, created_at desc
                """, MAPPER, appId);
    }

    @Transactional(readOnly = true)
    public List<WidgetContentDto> list(AuthPrincipal principal) {
        requireWorkspace(principal);
        String appId = appService.requireAppId();
        return jdbc.query(COLUMNS + """
                where app_id = ?
                order by updated_at desc
                """, MAPPER, appId);
    }

    @Transactional
    public WidgetContentDto create(AuthPrincipal principal, WidgetContentRequest request) {
        requireEditor(principal);
        String appId = appService.requireAppId();
        String id = Ids.uuid();
        jdbc.update("""
                insert into widget_content
                    (id, app_id, kind, title, summary, body, published, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                id,
                appId,
                normalizeKind(request.kind()),
                request.title().trim(),
                summary(request.summary()),
                request.body().trim(),
                request.published() == null || request.published());
        return find(appId, id);
    }

    @Transactional
    public WidgetContentDto update(AuthPrincipal principal, String id, WidgetContentRequest request) {
        requireEditor(principal);
        String appId = appService.requireAppId();
        WidgetContentDto current = find(appId, id);

        jdbc.update("""
                update widget_content
                set kind = ?, title = ?, summary = ?, body = ?, published = ?, updated_at = now()
                where id = ? and app_id = ?
                """,
                normalizeKind(request.kind()),
                request.title().trim(),
                request.summary() != null ? request.summary().trim() : current.summary(),
                request.body().trim(),
                request.published() != null ? request.published() : current.published(),
                id,
                appId);
        return find(appId, id);
    }

    @Transactional
    public void delete(AuthPrincipal principal, String id) {
        requireEditor(principal);
        String appId = appService.requireAppId();
        int deleted = jdbc.update("delete from widget_content where id = ? and app_id = ?", id, appId);
        if (deleted == 0) {
            throw new NotFoundException("Help-center content not found");
        }
    }

    private WidgetContentDto find(String appId, String id) {
        List<WidgetContentDto> rows = jdbc.query(COLUMNS + """
                where id = ? and app_id = ?
                """, MAPPER, id, appId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Help-center content not found");
        }
        return rows.get(0);
    }

    private String normalizeKind(String kind) {
        String normalised = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(normalised)) {
            throw new BadRequestException("Content type must be BLOG, FAQ or ANNOUNCEMENT");
        }
        return normalised;
    }

    private String summary(String summary) {
        return summary == null ? "" : summary.trim();
    }

    private String requireWorkspace(AuthPrincipal principal) {
        if (principal == null || !principal.hasWorkspace()) {
            throw new ForbiddenException("No workspace is selected for this session");
        }
        return principal.workspaceId();
    }

    private void requireEditor(AuthPrincipal principal) {
        requireWorkspace(principal);
        if (principal.isAdmin() || AuthPrincipal.ROLE_AGENT.equalsIgnoreCase(principal.role())) {
            return;
        }
        throw new ForbiddenException("You do not have permission to manage help-center content");
    }
}
