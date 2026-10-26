"use client";

import clsx from "clsx";
import { useMemo } from "react";

import { AlertIcon, BotIcon, InboxIcon, RefreshIcon, SearchIcon, UserIcon } from "./icons";
import { Button, Skeleton } from "./ui";
import { avatarColor, formatNumber, formatRelative, initials, truncate } from "@/lib/format";
import type { RealtimeStatus } from "@/lib/realtime";
import type { Conversation, ConversationStats } from "@/lib/types";

export interface ConversationFilters {
  q: string;
  status: "ALL" | "OPEN" | "CLOSED";
}

export function ConversationList({
  conversations,
  loading,
  page,
  totalPages,
  total,
  selectedId,
  onSelect,
  onPageChange,
  onFiltersChange,
  filters,
  stats,
  liveStatus,
  onRefresh,
}: {
  conversations: Conversation[];
  loading: boolean;
  page: number;
  totalPages: number;
  total: number;
  selectedId?: string | null;
  onSelect: (id: string) => void;
  onPageChange: (page: number) => void;
  onFiltersChange: (filters: ConversationFilters) => void;
  filters: ConversationFilters;
  stats: ConversationStats | null;
  liveStatus: RealtimeStatus;
  onRefresh?: () => void;
}) {
  const counts = useMemo(
    () => ({
      open: stats?.open ?? 0,
      closed: stats?.closed ?? 0,
      unread: stats?.unread ?? 0,
    }),
    [stats],
  );

  const liveLabel =
    liveStatus === "live" ? "Live" : liveStatus === "connecting" ? "Connecting" : "Offline";
  const liveDot =
    liveStatus === "live"
      ? "bg-emerald-500"
      : liveStatus === "connecting"
        ? "bg-amber-400 animate-pulse"
        : "bg-surface-300";

  return (
    <div className="flex h-full min-h-0 flex-col">
      {/* search + filters */}
      <div className="shrink-0 border-b border-surface-200 px-3 pb-2.5 pt-3">
        <div className="relative">
          <SearchIcon className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-surface-400" />
          <input
            type="search"
            value={filters.q}
            onChange={(event) => onFiltersChange({ ...filters, q: event.target.value })}
            placeholder="Search conversations"
            aria-label="Search conversations"
            className="h-8 w-full rounded-md border border-surface-200 bg-surface-50 pl-8 pr-3 text-[12.5px] text-surface-900 placeholder:text-surface-400 transition-colors focus:border-surface-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-brand-900/5"
          />
        </div>

        <div className="mt-2 flex items-center gap-1" role="group" aria-label="Filter by status">
          {(
            [
              { value: "ALL" as const, label: "All", count: total },
              { value: "OPEN" as const, label: "Open", count: counts.open },
              { value: "CLOSED" as const, label: "Closed", count: counts.closed },
            ] as const
          ).map((item) => {
            const active = filters.status === item.value;
            return (
              <button
                key={item.value}
                type="button"
                onClick={() => onFiltersChange({ ...filters, status: item.value })}
                aria-pressed={active}
                className={clsx(
                  "inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-[11.5px] font-medium transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500",
                  active
                    ? "bg-surface-900 text-white"
                    : "text-surface-500 hover:bg-surface-100 hover:text-surface-800",
                )}
              >
                {item.label}
                <span
                  className={clsx(
                    "text-[10px] tabular-nums",
                    active ? "text-surface-400" : "text-surface-400",
                  )}
                >
                  {formatNumber(item.count)}
                </span>
              </button>
            );
          })}
          <div className="ml-auto flex items-center gap-1.5">
            <span
              className="inline-flex items-center gap-1 text-[10px] font-medium uppercase tracking-[0.08em] text-surface-400"
              title={
                liveStatus === "live"
                  ? "Updates arrive automatically"
                  : liveStatus === "connecting"
                    ? "Connecting to live updates"
                    : "Live updates are offline"
              }
            >
              <span className={clsx("h-1.5 w-1.5 rounded-full", liveDot)} />
              {liveLabel}
            </span>
            {onRefresh ? (
              <button
                type="button"
                onClick={onRefresh}
                aria-label="Refresh conversations"
                className="rounded-md p-1.5 text-surface-400 transition-colors hover:bg-surface-100 hover:text-surface-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
              >
                <RefreshIcon className="h-3.5 w-3.5" />
              </button>
            ) : null}
          </div>
        </div>
      </div>

      {/* list */}
      <div className="min-h-0 flex-1 overflow-y-auto">
        {loading && conversations.length === 0 ? (
          <div className="space-y-2 p-3">
            {[0, 1, 2, 3, 4].map((index) => (
              <div key={index} className="rounded-lg border border-surface-200 p-3">
                <Skeleton className="h-4 w-2/3" />
                <Skeleton className="mt-2 h-3 w-full" />
                <Skeleton className="mt-2 h-3 w-1/3" />
              </div>
            ))}
          </div>
        ) : conversations.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-2 px-6 py-14 text-center">
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-surface-100 text-surface-400">
              <InboxIcon className="h-5 w-5" />
            </span>
            <p className="text-sm font-semibold text-surface-800">No conversations</p>
            <p className="text-xs text-surface-500">
              {filters.q || filters.status !== "ALL"
                ? "Try adjusting your filters."
                : "New conversations from your widget will appear here."}
            </p>
          </div>
        ) : (
          <ul className="divide-y divide-surface-100">
            {conversations.map((conversation) => {
              const active = conversation.id === selectedId;
              const contactName = conversation.contact?.name ?? "Unknown visitor";
              return (
                <li key={conversation.id}>
                  <button
                    type="button"
                    onClick={() => onSelect(conversation.id)}
                    aria-current={active ? "true" : undefined}
                    className={clsx(
                      "flex w-full gap-2.5 border-l-2 px-3 py-2.5 text-left transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand-500",
                      active
                        ? "border-brand-900 bg-surface-100/80"
                        : "border-transparent hover:bg-surface-50",
                    )}
                  >
                    <span
                      className={clsx(
                        "flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[10px] font-semibold text-white",
                        avatarColor(contactName),
                      )}
                      aria-hidden="true"
                    >
                      {initials(contactName)}
                    </span>

                    <span className="min-w-0 flex-1">
                      <span className="flex items-center gap-2">
                        <span
                          className={clsx(
                            "min-w-0 flex-1 truncate text-[12.5px]",
                            conversation.unreadCount > 0
                              ? "font-semibold text-surface-900"
                              : "font-medium text-surface-700",
                          )}
                        >
                          {contactName}
                        </span>
                        <span className="shrink-0 text-[10.5px] text-surface-400">
                          {formatRelative(conversation.lastActivityAt)}
                        </span>
                      </span>

                      <span className="mt-0.5 flex items-center gap-2">
                        <span className="min-w-0 flex-1 truncate text-[11.5px] text-surface-600">
                          {conversation.subject || "No subject"}
                        </span>
                        {conversation.unreadCount > 0 ? (
                          <span className="shrink-0 rounded-full bg-brand-900 px-1.5 py-0.5 text-[9.5px] font-semibold text-white">
                            {conversation.unreadCount}
                          </span>
                        ) : null}
                      </span>

                      <span className="mt-0.5 flex items-center gap-2">
                        <span className="min-w-0 flex-1 truncate text-[11px] text-surface-400">
                          {truncate(conversation.lastMessage?.body ?? "No messages yet", 70)}
                        </span>
                        {conversation.aiHandled ? (
                          <span
                            className="inline-flex shrink-0 items-center gap-0.5 rounded bg-accent-50 px-1 py-px text-[9.5px] font-medium text-accent-700"
                            title="Handled by AI"
                          >
                            <BotIcon className="h-2.5 w-2.5" />
                            AI
                          </span>
                        ) : null}
                      </span>

                      <span className="mt-1 flex items-center gap-1.5 text-[10.5px] text-surface-400">
                        {conversation.assigneeName ? (
                          <span className="inline-flex items-center gap-1 truncate">
                            <UserIcon className="h-2.5 w-2.5" />
                            {conversation.assigneeName}
                          </span>
                        ) : (
                          <span className="text-surface-400">Unassigned</span>
                        )}
                        {conversation.teamName ? (
                          <span className="shrink-0 rounded bg-surface-100 px-1 py-px text-[9.5px] text-surface-500">
                            {conversation.teamName}
                          </span>
                        ) : null}
                        {conversation.escalated ? (
                          <span
                            className="inline-flex shrink-0 items-center gap-0.5 rounded bg-amber-50 px-1 py-px text-[9.5px] font-medium text-amber-700"
                            title="A human is handling this thread — AI replies paused"
                          >
                            <AlertIcon className="h-2.5 w-2.5" />
                            Escalated
                          </span>
                        ) : null}
                        {conversation.status === "CLOSED" ? (
                          <span className="shrink-0 rounded bg-surface-100 px-1 py-px text-[9.5px] text-surface-500">
                            Closed
                          </span>
                        ) : null}
                      </span>
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      {/* pagination */}
      <div className="flex items-center justify-between gap-2 border-t border-surface-200 px-3 py-1.5">
        <p className="text-[11px] text-surface-500">
          {formatNumber(total)} conversation{total === 1 ? "" : "s"}
        </p>
        <div className="flex items-center gap-1.5">
          <Button
            size="sm"
            variant="ghost"
            disabled={page <= 0 || loading}
            onClick={() => onPageChange(Math.max(0, page - 1))}
            aria-label="Previous page"
          >
            Prev
          </Button>
          <span className="px-1 text-[11px] tabular-nums text-surface-500">
            {totalPages > 0 ? `${page + 1} / ${totalPages}` : "0 / 0"}
          </span>
          <Button
            size="sm"
            variant="ghost"
            disabled={page + 1 >= totalPages || loading}
            onClick={() => onPageChange(page + 1)}
            aria-label="Next page"
          >
            Next
          </Button>
        </div>
      </div>
    </div>
  );
}
