"use client";

import clsx from "clsx";
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";

import {
  AlertIcon,
  BotIcon,
  BuildingIcon,
  CheckIcon,
  ClockIcon,
  CloseIcon,
  FileIcon,
  GlobeIcon,
  InfoIcon,
  LeadsIcon,
  LinkIcon,
  MailIcon,
  MonitorIcon,
  RefreshIcon,
  SendIcon,
  SparklesIcon,
  TagIcon,
  UserIcon,
} from "./icons";
import {
  Badge,
  Button,
  ErrorBanner,
  Modal,
  Skeleton,
  Textarea,
  useToast,
} from "./ui";
import { ApiError, apiFetch } from "@/lib/api";
import {
  avatarColor,
  dayKey,
  formatDayHeading,
  formatRelative,
  formatScore,
  formatTime,
  initials,
} from "@/lib/format";
import type {
  Citation,
  Conversation,
  ConversationDetail,
  Message,
  Team,
  WorkspaceMember,
} from "@/lib/types";

const AUTHOR_STYLES = {
  CONTACT: {
    bubble: "bg-surface-100 text-surface-800",
    label: "text-surface-600",
    align: "items-start",
    avatar: "bg-surface-400",
  },
  AGENT: {
    bubble: "bg-brand-900 text-white",
    label: "text-brand-700",
    align: "items-end",
    avatar: "bg-brand-900",
  },
  AI: {
    bubble: "bg-accent-50 text-accent-950 ring-1 ring-inset ring-accent-200",
    label: "text-accent-700",
    align: "items-start",
    avatar: "bg-accent-500",
  },
} as const;

function CitationCard({ citation }: { citation: Citation }) {
  const body = (
    <>
      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-white/70 text-accent-600">
        {citation.sourceType === "DOCUMENT" ? (
          <FileIcon className="h-3.5 w-3.5" />
        ) : (
          <LinkIcon className="h-3.5 w-3.5" />
        )}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-xs font-semibold text-accent-950">
          {citation.title || "Untitled source"}
        </span>
        <span className="mt-0.5 flex items-center gap-1.5 text-[10px] uppercase tracking-wide text-accent-600">
          {citation.sourceType}
          {citation.score !== undefined ? (
            <span className="rounded bg-white/70 px-1 py-px font-mono">
              {formatScore(citation.score)}
            </span>
          ) : null}
        </span>
      </span>
    </>
  );

  if (citation.url) {
    return (
      <a
        href={citation.url}
        target="_blank"
        rel="noreferrer"
        className="flex items-center gap-2 rounded-lg bg-white/70 p-2 transition-colors hover:bg-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent-500"
      >
        {body}
      </a>
    );
  }

  return (
    <div className="flex items-center gap-2 rounded-lg bg-white/70 p-2">{body}</div>
  );
}

function DetailRow({
  icon,
  label,
  value,
}: {
  icon: ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-start gap-2">
      <span className="mt-0.5 text-surface-400">{icon}</span>
      <span className="min-w-0 flex-1">
        <span className="block text-[10px] uppercase tracking-[0.08em] text-surface-400">
          {label}
        </span>
        <span className="block truncate text-[12px] text-surface-700">{value}</span>
      </span>
    </div>
  );
}

