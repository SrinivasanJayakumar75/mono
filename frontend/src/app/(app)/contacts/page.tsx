"use client";

import clsx from "clsx";
import { useCallback, useEffect, useState } from "react";

import {
  CloseIcon,
  GlobeIcon,
  MailIcon,
  MonitorIcon,
  RefreshIcon,
  SearchIcon,
  UsersIcon,
} from "@/components/icons";
import { Badge, Button, EmptyState, ErrorBanner, Skeleton } from "@/components/ui";
import { apiFetch } from "@/lib/api";
import {
  avatarColor,
  formatDateTime,
  formatNumber,
  formatRelative,
  initials,
} from "@/lib/format";
import type { Contact, ContactDetail, Page } from "@/lib/types";

const PAGE_SIZE = 25;

export default function ContactsPage() {
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [activeId, setActiveId] = useState<string | null>(null);
  const [detail, setDetail] = useState<ContactDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
      setPage(0);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await apiFetch<Page<Contact>>("/contacts", {
        params: { q: debouncedQuery || undefined, page, size: PAGE_SIZE },
      });
      setContacts(result.items ?? []);
      setTotal(result.total ?? 0);
      setTotalPages(result.totalPages ?? 0);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load contacts.");
    } finally {
      setLoading(false);
    }
  }, [debouncedQuery, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const loadDetail = useCallback(async (id: string) => {
    setDetailLoading(true);
    setDetailError(null);
    try {
      const result = await apiFetch<ContactDetail>(`/contacts/${encodeURIComponent(id)}`);
      setDetail(result);
    } catch (err) {
      setDetailError(err instanceof Error ? err.message : "Could not load the contact.");
    } finally {
      setDetailLoading(false);
    }
  }, []);

  function openContact(contact: Contact) {
    setActiveId(contact.id);
    setDetail(null);
    void loadDetail(contact.id);
  }

  const contact = detail?.contact ?? null;

  return (
    <div className="flex h-full min-h-0 flex-col">
      {error ? (
        <div className="shrink-0 border-b border-surface-200 px-3 py-2">
          <ErrorBanner message={error} onRetry={() => void load()} />
        </div>
      ) : null}

      <div className="flex min-h-0 flex-1 flex-col bg-white">
        <div className="flex shrink-0 flex-wrap items-center gap-3 border-b border-surface-200 px-3 py-2.5">
          <div className="relative min-w-[14rem] flex-1">
            <SearchIcon className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-surface-400" />
            <input
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search by name or email"
              aria-label="Search contacts"
              className="h-8 w-full rounded-md border border-surface-200 bg-surface-50 pl-8 pr-3 text-[12.5px] text-surface-900 placeholder:text-surface-400 transition-colors focus:border-surface-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-brand-900/5"
            />
          </div>
          <p className="text-[11.5px] text-surface-500">
            {formatNumber(total)} contact{total === 1 ? "" : "s"}
          </p>
          <Button
            size="icon"
            variant="ghost"
            onClick={() => void load()}
            aria-label="Refresh contacts"
          >
            <RefreshIcon className="h-3.5 w-3.5" />
          </Button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto">
          {loading && contacts.length === 0 ? (
            <div className="space-y-2 p-4">
              {[0, 1, 2, 3, 4].map((index) => (
                <div key={index} className="flex items-center gap-3 rounded-lg border border-surface-200 p-3">
                  <Skeleton className="h-9 w-9 rounded-full" />
                  <div className="flex-1">
                    <Skeleton className="h-4 w-40" />
                    <Skeleton className="mt-2 h-3 w-56" />
                  </div>
                </div>
              ))}
            </div>
          ) : contacts.length === 0 ? (
            <div className="p-6">
              <EmptyState
                icon={<UsersIcon className="h-5 w-5" />}
                title="No contacts found"
                description={
                  query
                    ? "Try a different search term."
                    : "Visitors who start a chat appear here automatically."
                }
              />
            </div>
          ) : (
            <ul className="divide-y divide-surface-100">
              {contacts.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => openContact(item)}
                    className={clsx(
                      "flex w-full items-center gap-3 border-l-2 px-4 py-2.5 text-left transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand-500",
                      activeId === item.id
                        ? "border-brand-900 bg-surface-100/80"
                        : "border-transparent hover:bg-surface-50",
                    )}
                  >
                    <span
                      className={clsx(
                        "flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-[11px] font-semibold text-white",
                        avatarColor(item.name),
                      )}
                      aria-hidden="true"
                    >
                      {initials(item.name)}
                    </span>

                    <span className="min-w-0 flex-1">
                      <span className="flex items-center gap-2">
                        <span className="truncate text-[12.5px] font-medium text-surface-900">
                          {item.name}
                        </span>
                        {item.online ? (
                          <span className="inline-flex shrink-0 items-center gap-1 rounded bg-emerald-50 px-1 py-px text-[9.5px] font-medium text-emerald-700">
                            <span className="h-1 w-1 rounded-full bg-emerald-500" />
                            Online
                          </span>
                        ) : null}
                      </span>
                      <span className="mt-0.5 block truncate text-[11.5px] text-surface-500">
                        {item.email ?? "No email"}
                      </span>
                    </span>

                    <span className="hidden shrink-0 items-center gap-4 text-[11px] text-surface-400 lg:flex">
                      {item.browser ? (
                        <span className="inline-flex items-center gap-1">
                          <MonitorIcon className="h-3 w-3" />
                          {item.browser}
                        </span>
                      ) : null}
                      {item.location ? (
                        <span className="inline-flex items-center gap-1">
                          <GlobeIcon className="h-3 w-3" />
                          {item.location}
                        </span>
                      ) : null}
                      <span className="tabular-nums">
                        {formatNumber(item.conversationCount ?? 0)} conversations
                      </span>
                    </span>

                    <span className="shrink-0 text-[10.5px] text-surface-400">
                      {formatRelative(item.lastSeenAt ?? item.createdAt)}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        {totalPages > 1 ? (
          <div className="flex shrink-0 items-center justify-between gap-2 border-t border-surface-200 px-4 py-2">
            <p className="text-[11px] text-surface-500">
              Page {page + 1} of {totalPages}
            </p>
            <div className="flex gap-1.5">
              <Button
                size="sm"
                variant="ghost"
                disabled={page <= 0 || loading}
                onClick={() => setPage((value) => Math.max(0, value - 1))}
              >
                Prev
              </Button>
              <Button
                size="sm"
                variant="ghost"
                disabled={page + 1 >= totalPages || loading}
                onClick={() => setPage((value) => value + 1)}
              >
                Next
              </Button>
            </div>
          </div>
        ) : null}
      </div>

      {/* drawer */}
      {activeId ? (
        <div className="fixed inset-0 z-50 flex justify-end">
          <div
            className="absolute inset-0 bg-surface-950/40 animate-fade-in"
            onClick={() => setActiveId(null)}
            aria-hidden="true"
          />
          <aside
            role="dialog"
            aria-modal="true"
            aria-label="Contact details"
            className="relative z-10 flex h-full w-full max-w-md flex-col overflow-y-auto bg-white shadow-lift animate-fade-up"
          >
            <header className="flex items-start justify-between gap-3 border-b border-surface-200 px-5 py-4">
              <div className="flex min-w-0 items-center gap-3">
                <span
                  className={clsx(
                    "flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-sm font-semibold text-white",
                    avatarColor(contact?.name ?? activeId),
                  )}
                >
                  {initials(contact?.name)}
                </span>
                <div className="min-w-0">
                  <h2 className="truncate text-base font-semibold text-surface-900">
                    {contact?.name ?? "Loading…"}
                  </h2>
                  <p className="truncate text-xs text-surface-500">
                    {contact?.email ?? "—"}
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setActiveId(null)}
                aria-label="Close contact details"
                className="rounded-lg p-1.5 text-surface-400 transition-colors hover:bg-surface-100 hover:text-surface-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
              >
                <CloseIcon className="h-5 w-5" />
              </button>
            </header>

            <div className="space-y-5 px-5 py-4">
              {detailError ? (
                <ErrorBanner message={detailError} onRetry={() => void loadDetail(activeId)} />
              ) : null}

              {detailLoading ? (
                <div className="space-y-3">
                  <Skeleton className="h-4 w-32" />
                  <Skeleton className="h-20 w-full" />
                  <Skeleton className="h-4 w-24" />
                  <Skeleton className="h-12 w-full" />
                </div>
              ) : contact ? (
                <>
                  <section>
                    <h3 className="text-xs font-semibold uppercase tracking-wide text-surface-400">
                      Details
                    </h3>
                    <dl className="mt-2 space-y-2 text-sm">
                      <div className="flex items-center gap-2">
                        <dt className="flex w-28 shrink-0 items-center gap-1.5 text-xs text-surface-500">
                          <MailIcon className="h-3.5 w-3.5" /> Email
                        </dt>
                        <dd className="min-w-0 flex-1 truncate text-surface-800">
                          {contact.email ?? "—"}
                        </dd>
                      </div>
                      <div className="flex items-center gap-2">
                        <dt className="flex w-28 shrink-0 items-center gap-1.5 text-xs text-surface-500">
                          <GlobeIcon className="h-3.5 w-3.5" /> Location
                        </dt>
                        <dd className="min-w-0 flex-1 truncate text-surface-800">
                          {contact.location ?? "—"}
                        </dd>
                      </div>
                      <div className="flex items-center gap-2">
                        <dt className="flex w-28 shrink-0 items-center gap-1.5 text-xs text-surface-500">
                          <MonitorIcon className="h-3.5 w-3.5" /> Browser
                        </dt>
                        <dd className="min-w-0 flex-1 truncate text-surface-800">
                          {contact.browser ?? "—"}
                          {contact.os ? ` · ${contact.os}` : ""}
                        </dd>
                      </div>
                      <div className="flex items-center gap-2">
                        <dt className="w-28 shrink-0 text-xs text-surface-500">Visitor ID</dt>
                        <dd className="min-w-0 flex-1 truncate font-mono text-xs text-surface-600">
                          {contact.visitorId ?? "—"}
                        </dd>
                      </div>
                      <div className="flex items-center gap-2">
                        <dt className="w-28 shrink-0 text-xs text-surface-500">First seen</dt>
                        <dd className="min-w-0 flex-1 truncate text-surface-800">
                          {formatDateTime(contact.createdAt)}
                        </dd>
                      </div>
                    </dl>
                  </section>

                  <section>
                    <h3 className="text-xs font-semibold uppercase tracking-wide text-surface-400">
                      Conversations
                    </h3>
                    {(detail?.conversations?.length ?? 0) === 0 ? (
                      <p className="mt-2 rounded-lg border border-dashed border-surface-300 bg-surface-50 px-4 py-6 text-center text-xs text-surface-500">
                        No conversations with this contact yet.
                      </p>
                    ) : (
                      <ul className="mt-2 space-y-2">
                        {(detail?.conversations ?? []).map((conversation) => (
                          <li
                            key={conversation.id}
                            className="rounded-lg border border-surface-200 px-3 py-2.5"
                          >
                            <div className="flex items-center justify-between gap-2">
                              <p className="truncate text-sm font-medium text-surface-800">
                                {conversation.subject || "No subject"}
                              </p>
                              <Badge
                                variant={
                                  conversation.status === "OPEN" ? "success" : "neutral"
                                }
                              >
                                {conversation.status}
                              </Badge>
                            </div>
                            <p className="mt-1 text-[11px] text-surface-400">
                              Last activity {formatRelative(conversation.lastActivityAt)}
                            </p>
                          </li>
                        ))}
                      </ul>
                    )}
                  </section>
                </>
              ) : null}
            </div>
          </aside>
        </div>
      ) : null}
    </div>
  );
}
