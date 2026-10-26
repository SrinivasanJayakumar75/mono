"use client";

import clsx from "clsx";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import {
  ConversationList,
  type ConversationFilters,
} from "./conversation-list";
import { ConversationThread } from "./conversation-thread";
import { InboxIcon } from "./icons";
import { ErrorBanner } from "./ui";
import { apiFetch } from "@/lib/api";
import {
  useRealtimeStream,
  type RealtimeNotification,
  type RealtimeStatus,
} from "@/lib/realtime";
import type { Conversation, ConversationStats, Page } from "@/lib/types";

const PAGE_SIZE = 25;

export function InboxView({ conversationId }: { conversationId?: string }) {
  const router = useRouter();

  const [filters, setFilters] = useState<ConversationFilters>({ q: "", status: "ALL" });
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [page, setPage] = useState(0);

  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [stats, setStats] = useState<ConversationStats | null>(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [liveStatus, setLiveStatus] = useState<RealtimeStatus>("connecting");
  const [liveUpdate, setLiveUpdate] = useState<{
    seq: number;
    conversationId: string | null;
  }>({ seq: 0, conversationId: null });

  const loadSeq = useRef(0);
  const statsSeq = useRef(0);
  const refreshTimer = useRef<number | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(filters.q.trim());
      setPage(0);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [filters.q]);

  const load = useCallback(
    async (silent = false) => {
      const seq = ++loadSeq.current;
      if (!silent) {
        setLoading(true);
        setError(null);
      }
      try {
        const result = await apiFetch<Page<Conversation>>("/conversations", {
          params: {
            status: filters.status === "ALL" ? undefined : filters.status,
            q: debouncedQuery || undefined,
            page,
            size: PAGE_SIZE,
          },
        });
        if (seq !== loadSeq.current) return;
        setConversations(result.items ?? []);
        setTotal(result.total ?? 0);
        setTotalPages(result.totalPages ?? 0);
        setError(null);
      } catch (err) {
        if (seq !== loadSeq.current || silent) return;
        setError(err instanceof Error ? err.message : "Could not load conversations.");
      } finally {
        if (!silent) setLoading(false);
      }
    },
    [filters.status, debouncedQuery, page],
  );

  const loadStats = useCallback(async () => {
    const seq = ++statsSeq.current;
    try {
      const result = await apiFetch<ConversationStats>("/conversations/stats");
      if (seq === statsSeq.current) setStats(result);
    } catch {
      /* stats are non-critical */
    }
  }, []);

  const scheduleLiveRefresh = useCallback(() => {
    if (refreshTimer.current !== null) window.clearTimeout(refreshTimer.current);
    refreshTimer.current = window.setTimeout(() => {
      refreshTimer.current = null;
      void load(true);
      void loadStats();
    }, 120);
  }, [load, loadStats]);

  const handleRealtimeEvent = useCallback(
    (event: RealtimeNotification) => {
      if (event.type === "ready") return;
      setLiveUpdate((current) => ({
        seq: current.seq + 1,
        conversationId: event.conversationId ?? null,
      }));
      scheduleLiveRefresh();
    },
    [scheduleLiveRefresh],
  );

  const handleRealtimeOpen = useCallback(() => {
    // A reconnect may have missed events, so re-read both views.
    setLiveUpdate((current) => ({ seq: current.seq + 1, conversationId: null }));
    void load(true);
    void loadStats();
  }, [load, loadStats]);

  useRealtimeStream({
    onEvent: handleRealtimeEvent,
    onOpen: handleRealtimeOpen,
    onStatus: (status) => setLiveStatus(status),
  });

  useEffect(
    () => () => {
      if (refreshTimer.current !== null) window.clearTimeout(refreshTimer.current);
    },
    [],
  );

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void loadStats();
  }, [loadStats]);

  const onSelect = useCallback(
    (id: string) => {
      router.push(`/inbox/${id}`);
    },
    [router],
  );

  const list = useMemo(
    () => (
      <ConversationList
        conversations={conversations}
        loading={loading}
        page={page}
        totalPages={totalPages}
        total={total}
        selectedId={conversationId ?? null}
        onSelect={onSelect}
        onPageChange={setPage}
        onFiltersChange={(next) => {
          setFilters(next);
          setPage(0);
        }}
        filters={filters}
        stats={stats}
        liveStatus={liveStatus}
        onRefresh={() => {
          void load();
          void loadStats();
        }}
      />
    ),
    [
      conversations,
      loading,
      page,
      totalPages,
      total,
      conversationId,
      onSelect,
      filters,
      stats,
      liveStatus,
      load,
      loadStats,
    ],
  );

  return (
    <div className="flex h-full min-h-0 flex-col bg-white">
      {error ? (
        <div className="shrink-0 border-b border-surface-200 px-3 py-2">
          <ErrorBanner message={error} onRetry={() => void load()} />
        </div>
      ) : null}

      <div className="grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-[minmax(19rem,22rem)_minmax(0,1fr)]">
        <aside
          className={clsx(
            "min-h-0 flex-col border-surface-200 bg-white lg:flex lg:border-r",
            conversationId ? "hidden" : "flex",
          )}
        >
          {list}
        </aside>

        <section
          className={clsx(
            "min-h-0 flex-col bg-white",
            conversationId ? "flex" : "hidden lg:flex",
          )}
        >
          {conversationId ? (
            <ConversationThread
              key={conversationId}
              conversationId={conversationId}
              liveUpdate={liveUpdate}
              onBack={() => router.push("/inbox")}
              onChanged={() => {
                void load(true);
                void loadStats();
              }}
            />
          ) : (
            <div className="flex h-full flex-col items-center justify-center gap-3 bg-surface-50/60 p-8 text-center">
              <span className="flex h-11 w-11 items-center justify-center rounded-full border border-surface-200 bg-white text-surface-400">
                <InboxIcon className="h-5 w-5" />
              </span>
              <div className="space-y-1">
                <p className="text-[13px] font-medium text-surface-800">
                  No conversation selected
                </p>
                <p className="mx-auto max-w-xs text-[12.5px] leading-relaxed text-surface-500">
                  Choose a thread to read the history, reply, or draft a cited answer with
                  Monotron AI.
                </p>
              </div>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
