package com.monotron.support.tenancy;

import java.util.List;

import com.monotron.support.common.ConflictException;
import com.monotron.support.common.ForbiddenException;
import com.monotron.support.common.Ids;
import com.monotron.support.common.NotFoundException;
import com.monotron.support.security.AuthPrincipal;
import com.monotron.support.tenancy.dto.TeamDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent teams used to route conversations.
 */
@Service
public class TeamService {

    private final JdbcTemplate jdbc;

    public TeamService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<TeamDto> list(AuthPrincipal principal) {
        String workspaceId = requireWorkspace(principal);
        List<TeamDto> teams = jdbc.query("""
                select id, name, description, created_at from teams where workspace_id = ? order by name asc
                """, (rs, i) -> new TeamDto(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getTimestamp("created_at").toInstant(),
                List.of()), workspaceId);
        return teams.stream().map(team -> withMembers(team, workspaceId)).toList();
    }

    @Transactional
    public TeamDto create(AuthPrincipal principal, String name, String description) {
        String workspaceId = requireWorkspace(principal);
        requireEditor(principal);
        String teamId = Ids.uuid();
        jdbc.update("""
                insert into teams (id, name, description, created_at, updated_at, workspace_id)
                values (?, ?, ?, now(), now(), ?)
                """, teamId, name.trim(), description == null || description.isBlank() ? null : description.trim(),
                workspaceId);
        return find(principal, teamId);
    }

    @Transactional
    public TeamDto addMember(AuthPrincipal principal, String teamId, String userId) {
        String workspaceId = requireWorkspace(principal);
        requireEditor(principal);
        ensureTeamExists(workspaceId, teamId);
        ensureUserIsMember(workspaceId, userId);

        Integer existing = jdbc.queryForObject("""
                select count(*) from team_members where team_id = ? and user_id = ?
                """, Integer.class, teamId, userId);
        if (existing != null && existing > 0) {
            throw new ConflictException("That person is already in this team");
        }
        jdbc.update("insert into team_members (team_id, user_id) values (?, ?)", teamId, userId);
        return find(principal, teamId);
    }

    @Transactional
    public TeamDto removeMember(AuthPrincipal principal, String teamId, String userId) {
        String workspaceId = requireWorkspace(principal);
        requireEditor(principal);
        ensureTeamExists(workspaceId, teamId);
        jdbc.update("delete from team_members where team_id = ? and user_id = ?", teamId, userId);
        return find(principal, teamId);
    }

    @Transactional
    public void delete(AuthPrincipal principal, String teamId) {
        String workspaceId = requireWorkspace(principal);
        requireAdmin(principal);
        jdbc.update("delete from teams where id = ? and workspace_id = ?", teamId, workspaceId);
    }

    private TeamDto find(AuthPrincipal principal, String teamId) {
        String workspaceId = requireWorkspace(principal);
        List<TeamDto> rows = jdbc.query("""
                select id, name, description, created_at from teams where id = ? and workspace_id = ?
                """, (rs, i) -> new TeamDto(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getTimestamp("created_at").toInstant(),
                List.of()), teamId, workspaceId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Team not found");
        }
        return withMembers(rows.get(0), workspaceId);
    }

    private TeamDto withMembers(TeamDto team, String workspaceId) {
        List<String> memberIds = jdbc.query("""
                select tm.user_id
                from team_members tm
                join workspace_memberships wm on wm.user_id = tm.user_id and wm.workspace_id = ?
                where tm.team_id = ?
                order by tm.user_id
                """, (rs, i) -> rs.getString(1), workspaceId, team.id());
        return new TeamDto(team.id(), team.name(), team.description(), team.createdAt(), memberIds);
    }

    private void ensureTeamExists(String workspaceId, String teamId) {
        Integer count = jdbc.queryForObject("select count(*) from teams where id = ? and workspace_id = ?",
                Integer.class, teamId, workspaceId);
        if (count == null || count == 0) {
            throw new NotFoundException("Team not found");
        }
    }

    private void ensureUserIsMember(String workspaceId, String userId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from workspace_memberships where workspace_id = ? and user_id = ?
                """, Integer.class, workspaceId, userId);
        if (count == null || count == 0) {
            throw new NotFoundException("That person is not a member of this workspace");
        }
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
        throw new ForbiddenException("You do not have permission to change teams");
    }
}
