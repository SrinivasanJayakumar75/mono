package com.monotron.support.realtime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Delivers conversation signals only after the database transaction commits.
 *
 * <p>This ordering is essential: a client that receives an event immediately
 * fetches the conversation, so the new row must already be visible to that
 * request. If no transaction is active, the event is delivered immediately.
 */
@Component
public class RealtimeBridge {

    private final RealtimeHub hub;

    public RealtimeBridge(RealtimeHub hub) {
        this.hub = hub;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onConversationChanged(RealtimeEvent event) {
        hub.publishWorkspace(event);
        if (event.notifyWidget()) {
            hub.publishConversation(event);
        }
    }
}
