package com.monotron.support.tenancy;

import java.util.List;

import com.monotron.support.security.AuthPrincipal;
import com.monotron.support.tenancy.dto.CreateTeamRequest;
import com.monotron.support.tenancy.dto.TeamDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent teams.
 */
@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public TeamsResponse list(@AuthenticationPrincipal AuthPrincipal principal) {
        return new TeamsResponse(teamService.list(principal));
    }

    @PostMapping
    public TeamDto create(@AuthenticationPrincipal AuthPrincipal principal,
                          @Valid @RequestBody CreateTeamRequest request) {
        return teamService.create(principal, request.name(), request.description());
    }

    @PostMapping("/{teamId}/members")
    public TeamDto addMember(@AuthenticationPrincipal AuthPrincipal principal,
                             @PathVariable String teamId,
                             @RequestBody TeamMemberRequest request) {
        return teamService.addMember(principal, teamId, request.userId());
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public TeamDto removeMember(@AuthenticationPrincipal AuthPrincipal principal,
                                @PathVariable String teamId,
                                @PathVariable String userId) {
        return teamService.removeMember(principal, teamId, userId);
    }

    @DeleteMapping("/{teamId}")
    public org.springframework.http.ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal principal,
                                                                @PathVariable String teamId) {
        teamService.delete(principal, teamId);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    public record TeamMemberRequest(String userId) {
    }

    public record TeamsResponse(List<TeamDto> items) {
    }
}
