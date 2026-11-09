package com.monotron.support.channels;

import java.util.List;

import com.monotron.support.channels.dto.ChannelAutomationDto;
import com.monotron.support.channels.dto.UpdateChannelAutomationRequest;
import com.monotron.support.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Automation settings for Gmail, WhatsApp and Instagram conversations.
 */
@RestController
@RequestMapping("/api/channel-automations")
public class ChannelAutomationController {

    private final ChannelAutomationService channelAutomationService;

    public ChannelAutomationController(ChannelAutomationService channelAutomationService) {
        this.channelAutomationService = channelAutomationService;
    }

    @GetMapping
    public List<ChannelAutomationDto> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return channelAutomationService.list(principal);
    }

    @PutMapping("/{channel}")
    public ChannelAutomationDto update(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable("channel") String channel,
                                       @Valid @RequestBody UpdateChannelAutomationRequest request) {
        return channelAutomationService.update(principal, channel, request);
    }
}
