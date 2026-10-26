package com.monotron.support.widget;

import java.util.List;

import com.monotron.support.realtime.RealtimeHub;
import com.monotron.support.widget.dto.WidgetConfigDto;
import com.monotron.support.widget.dto.WidgetContentDto;
import com.monotron.support.widget.dto.WidgetConversationDto;
import com.monotron.support.widget.dto.WidgetSendRequest;
import com.monotron.support.widget.dto.WidgetSendResponseDto;
import com.monotron.support.widget.dto.WidgetSessionRequest;
import com.monotron.support.widget.dto.WidgetSessionResponseDto;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The public messenger widget.
 *
 * <p>These endpoints are reachable without a token — they are the API used by
 * the embeddable widget on a customer's website.
 */
@RestController
public class WidgetController {

    private final WidgetService widgetService;
    private final WidgetContentService contentService;
    private final RealtimeHub realtimeHub;

    public WidgetController(WidgetService widgetService, WidgetContentService contentService,
                            RealtimeHub realtimeHub) {
        this.widgetService = widgetService;
        this.contentService = contentService;
        this.realtimeHub = realtimeHub;
    }

    @GetMapping("/api/widget/{appId}/config")
    public WidgetConfigDto config(@PathVariable String appId) {
        return widgetService.config(appId);
    }

    /**
     * Published blogs, FAQs and announcements for the widget help screen.
     */
    @GetMapping("/api/widget/{appId}/content")
    public List<WidgetContentDto> content(@PathVariable String appId) {
        return contentService.published(appId);
    }

    @PostMapping("/api/widget/{appId}/session")
    public WidgetSessionResponseDto session(@PathVariable String appId,
                                            @Valid @RequestBody(required = false) WidgetSessionRequest request) {
        WidgetSessionRequest payload = request == null
                ? new WidgetSessionRequest(null, null, null)
                : request;
        return widgetService.session(appId, payload);
    }

    @PostMapping("/api/widget/{appId}/messages")
    public WidgetSendResponseDto send(@PathVariable String appId,
                                      @Valid @RequestBody WidgetSendRequest request) {
        return widgetService.send(appId, request);
    }

    /**
     * Returns the full conversation so the widget can fill any gap after a
     * reconnect. Agent replies arrive as a live signal on the SSE endpoint.
     */
    @GetMapping("/api/widget/{appId}/conversations/{conversationId}")
    public WidgetConversationDto conversation(@PathVariable String appId,
                                              @PathVariable String conversationId,
                                              @RequestParam String visitorId) {
        return widgetService.conversationForVisitor(appId, conversationId, visitorId);
    }

    /**
     * Live agent replies for one visitor conversation.
     */
    @GetMapping("/api/widget/{appId}/conversations/{conversationId}/events")
    public SseEmitter events(@PathVariable String appId,
                             @PathVariable String conversationId,
                             @RequestParam String visitorId,
                             HttpServletResponse response) {
        widgetService.assertVisitorConversation(appId, conversationId, visitorId);
        RealtimeHub.prepare(response);
        return realtimeHub.subscribeConversation(conversationId);
    }
}