export function ConversationThread({
  conversationId,
  liveUpdate,
  onBack,
  onChanged,
}: {
  conversationId: string;
  liveUpdate?: { seq: number; conversationId: string | null };
  onBack?: () => void;
  onChanged?: () => void;
}) {
  const { toast } = useToast();

  const [detail, setDetail] = useState<ConversationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [askingAi, setAskingAi] = useState(false);
  const [addingToCrm, setAddingToCrm] = useState(false);
  const [showDetails, setShowDetails] = useState(true);

  const [assignOpen, setAssignOpen] = useState(false);
  const [teamOpen, setTeamOpen] = useState(false);
  const [members, setMembers] = useState<WorkspaceMember[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [rosterLoading, setRosterLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const bottomRef = useRef<HTMLDivElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const loadSeq = useRef(0);
  const lastMutationAt = useRef(0);
  const stickToBottom = useRef(true);

  const load = useCallback(
    async (silent = false) => {
      const seq = ++loadSeq.current;
      const startedAt = Date.now();
      if (!silent) {
        setLoading(true);
        setError(null);
      }
      try {
        const data = await apiFetch<ConversationDetail>(
          `/conversations/${encodeURIComponent(conversationId)}`,
        );
        if (seq !== loadSeq.current) return;
        if (silent && startedAt < lastMutationAt.current) return;
        setDetail(data);
        setError(null);
      } catch (err) {
        if (seq !== loadSeq.current || silent) return;
        setError(err instanceof Error ? err.message : "Could not load this conversation.");
      } finally {
        if (!silent) setLoading(false);
      }
    },
    [conversationId],
  );

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!liveUpdate || liveUpdate.seq === 0) return;
    if (liveUpdate.conversationId && liveUpdate.conversationId !== conversationId) return;
    void load(true);
  }, [liveUpdate, conversationId, load]);

  useEffect(() => {
    if (stickToBottom.current) {
      bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
    }
  }, [detail?.messages.length, askingAi]);

  const conversation: Conversation | null = detail?.conversation ?? null;
  const contact = detail?.contact ?? null;

  const grouped = useMemo(() => {
    const messages = detail?.messages ?? [];
    const groups: { key: string; label: string; items: Message[] }[] = [];
    for (const message of messages) {
      const key = dayKey(message.createdAt);
      const last = groups[groups.length - 1];
      if (last && last.key === key) {
        last.items.push(message);
      } else {
        groups.push({
          key,
          label: formatDayHeading(message.createdAt),
          items: [message],
        });
      }
    }
    return groups;
  }, [detail?.messages]);

  async function loadRoster(target: "assign" | "team") {
    setRosterLoading(true);
    try {
      if (target === "assign" && members.length === 0) {
        const data = await apiFetch<{ items: WorkspaceMember[] }>("/workspace/members");
        setMembers(data.items ?? []);
      }
      if (target === "team" && teams.length === 0) {
        const data = await apiFetch<{ items: Team[] }>("/teams");
        setTeams(data.items ?? []);
      }
    } catch (err) {
      toast("Could not load options", {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    } finally {
      setRosterLoading(false);
    }
  }

  function appendMessage(message: Message) {
    setDetail((current) => {
      if (!current) return current;
      if (current.messages.some((existing) => existing.id === message.id)) return current;
      return { ...current, messages: [...current.messages, message] };
    });
  }

  function handleScroll() {
    const element = scrollRef.current;
    if (!element) return;
    stickToBottom.current =
      element.scrollHeight - element.scrollTop - element.clientHeight < 96;
  }

  async function sendReply(event: FormEvent) {
    event.preventDefault();
    const body = draft.trim();
    if (!body) return;
    stickToBottom.current = true;
    setSending(true);
    try {
      const message = await apiFetch<Message>(
        `/conversations/${encodeURIComponent(conversationId)}/reply`,
        { method: "POST", json: { body } },
      );
      lastMutationAt.current = Date.now();
      appendMessage(message);
      setDraft("");
      toast("Reply sent", { tone: "success" });
      onChanged?.();
    } catch (err) {
      toast("Message not sent", {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    } finally {
      setSending(false);
    }
  }

  async function askAi() {
    // The question is optional — fall back to the latest inbound message so the
    // assistant always has something to answer.
    const lastContactMessage = [...(detail?.messages ?? [])]
      .reverse()
      .find((message) => message.authorType === "CONTACT");
    const question = draft.trim() || lastContactMessage?.body || "";

    if (!question) {
      toast("Type a question first", {
        tone: "info",
        description: "Ask AI needs a question, or an inbound customer message to answer.",
      });
      return;
    }

    stickToBottom.current = true;
    setAskingAi(true);
    try {
      const message = await apiFetch<Message>(
        `/conversations/${encodeURIComponent(conversationId)}/ai-reply`,
        { method: "POST", json: question ? { question } : {} },
      );
      lastMutationAt.current = Date.now();
      appendMessage(message);
      setDraft("");
      toast("AI reply added", { tone: "success" });
      onChanged?.();
    } catch (err) {
      toast("AI could not answer", {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    } finally {
      setAskingAi(false);
    }
  }

  async function addToCrm() {
    if (!contact || contact.crmLeadId || addingToCrm) return;
    setAddingToCrm(true);
    try {
      const result = await apiFetch<{
        created: boolean;
        lead: { id: string; name?: string | null };
      }>(`/crm/leads/from-contact/${encodeURIComponent(contact.id)}`, {
        method: "POST",
      });
      setDetail((current) =>
        current
          ? {
              ...current,
              contact: { ...current.contact, crmLeadId: result.lead.id },
            }
          : current,
      );
      toast(result.created ? "Added to CRM" : "Already in CRM", {
        tone: "success",
        description: result.created
          ? `${result.lead.name ?? contact.name} is now a lead in your CRM.`
          : `${result.lead.name ?? contact.name} is already a CRM lead.`,
      });
    } catch (err) {
      toast("Could not add to CRM", {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    } finally {
      setAddingToCrm(false);
    }
  }

  async function mutate(
    path: string,
    payload: Record<string, unknown> | null,
    successMessage: string,
  ) {
    setActionLoading(path);
    try {
      const updated = await apiFetch<Conversation>(path, {
        method: "POST",
        json: payload ?? {},
      });
      lastMutationAt.current = Date.now();
      setDetail((current) =>
        current ? { ...current, conversation: { ...current.conversation, ...updated } } : current,
      );
      toast(successMessage, { tone: "success" });
      onChanged?.();
      return true;
    } catch (err) {
      const message =
        err instanceof ApiError
          ? (Object.values(err.fieldErrors ?? {})[0] ?? err.message)
          : "Something went wrong.";
      toast("Action failed", { tone: "error", description: message });
      return false;
    } finally {
      setActionLoading(null);
    }
  }

  if (loading && !detail) {
    return (
      <div className="flex h-full flex-col">
        <div className="border-b border-surface-200 px-4 py-3">
          <Skeleton className="h-4 w-44" />
          <Skeleton className="mt-2 h-3 w-32" />
        </div>
        <div className="flex-1 space-y-4 overflow-y-auto p-4">
          <Skeleton className="h-14 w-2/3 rounded-lg" />
          <Skeleton className="ml-auto h-16 w-2/3 rounded-lg" />
          <Skeleton className="h-20 w-3/4 rounded-lg" />
        </div>
      </div>
    );
  }

  if (error && !detail) {
    return (
      <div className="p-4">
        <ErrorBanner message={error} onRetry={() => void load()} />
      </div>
    );
  }

  if (!detail || !conversation || !contact) {
    return (
      <div className="flex h-full items-center justify-center p-8 text-sm text-surface-500">
        Conversation unavailable.
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-0 bg-white">
      <div className="flex min-h-0 flex-1 flex-col">
      {/* header */}
      <header className="shrink-0 border-b border-surface-200 bg-white px-4 py-2.5 sm:px-5">
        <div className="flex items-start gap-3">
          {onBack ? (
            <button
              type="button"
              onClick={onBack}
              aria-label="Back to conversations"
              className="rounded-md p-1.5 text-surface-500 hover:bg-surface-100 lg:hidden"
            >
              <CloseIcon className="h-5 w-5" />
            </button>
          ) : null}

          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="truncate text-[14px] font-semibold tracking-[-0.01em] text-surface-900">
                {contact?.name ?? "Unknown visitor"}
              </h2>
              <Badge
                variant={conversation.status === "OPEN" ? "success" : "neutral"}
              >
                {conversation.status === "OPEN" ? "Open" : "Closed"}
              </Badge>
              {conversation.escalated ? (
                <Badge variant="warning">
                  <AlertIcon className="h-3 w-3" />
                  Escalated
                </Badge>
              ) : null}
              {conversation.aiHandled ? (
                <Badge variant="brand">
                  <BotIcon className="h-3 w-3" />
                  AI handled
                </Badge>
              ) : null}
              <Badge variant="info">{String(conversation.channel ?? "—")}</Badge>
            </div>
            <p className="mt-0.5 truncate text-[11.5px] text-surface-500">
              {conversation.subject || "No subject"}
              {contact?.email ? ` · ${contact.email}` : ""}
              {contact?.location ? ` · ${contact.location}` : ""}
            </p>
          </div>

          <div className="flex shrink-0 flex-wrap items-center justify-end gap-1.5">
            <Button
              size="sm"
              variant="outline"
              onClick={() => {
                setAssignOpen(true);
                void loadRoster("assign");
              }}
            >
              <UserIcon className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">
                {conversation.assigneeName ? "Reassign" : "Assign"}
              </span>
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => {
                setTeamOpen(true);
                void loadRoster("team");
              }}
            >
              <LinkIcon className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">Team</span>
            </Button>
            <Button
              size="sm"
              variant="outline"
              loading={addingToCrm}
              disabled={Boolean(contact.crmLeadId)}
              onClick={() => void addToCrm()}
              aria-label={
                contact.crmLeadId ? "Customer is already in CRM" : "Add customer to CRM"
              }
              title={
                contact.crmLeadId
                  ? "This customer is already a lead in the CRM."
                  : "Create a CRM lead from this inbox customer."
              }
            >
              {contact.crmLeadId ? (
                <CheckIcon className="h-3.5 w-3.5" />
              ) : (
                <LeadsIcon className="h-3.5 w-3.5" />
              )}
              <span className="hidden sm:inline">
                {contact.crmLeadId ? "In CRM" : "Add to CRM"}
              </span>
            </Button>
            <Button
              size="sm"
              variant="outline"
              loading={actionLoading === `/conversations/${conversationId}/escalate`}
              onClick={() =>
                void mutate(
                  `/conversations/${conversationId}/escalate`,
                  { escalated: !conversation.escalated },
                  conversation.escalated
                    ? "AI replies resumed"
                    : "Escalated — AI replies paused",
                )
              }
              title={
                conversation.escalated
                  ? "A human is handling this thread. Click to let the AI answer again."
                  : "Take over this conversation and pause the AI's automatic replies."
              }
              aria-pressed={conversation.escalated}
              className={
                conversation.escalated
                  ? "border-amber-300 bg-amber-50 text-amber-800 hover:border-amber-400 hover:bg-amber-100"
                  : undefined
              }
            >
              <AlertIcon className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">
                {conversation.escalated ? "Escalated" : "Escalate"}
              </span>
            </Button>
            {conversation.status === "OPEN" ? (
              <Button
                size="sm"
                variant="secondary"
                loading={actionLoading === `/conversations/${conversationId}/close`}
                onClick={() =>
                  void mutate(`/conversations/${conversationId}/close`, null, "Conversation closed")
                }
              >
                Close
              </Button>
            ) : (
              <Button
                size="sm"
                variant="secondary"
                loading={actionLoading === `/conversations/${conversationId}/reopen`}
                onClick={() =>
                  void mutate(
                    `/conversations/${conversationId}/reopen`,
                    null,
                    "Conversation reopened",
                  )
                }
              >
                Reopen
              </Button>
            )}
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setShowDetails((value) => !value)}
              className="hidden 2xl:inline-flex"
              aria-pressed={showDetails}
            >
              <InfoIcon className="h-3.5 w-3.5" />
              Details
            </Button>
            <Button size="icon" variant="ghost" onClick={() => void load()} aria-label="Refresh conversation">
              <RefreshIcon className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </header>

      {conversation.escalated ? (
        <div className="flex shrink-0 items-center gap-2 border-b border-amber-200 bg-amber-50 px-4 py-1.5 text-[11.5px] text-amber-800 sm:px-5">
          <AlertIcon className="h-3.5 w-3.5 shrink-0" />
          <span className="flex-1">
            Escalated to a human — the AI will not reply automatically to this conversation.
          </span>
          <button
            type="button"
            onClick={() =>
              void mutate(
                `/conversations/${conversationId}/escalate`,
                { escalated: false },
                "AI replies resumed",
              )
            }
            className="shrink-0 font-medium underline underline-offset-2 hover:text-amber-900"
          >
            Resume AI
          </button>
        </div>
      ) : null}

      {/* messages */}
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="min-h-0 flex-1 overflow-y-auto bg-white px-4 py-5 sm:px-6"
      >
        {grouped.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center gap-2 text-center">
            <span className="flex h-10 w-10 items-center justify-center rounded-full border border-surface-200 bg-white text-surface-400">
              <MessagePlaceholder />
            </span>
            <p className="text-[13px] font-medium text-surface-700">No messages yet</p>
            <p className="text-[12.5px] text-surface-500">
              Say hello — the visitor will see your reply instantly.
            </p>
          </div>
        ) : (
          <div className="mx-auto w-full max-w-3xl space-y-5">
            {grouped.map((group) => (
              <div key={group.key} className="space-y-3">
                <div className="flex items-center gap-3">
                  <span className="h-px flex-1 bg-surface-100" />
                  <span className="rounded-md bg-surface-100 px-2 py-0.5 text-[10.5px] font-medium text-surface-500">
                    {group.label}
                  </span>
                  <span className="h-px flex-1 bg-surface-100" />
                </div>

                {group.items.map((message) => {
                  const style = AUTHOR_STYLES[message.authorType] ?? AUTHOR_STYLES.CONTACT;
                  const isAgent = message.authorType === "AGENT";
                  const isAi = message.authorType === "AI";
                  return (
                    <div
                      key={message.id}
                      className={clsx(
                        "flex gap-2.5",
                        isAgent ? "flex-row-reverse" : "flex-row",
                      )}
                    >
                      <span
                        className={clsx(
                          "flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-[10px] font-semibold text-white",
                          isAi ? style.avatar : avatarColor(message.authorName),
                        )}
                        aria-hidden="true"
                      >
                        {isAi ? (
                          <SparklesIcon className="h-3.5 w-3.5" />
                        ) : (
                          initials(message.authorName)
                        )}
                      </span>

                      <div
                        className={clsx(
                          "max-w-[85%] space-y-1.5 sm:max-w-[75%]",
                          isAgent ? "items-end text-right" : "items-start",
                          isAgent ? "flex flex-col" : "flex flex-col",
                        )}
                      >
                        <div
                          className={clsx(
                            "flex items-center gap-2 text-[10.5px]",
                            style.label,
                            isAgent ? "justify-end" : "justify-start",
                          )}
                        >
                          <span className="font-medium">
                            {isAi ? "Monotron AI" : (message.authorName ?? "Unknown")}
                          </span>
                          <span className="text-surface-400">
                            {formatTime(message.createdAt)}
                          </span>
                        </div>

                        <div
                          className={clsx(
                            "whitespace-pre-wrap break-words rounded-xl px-3.5 py-2 text-[13px] leading-relaxed",
                            isAgent ? "rounded-tr-sm" : "rounded-tl-sm",
                            style.bubble,
                          )}
                        >
                          {message.body}
                        </div>

                        {message.citations && message.citations.length > 0 ? (
                          <div className="space-y-1.5">
                            <p className="text-[9.5px] font-medium uppercase tracking-[0.1em] text-accent-600">
                              Sources
                            </p>
                            <div className="grid gap-1.5">
                              {message.citations.map((citation, index) => (
                                <CitationCard
                                  key={`${message.id}-citation-${index}`}
                                  citation={citation}
                                />
                              ))}
                            </div>
                          </div>
                        ) : null}
                      </div>
                    </div>
                  );
                })}
              </div>
            ))}
            <div ref={bottomRef} />
          </div>
        )}

        {askingAi ? (
          <div className="mx-auto mt-4 flex w-full max-w-3xl items-center gap-2.5">
            <span className="flex h-7 w-7 items-center justify-center rounded-full bg-accent-500 text-white">
              <SparklesIcon className="h-3.5 w-3.5" />
            </span>
            <span className="flex items-center gap-1.5 rounded-xl rounded-tl-sm bg-accent-50 px-3.5 py-3 ring-1 ring-inset ring-accent-200">
              {[0, 1, 2].map((index) => (
                <span
                  key={index}
                  className="h-1.5 w-1.5 animate-typing-dot rounded-full bg-accent-500"
                  style={{ animationDelay: `${index * 0.15}s` }}
                />
              ))}
              <span className="sr-only">AI is typing</span>
            </span>
          </div>
        ) : null}
      </div>

      {/* composer */}
      <form
        onSubmit={sendReply}
        className="shrink-0 border-t border-surface-200 bg-white px-4 py-2.5 sm:px-6"
      >
        <div className="mx-auto w-full max-w-3xl">
          <Textarea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="Write a reply…"
            rows={2}
            aria-label="Reply message"
            className="mb-2"
          />
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <Button
                type="button"
                size="sm"
                variant="ghost"
                onClick={() => void askAi()}
                loading={askingAi}
                icon={<SparklesIcon className="h-3.5 w-3.5" />}
              >
                Ask AI
              </Button>
              <span className="hidden text-[11px] text-surface-400 sm:inline">
                Grounded in your knowledge base
              </span>
            </div>
            <div className="flex items-center gap-2">
              <span className="hidden text-[11px] text-surface-400 sm:inline">
                Shift + Enter for a new line
              </span>
              <Button
                type="submit"
                size="sm"
                loading={sending}
                icon={<SendIcon className="h-3.5 w-3.5" />}
              >
                Send
              </Button>
            </div>
          </div>
        </div>
      </form>
      </div>

      {showDetails ? (
        <aside className="hidden w-72 shrink-0 flex-col overflow-y-auto border-l border-surface-200 bg-surface-50/40 2xl:flex">
          <div className="flex h-11 shrink-0 items-center justify-between border-b border-surface-200 px-4">
            <h3 className="text-[12px] font-medium text-surface-800">Conversation details</h3>
            <button
              type="button"
              onClick={() => setShowDetails(false)}
              aria-label="Hide details"
              className="rounded p-1 text-surface-400 transition-colors hover:bg-surface-200/60 hover:text-surface-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
            >
              <CloseIcon className="h-3.5 w-3.5" />
            </button>
          </div>

          <div className="space-y-5 p-4">
            <section className="text-center">
              <span
                className={clsx(
                  "mx-auto flex h-12 w-12 items-center justify-center rounded-full text-sm font-semibold text-white",
                  avatarColor(contact.name),
                )}
              >
                {initials(contact.name)}
              </span>
              <p className="mt-2 truncate text-[13px] font-medium text-surface-900">
                {contact.name}
              </p>
              <p className="truncate text-[11.5px] text-surface-500">
                {contact.email ?? "No email"}
              </p>
              <span
                className={clsx(
                  "mt-1.5 inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[9.5px] font-medium",
                  contact.online
                    ? "bg-emerald-50 text-emerald-700"
                    : "bg-surface-100 text-surface-500",
                )}
              >
                <span
                  className={clsx(
                    "h-1 w-1 rounded-full",
                    contact.online ? "bg-emerald-500" : "bg-surface-400",
                  )}
                />
                {contact.online ? "Online now" : "Offline"}
              </span>
            </section>

            <section>
              <h4 className="text-[10px] font-medium uppercase tracking-[0.12em] text-surface-400">
                Contact
              </h4>
              <dl className="mt-2 space-y-2.5">
                <DetailRow
                  icon={<MailIcon className="h-3.5 w-3.5" />}
                  label="Email"
                  value={contact.email ?? "—"}
                />
                <DetailRow
                  icon={<GlobeIcon className="h-3.5 w-3.5" />}
                  label="Location"
                  value={contact.location ?? "—"}
                />
                <DetailRow
                  icon={<MonitorIcon className="h-3.5 w-3.5" />}
                  label="Browser"
                  value={
                    [contact.browser, contact.os].filter(Boolean).join(" · ") || "—"
                  }
                />
                <DetailRow
                  icon={<ClockIcon className="h-3.5 w-3.5" />}
                  label="Last seen"
                  value={contact.lastSeenAt ? formatRelative(contact.lastSeenAt) : "—"}
                />
              </dl>
            </section>

            <section>
              <h4 className="text-[10px] font-medium uppercase tracking-[0.12em] text-surface-400">
                Conversation
              </h4>
              <dl className="mt-2 space-y-2.5">
                <DetailRow
                  icon={<TagIcon className="h-3.5 w-3.5" />}
                  label="Status"
                  value={conversation.status === "OPEN" ? "Open" : "Closed"}
                />
                <DetailRow
                  icon={<InfoIcon className="h-3.5 w-3.5" />}
                  label="Channel"
                  value={String(conversation.channel ?? "—")}
                />
                <DetailRow
                  icon={<UserIcon className="h-3.5 w-3.5" />}
                  label="Assignee"
                  value={conversation.assigneeName ?? "Unassigned"}
                />
                <DetailRow
                  icon={<BuildingIcon className="h-3.5 w-3.5" />}
                  label="Team"
                  value={conversation.teamName ?? "—"}
                />
                <DetailRow
                  icon={<ClockIcon className="h-3.5 w-3.5" />}
                  label="Started"
                  value={formatRelative(conversation.createdAt)}
                />
              </dl>
            </section>
          </div>
        </aside>
      ) : null}

      {/* assign modal */}
      <Modal
        open={assignOpen}
        onClose={() => setAssignOpen(false)}
        title="Assign conversation"
        description="Pick a teammate to own this conversation."
        footer={
          <>
            <Button variant="ghost" onClick={() => setAssignOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="secondary"
              onClick={async () => {
                const ok = await mutate(
                  `/conversations/${conversationId}/assign`,
                  { assigneeId: null },
                  "Conversation unassigned",
                );
                if (ok) setAssignOpen(false);
              }}
            >
              Unassign
            </Button>
          </>
        }
      >
        {rosterLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
          </div>
        ) : (
          <ul className="space-y-1.5">
            {members.length === 0 ? (
              <li className="rounded-lg border border-dashed border-surface-300 bg-surface-50 px-4 py-6 text-center text-sm text-surface-500">
                No teammates found in this workspace.
              </li>
            ) : (
              members.map((member) => (
                <li key={member.userId}>
                  <button
                    type="button"
                    onClick={async () => {
                      const ok = await mutate(
                        `/conversations/${conversationId}/assign`,
                        { assigneeId: member.userId },
                        `Assigned to ${member.name}`,
                      );
                      if (ok) setAssignOpen(false);
                    }}
                    className={clsx(
                      "flex w-full items-center gap-3 rounded-lg border px-3 py-2.5 text-left transition-colors hover:bg-surface-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500",
                      conversation.assigneeId === member.userId
                        ? "border-brand-300 bg-brand-50"
                        : "border-surface-200",
                    )}
                  >
                    <span
                      className={clsx(
                        "flex h-8 w-8 items-center justify-center rounded-full text-[11px] font-semibold text-white",
                        avatarColor(member.name),
                      )}
                    >
                      {initials(member.name)}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium text-surface-800">
                        {member.name}
                      </span>
                      <span className="block truncate text-xs text-surface-500">
                        {member.email}
                      </span>
                    </span>
                    {conversation.assigneeId === member.userId ? (
                      <CheckIcon className="h-4 w-4 text-brand-600" />
                    ) : null}
                  </button>
                </li>
              ))
            )}
          </ul>
        )}
      </Modal>

      {/* team modal */}
      <Modal
        open={teamOpen}
        onClose={() => setTeamOpen(false)}
        title="Assign team"
        description="Route this conversation to a team."
        footer={
          <>
            <Button variant="ghost" onClick={() => setTeamOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="secondary"
              onClick={async () => {
                const ok = await mutate(
                  `/conversations/${conversationId}/team`,
                  { teamId: null },
                  "Team removed",
                );
                if (ok) setTeamOpen(false);
              }}
            >
              Remove team
            </Button>
          </>
        }
      >
        {rosterLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
          </div>
        ) : (
          <ul className="space-y-1.5">
            {teams.length === 0 ? (
              <li className="rounded-lg border border-dashed border-surface-300 bg-surface-50 px-4 py-6 text-center text-sm text-surface-500">
                No teams yet. Create one in Settings.
              </li>
            ) : (
              teams.map((team) => (
                <li key={team.id}>
                  <button
                    type="button"
                    onClick={async () => {
                      const ok = await mutate(
                        `/conversations/${conversationId}/team`,
                        { teamId: team.id },
                        `Assigned to ${team.name}`,
                      );
                      if (ok) setTeamOpen(false);
                    }}
                    className={clsx(
                      "flex w-full items-center gap-3 rounded-lg border px-3 py-2.5 text-left transition-colors hover:bg-surface-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500",
                      conversation.teamId === team.id
                        ? "border-brand-300 bg-brand-50"
                        : "border-surface-200",
                    )}
                  >
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium text-surface-800">
                        {team.name}
                      </span>
                      {team.description ? (
                        <span className="block truncate text-xs text-surface-500">
                          {team.description}
                        </span>
                      ) : null}
                    </span>
                    {conversation.teamId === team.id ? (
                      <CheckIcon className="h-4 w-4 text-brand-600" />
                    ) : null}
                  </button>
                </li>
              ))
            )}
          </ul>
        )}
      </Modal>
    </div>
  );
}

function MessagePlaceholder() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M20 14a2 2 0 0 1-2 2H8l-4 3.5V6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v8Z" />
    </svg>
  );
}
