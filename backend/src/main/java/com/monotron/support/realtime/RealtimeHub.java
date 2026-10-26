package com.monotron.support.realtime;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Keeps the open server-sent-event connections for the live inbox and widget.
 *
 * <p>There are two independent channels because they have different audiences:
 *
 * <ul>
 *   <li><strong>workspace</strong> — every authenticated agent in a tenant, used
 *       by the inbox list and thread;</li>
 *   <li><strong>conversation</strong> — a single visitor watching their widget,
 *       used to receive agent replies.</li>
 * </ul>
 *
 * <p>Events only carry a type and conversation id. Clients re-fetch the normal
 * REST representation, which keeps row-level security, unread counts and all
 * existing formatting in one place. Heartbeats keep proxies from closing idle
 * connections and also detect dead clients.
 */
@Component
public class RealtimeHub {

    private static final Logger log = LoggerFactory.getLogger(RealtimeHub.class);

    private static final long HEARTBEAT_SECONDS = 20L;

    private final ObjectMapper mapper;
    private final Map<String, Set<SseEmitter>> workspaceEmitters = new ConcurrentHashMap<>();
    private final Map<String, Set<SseEmitter>> conversationEmitters = new ConcurrentHashMap<>();

    /**
     * One serial worker for every send: SSE frames from concurrent requests are
     * never interleaved, and a slow client cannot hold up the request thread
     * that committed the transaction.
     */
    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "monotron-realtime");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "monotron-realtime-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public RealtimeHub(ObjectMapper mapper) {
        this.mapper = mapper;
        this.heartbeat.scheduleAtFixedRate(
                () -> dispatcher.execute(this::pingAll),
                HEARTBEAT_SECONDS,
                HEARTBEAT_SECONDS,
                TimeUnit.SECONDS);
    }

    /**
     * Adds standard headers for a long-lived SSE response.
     */
    public static void prepare(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
    }

    /**
     * Opens the authenticated inbox stream for one workspace.
     */
    public SseEmitter subscribeWorkspace(String workspaceId) {
        return subscribe(workspaceEmitters, workspaceId);
    }

    /**
     * Opens the visitor stream for one conversation.
     */
    public SseEmitter subscribeConversation(String conversationId) {
        return subscribe(conversationEmitters, conversationId);
    }

    public void publishWorkspace(RealtimeEvent event) {
        enqueue(workspaceEmitters, event.workspaceId(), event);
    }

    public void publishConversation(RealtimeEvent event) {
        enqueue(conversationEmitters, event.conversationId(), event);
    }

    // ------------------------------------------------------------------
    // Subscriptions
    // ------------------------------------------------------------------

    private SseEmitter subscribe(Map<String, Set<SseEmitter>> registry, String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("A realtime channel needs an id");
        }

        SseEmitter emitter = new SseEmitter(0L);
        Runnable remove = () -> remove(registry, key, emitter);

        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            remove.run();
            completeQuietly(emitter);
        });
        emitter.onError(exception -> remove.run());

        registry.compute(key, (ignored, current) -> {
            Set<SseEmitter> set = current == null ? ConcurrentHashMap.newKeySet() : current;
            set.add(emitter);
            return set;
        });

        if (!trySend(emitter, "{\"type\":\"ready\"}")) {
            remove.run();
            completeQuietly(emitter);
        }
        return emitter;
    }

    private void remove(Map<String, Set<SseEmitter>> registry, String key, SseEmitter emitter) {
        registry.computeIfPresent(key, (ignored, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }

    // ------------------------------------------------------------------
    // Delivery
    // ------------------------------------------------------------------

    private void enqueue(Map<String, Set<SseEmitter>> registry, String key, RealtimeEvent event) {
        if (key == null || key.isBlank()) {
            return;
        }
        String payload = wire(event);
        if (payload == null) {
            return;
        }
        dispatcher.execute(() -> broadcast(registry, key, payload));
    }

    private String wire(RealtimeEvent event) {
        if (event.conversationId() == null || event.conversationId().isBlank()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(Map.of(
                    "type", event.type(),
                    "conversationId", event.conversationId(),
                    "at", Instant.now().toString()));
        } catch (JsonProcessingException ex) {
            log.warn("Unable to serialise realtime event {}", event.type(), ex);
            return null;
        }
    }

    private void broadcast(Map<String, Set<SseEmitter>> registry, String key, String payload) {
        Set<SseEmitter> set = registry.get(key);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : new ArrayList<>(set)) {
            if (!trySend(emitter, payload)) {
                remove(registry, key, emitter);
                completeQuietly(emitter);
            }
        }
    }

    private boolean trySend(SseEmitter emitter, String payload) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().data(payload));
            }
            return true;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    private void pingAll() {
        ping(workspaceEmitters);
        ping(conversationEmitters);
    }

    private void ping(Map<String, Set<SseEmitter>> registry) {
        for (Map.Entry<String, Set<SseEmitter>> entry : registry.entrySet()) {
            for (SseEmitter emitter : new ArrayList<>(entry.getValue())) {
                try {
                    synchronized (emitter) {
                        emitter.send(SseEmitter.event().comment("keep-alive"));
                    }
                } catch (IOException | RuntimeException ex) {
                    remove(registry, entry.getKey(), emitter);
                    completeQuietly(emitter);
                }
            }
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ex) {
            // The container already closed this response.
        }
    }

    @PreDestroy
    public void shutdown() {
        heartbeat.shutdownNow();
        dispatcher.shutdownNow();
        closeAll(workspaceEmitters);
        closeAll(conversationEmitters);
    }

    private void closeAll(Map<String, Set<SseEmitter>> registry) {
        for (Set<SseEmitter> set : registry.values()) {
            for (SseEmitter emitter : new ArrayList<>(set)) {
                completeQuietly(emitter);
            }
        }
        registry.clear();
    }
}
