package com.monotron.support.channels;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.monotron.support.channels.dto.ChannelConnectionDto;
import com.monotron.support.channels.dto.OAuthStartDto;
import com.monotron.support.common.BadRequestException;
import com.monotron.support.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth connection lifecycle for Gmail, WhatsApp and Instagram.
 */
@RestController
@RequestMapping("/api/channel-connections")
public class ChannelConnectionController {

    private final ChannelConnectionService connectionService;
    private final ChannelOAuthService oauthService;
    private final GmailChannelSyncService gmailSyncService;

    public ChannelConnectionController(ChannelConnectionService connectionService,
                                       ChannelOAuthService oauthService,
                                       GmailChannelSyncService gmailSyncService) {
        this.connectionService = connectionService;
        this.oauthService = oauthService;
        this.gmailSyncService = gmailSyncService;
    }

    @GetMapping
    public List<ChannelConnectionDto> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return connectionService.list(principal);
    }

    @DeleteMapping("/{channel}")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable("channel") String channel) {
        connectionService.disconnect(principal, channel);
        return ResponseEntity.noContent().build();
    }

    /**
     * Imports unread Gmail messages immediately instead of waiting for the
     * background poll interval. Other channels are added when their live
     * ingestion is implemented.
     */
    @PostMapping("/{channel}/sync")
    public Map<String, Integer> sync(@AuthenticationPrincipal AuthPrincipal principal,
                                     @PathVariable("channel") String channel) {
        String normalized = ChannelConnectionService.normalizeChannel(channel);
        if (!"GMAIL".equals(normalized)) {
            throw new BadRequestException("Live sync is currently available for Gmail only.");
        }
        connectionService.requireManage(principal);
        return Map.of("processed", gmailSyncService.syncWorkspace(principal.workspaceId()));
    }

    @GetMapping("/{channel}/oauth/start")
    public OAuthStartDto start(@AuthenticationPrincipal AuthPrincipal principal,
                               @PathVariable("channel") String channel) {
        return oauthService.start(principal, channel);
    }

    /**
     * Public provider callback. The signed state identifies the workspace and
     * channel; the request itself carries no bearer token.
     */
    @GetMapping("/{channel}/oauth/callback")
    public ResponseEntity<Void> callback(@PathVariable("channel") String channel,
                                         @RequestParam(value = "state", required = false) String state,
                                         @RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "error", required = false) String error) {
        String target;
        try {
            target = oauthService.handleCallback(channel, state, code, error);
        } catch (RuntimeException ex) {
            target = oauthService.failureRedirect(ex.getMessage());
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .build();
    }
}
