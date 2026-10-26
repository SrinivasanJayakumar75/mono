package com.monotron.support.realtime;

/**
 * An internal signal that a conversation changed.
 *
 * <p>The event is deliberately small: it tells connected clients to re-read the
 * authoritative REST payload instead of duplicating conversation data in the
 * push channel. It is published inside the service transaction and dispatched
 * by {@link RealtimeBridge} only after that transaction commits, so a client
 * can never be told to fetch data that is not visible yet.
 *
 * @param type           event name, e.g. {@code message.created}
 * @param workspaceId    tenant that owns the conversation
 * @param conversationId conversation that changed
 * @param notifyWidget   whether the anonymous visitor stream should hear it
 */
public record RealtimeEvent(
        String type,
        String workspaceId,
        String conversationId,
        boolean notifyWidget) {

    public static final String MESSAGE_CREATED = "message.created";
    public static final String CONVERSATION_UPDATED = "conversation.updated";
}
