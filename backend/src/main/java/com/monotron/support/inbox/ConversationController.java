package com.monotron.support.inbox;

import com.monotron.support.common.PageResponse;
import com.monotron.support.inbox.dto.AiReplyRequest;
import com.monotron.support.inbox.dto.AssignRequest;
import com.monotron.support.inbox.dto.ConversationDetailDto;
import com.monotron.support.inbox.dto.ConversationDto;
import com.monotron.support.inbox.dto.ConversationStatsDto;
import com.monotron.support.inbox.dto.ConversationSummaryDto;
import com.monotron.support.inbox.dto.EscalateRequest;
import com.monotron.support.inbox.dto.MessageDto;
import com.monotron.support.inbox.dto.ReplyRequest;
import com.monotron.support.inbox.dto.TeamRequest;
import com.monotron.support.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The agent inbox: conversations, replies and the AI agent.
 */
@RestController
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/api/conversations")
    public PageResponse<ConversationSummaryDto> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "assigneeId", required = false) String assigneeId,
            @RequestParam(name = "teamId", required = false) String teamId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return conversationService.list(principal, status, q, assigneeId, teamId, page, size);
    }

    @GetMapping("/api/conversations/stats")
    public ConversationStatsDto stats(@AuthenticationPrincipal AuthPrincipal principal) {
        return conversationService.stats(principal);
    }

    @GetMapping("/api/conversations/{id}")
    public ConversationDetailDto get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable("id") String id) {
        return conversationService.get(principal, id);
    }

    @PostMapping("/api/conversations/{id}/reply")
    public MessageDto reply(@AuthenticationPrincipal AuthPrincipal principal,
                            @PathVariable("id") String id,
                            @Valid @RequestBody ReplyRequest request) {
        return conversationService.reply(principal, id, request.body());
    }

    @PostMapping("/api/conversations/{id}/assign")
    public ConversationDto assign(@AuthenticationPrincipal AuthPrincipal principal,
                                  @PathVariable("id") String id,
                                  @RequestBody(required = false) AssignRequest request) {
        return conversationService.assign(principal, id, request == null ? null : request.assigneeId());
    }

    @PostMapping("/api/conversations/{id}/team")
    public ConversationDto routeToTeam(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable("id") String id,
                                       @RequestBody(required = false) TeamRequest request) {
        return conversationService.routeToTeam(principal, id, request == null ? null : request.teamId());
    }

    @PostMapping("/api/conversations/{id}/close")
    public ConversationDto close(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable("id") String id) {
        return conversationService.close(principal, id);
    }

    @PostMapping("/api/conversations/{id}/reopen")
    public ConversationDto reopen(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable("id") String id) {
        return conversationService.reopen(principal, id);
    }

    @PostMapping("/api/conversations/{id}/escalate")
    public ConversationDto escalate(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable("id") String id,
                                    @RequestBody(required = false) EscalateRequest request) {
        boolean escalated = request == null || request.escalated() == null || request.escalated();
        return conversationService.escalate(principal, id, escalated);
    }

    @PostMapping("/api/conversations/{id}/ai-reply")
    public MessageDto aiReply(@AuthenticationPrincipal AuthPrincipal principal,
                              @PathVariable("id") String id,
                              @RequestBody(required = false) AiReplyRequest request) {
        return conversationService.aiReply(principal, id, request == null ? null : request.question());
    }
}
