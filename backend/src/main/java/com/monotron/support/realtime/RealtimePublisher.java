package com.monotron.support.realtime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes small conversation-change signals for the live inbox and widget.
 */
@Component
public class RealtimePublisher {

    private final ApplicationEventPublisher events;

    public RealtimePublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    /**
     * A message (visitor, agent or AI) was added to the conversation.
     *
     * @param notifyWidget whether the visitor's open widget should refresh too
     */
    public void messageCreated(String workspaceId, String conversationId, boolean notifyWidget) {
        events.publishEvent(new RealtimeEvent(
                RealtimeEvent.MESSAGE_CREATED, workspaceId, conversationId, notifyWidget));
    }

    /**
     * Conversation metadata changed (status, assignee, team or escalation).
     */
    public void conversationUpdated(String workspaceId, String conversationId) {
        events.publishEvent(new RealtimeEvent(
                RealtimeEvent.CONVERSATION_UPDATED, workspaceId, conversationId, false));
    }
}
