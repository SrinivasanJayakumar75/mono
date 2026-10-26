package com.monotron.support.security;

/**
 * Holds the tenant that the current thread is operating on.
 *
 * <p>This is the in-process mirror of the PostgreSQL session variable
 * {@code app.workspace_id}. It is set for the duration of a request (or of an
 * asynchronous job) and cleared afterwards.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            CURRENT.remove();
        } else {
            CURRENT.set(workspaceId);
        }
    }

    public static String get() {
        return CURRENT.get();
    }

    public static String require() {
        String workspaceId = CURRENT.get();
        if (workspaceId == null) {
            throw new IllegalStateException("No tenant bound to the current thread");
        }
        return workspaceId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
