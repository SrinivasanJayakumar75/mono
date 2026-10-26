package com.monotron.support.tenancy;

import java.util.List;

import com.monotron.support.security.AuthPrincipal;
import com.monotron.support.tenancy.dto.AddMemberRequest;
import com.monotron.support.tenancy.dto.MemberDto;
import com.monotron.support.tenancy.dto.UpdateWorkspaceRequest;
import com.monotron.support.tenancy.dto.WorkspaceDetailDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workspace profile and membership.
 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public WorkspaceDetailDto get(@AuthenticationPrincipal AuthPrincipal principal) {
        return workspaceService.get(principal);
    }

    @PatchMapping
    public WorkspaceDetailDto update(@AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody UpdateWorkspaceRequest request) {
        return workspaceService.update(principal, request.name());
    }

    @GetMapping("/members")
    public MembersResponse members(@AuthenticationPrincipal AuthPrincipal principal) {
        List<MemberDto> members = workspaceService.members(principal);
        return new MembersResponse(members);
    }

    @PostMapping("/members")
    public MemberDto addMember(@AuthenticationPrincipal AuthPrincipal principal,
                               @Valid @RequestBody AddMemberRequest request) {
        return workspaceService.addMember(principal, request.email(), request.role());
    }

    @DeleteMapping("/members/{userId}")
    public org.springframework.http.ResponseEntity<Void> removeMember(@AuthenticationPrincipal AuthPrincipal principal,
                                                                     @PathVariable String userId) {
        workspaceService.removeMember(principal, userId);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    /** Envelope used by the member list endpoint. */
    public record MembersResponse(List<MemberDto> items) {
    }
}
