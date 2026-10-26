package com.monotron.support.realtime;

import com.monotron.support.common.ForbiddenException;
import com.monotron.support.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The authenticated live-update stream used by the inbox.
 *
 * <p>It is scoped to the workspace in the JWT. Sending a message is still a
 * normal REST call; this endpoint only carries notifications.
 */
@RestController
public class RealtimeController {

    private final RealtimeHub hub;

    public RealtimeController(RealtimeHub hub) {
        this.hub = hub;
    }

    @GetMapping("/api/events")
    public SseEmitter events(
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletResponse response) {
        if (principal == null || !principal.hasWorkspace()) {
            throw new ForbiddenException("No workspace is selected for this session");
        }
        RealtimeHub.prepare(response);
        return hub.subscribeWorkspace(principal.workspaceId());
    }
}
