"use client";

import { useEffect, useRef } from "react";

import { apiUrl, getAccessToken, refreshAccessToken } from "./api";

export type RealtimeStatus = "connecting" | "live" | "offline";

export interface RealtimeNotification {
  type: string;
  conversationId?: string;
  at?: string;
}

interface ConnectRealtimeOptions {
  path: string;
  signal: AbortSignal;
  onEvent: (event: RealtimeNotification) => void;
  onOpen?: () => void;
  onStatus?: (status: RealtimeStatus) => void;
}

const MAX_BACKOFF_MS = 30_000;

/**
 * Consumes a server-sent-event stream using fetch, so the JWT can travel in the
 * Authorization header instead of the URL. It reconnects with capped
 * exponential backoff and refreshes the access token once on a 401.
 */
export async function connectRealtime({
  path,
  signal,
  onEvent,
  onOpen,
  onStatus,
}: ConnectRealtimeOptions): Promise<void> {
  let attempt = 0;

  while (!signal.aborted) {
    onStatus?.("connecting");

    const token = getAccessToken();
    if (!token) {
      onStatus?.("offline");
      return;
    }

    let response: Response;
    try {
      response = await fetch(apiUrl(path), {
        method: "GET",
        headers: {
          Accept: "text/event-stream",
          Authorization: `Bearer ${token}`,
        },
        cache: "no-store",
        signal,
      });
    } catch {
      if (signal.aborted) return;
      onStatus?.("offline");
      await wait(retryDelay(attempt++), signal);
      continue;
    }

    if (response.status === 401) {
      await discard(response);
      const refreshed = await refreshAccessToken();
      if (signal.aborted) return;
      if (!refreshed) {
        onStatus?.("offline");
        return;
      }
      attempt = 0;
      continue;
    }

    const contentType = response.headers.get("content-type") ?? "";
    if (!response.ok || !response.body || !contentType.includes("text/event-stream")) {
      await discard(response);
      onStatus?.("offline");
      await wait(retryDelay(attempt++), signal);
      continue;
    }

    attempt = 0;
    onStatus?.("live");
    onOpen?.();

    try {
      await readSse(response.body, onEvent, signal);
    } catch {
      if (signal.aborted) return;
    }

    if (signal.aborted) return;
    onStatus?.("offline");
    await wait(retryDelay(attempt++), signal);
  }
}

/**
 * One authenticated inbox stream for the lifetime of a component.
 *
 * <p>Callbacks are kept in refs so a filter or page change does not tear down
 * and rebuild the connection.
 */
export function useRealtimeStream({
  enabled = true,
  onEvent,
  onOpen,
  onStatus,
}: {
  enabled?: boolean;
  onEvent: (event: RealtimeNotification) => void;
  onOpen?: () => void;
  onStatus?: (status: RealtimeStatus) => void;
}): void {
  const eventRef = useRef(onEvent);
  const openRef = useRef(onOpen);
  const statusRef = useRef(onStatus);

  useEffect(() => {
    eventRef.current = onEvent;
  }, [onEvent]);

  useEffect(() => {
    openRef.current = onOpen;
  }, [onOpen]);

  useEffect(() => {
    statusRef.current = onStatus;
  }, [onStatus]);

  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    void connectRealtime({
      path: "/events",
      signal: controller.signal,
      onEvent: (event) => eventRef.current(event),
      onOpen: () => openRef.current?.(),
      onStatus: (status) => statusRef.current?.(status),
    });
    return () => controller.abort();
  }, [enabled]);
}

// ---------------------------------------------------------------------------
// SSE parsing
// ---------------------------------------------------------------------------

async function readSse(
  body: ReadableStream<Uint8Array>,
  onEvent: (event: RealtimeNotification) => void,
  signal: AbortSignal,
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  try {
    while (!signal.aborted) {
      const { value, done } = await reader.read();
      if (done) return;
      buffer += decoder.decode(value, { stream: true });
      buffer = buffer.replace(/\r\n/g, "\n");

      let boundary: number;
      while ((boundary = buffer.indexOf("\n\n")) >= 0) {
        const frame = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        const payload = parseData(frame);
        if (!payload) continue;

        try {
          const event = JSON.parse(payload) as RealtimeNotification;
          if (event?.type === "ready" || !event?.type) continue;
          onEvent(event);
        } catch {
          /* Ignore malformed frames; the next event or reconnect will resync. */
        }
      }
    }
  } finally {
    reader.releaseLock();
  }
}

function parseData(frame: string): string | null {
  const lines: string[] = [];
  for (const line of frame.split("\n")) {
    if (!line || line.startsWith(":")) continue;
    if (line.startsWith("data:")) {
      lines.push(line.slice(5).replace(/^ /, ""));
    }
  }
  return lines.length > 0 ? lines.join("\n") : null;
}

function retryDelay(attempt: number): number {
  const base = Math.min(MAX_BACKOFF_MS, 1_000 * 2 ** Math.min(attempt, 5));
  return base + Math.floor(Math.random() * 251);
}

function wait(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    if (signal.aborted) {
      resolve();
      return;
    }
    const onAbort = () => {
      window.clearTimeout(timer);
      resolve();
    };
    const timer = window.setTimeout(() => {
      signal.removeEventListener("abort", onAbort);
      resolve();
    }, milliseconds);
    signal.addEventListener("abort", onAbort, { once: true });
  });
}

async function discard(response: Response): Promise<void> {
  try {
    await response.body?.cancel();
  } catch {
    /* The body may already be closed. */
  }
}
