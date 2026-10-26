"use client";

import clsx from "clsx";
import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { useParams } from "next/navigation";

import {
  ArrowLeftIcon,
  ArrowRightIcon,
  BotIcon,
  CloseIcon,
  FileIcon,
  MegaphoneIcon,
  MenuIcon,
  SearchIcon,
  SendIcon,
  SparklesIcon,
} from "@/components/icons";
import { Spinner } from "@/components/ui";
import { apiFetch, apiUrl } from "@/lib/api";
import { formatDate, formatTime } from "@/lib/format";
import type {
  Citation,
  WidgetConfig,
  WidgetContent,
  WidgetContentKind,
  WidgetConversation,
  WidgetMessage,
  WidgetSendResponse,
  WidgetSessionResponse,
} from "@/lib/types";

const VISITOR_KEY_PREFIX = "rd.visitor.";
const PROFILE_KEY_PREFIX = "rd.visitor.profile.";

function readStorage(key: string): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(key, value);
  } catch {
    /* ignore */
  }
}

interface BubbleMessage {
  id: string;
  authorType: "CONTACT" | "AGENT" | "AI";
  body: string;
  createdAt: string;
  citations?: Citation[];
}

function toBubble(message: WidgetMessage): BubbleMessage {
  return {
    id: message.id,
    authorType: message.authorType,
    body: message.body,
    createdAt: message.createdAt,
    citations: message.citations,
  };
}

/**
 * Replaces the transcript with the server snapshot while keeping messages that
 * are still being sent. This makes live refreshes idempotent even if a response
 * arrives while the visitor's own message is in flight.
 */
function mergeSnapshot(current: BubbleMessage[], incoming: WidgetMessage[]): BubbleMessage[] {
  const server = incoming.map(toBubble);
  const optimistic = current.filter(
    (message) =>
      message.id.startsWith("local-") &&
      !server.some(
        (item) => item.authorType === message.authorType && item.body === message.body,
      ),
  );
  return [...server, ...optimistic];
}

type WidgetView = "chat" | "help" | "article";
type HelpKind = WidgetContentKind | "ALL";

const HELP_TABS: Array<{ value: HelpKind; label: string }> = [
  { value: "ALL", label: "All" },
  { value: "FAQ", label: "FAQs" },
  { value: "ANNOUNCEMENT", label: "News" },
  { value: "BLOG", label: "Blogs" },
];

function contentKindLabel(kind: WidgetContentKind): string {
  if (kind === "BLOG") return "Blog";
  if (kind === "ANNOUNCEMENT") return "News";
  return "FAQ";
}

function ContentKindIcon({
  kind,
  className,
}: {
  kind: WidgetContentKind;
  className?: string;
}) {
  if (kind === "BLOG") return <FileIcon className={className} />;
  if (kind === "ANNOUNCEMENT") return <MegaphoneIcon className={className} />;
  return <SearchIcon className={className} />;
}

export default function WidgetPage() {
  const params = useParams<{ appId: string }>();
  const rawAppId = params?.appId;
  const appId = Array.isArray(rawAppId) ? rawAppId[0] : (rawAppId ?? "");

  const [config, setConfig] = useState<WidgetConfig | null>(null);
  const [configError, setConfigError] = useState<string | null>(null);
  const [session, setSession] = useState<WidgetSessionResponse | null>(null);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<BubbleMessage[]>([]);
  const [visitorId, setVisitorId] = useState<string | null>(null);

  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [started, setStarted] = useState(false);
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [awaitingAi, setAwaitingAi] = useState(false);
  const [open, setOpen] = useState(false);
  const [view, setView] = useState<WidgetView>("chat");
  const [content, setContent] = useState<WidgetContent[] | null>(null);
  const [contentLoading, setContentLoading] = useState(false);
  const [contentError, setContentError] = useState<string | null>(null);
  const [helpQuery, setHelpQuery] = useState("");
  const [helpKind, setHelpKind] = useState<HelpKind>("ALL");
  const [selectedContent, setSelectedContent] = useState<WidgetContent | null>(null);
  // `null` until we know whether the page is inside an iframe. Rendering the
  // standalone backdrop before that check is what used to flash a white box
  // inside embedded widgets.
  const [embedded, setEmbedded] = useState<boolean | null>(null);

  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const refreshSeq = useRef(0);

  // Detect iframe embedding so the page can render as a bare widget.
  useEffect(() => {
    let isEmbedded = false;
    try {
      isEmbedded = window.self !== window.top;
    } catch {
      isEmbedded = true;
    }
    setEmbedded(isEmbedded);
  }, []);

  /* ------------------------------ bootstrap ------------------------------ */
  useEffect(() => {
    if (!appId) return;
    let cancelled = false;
    (async () => {
      try {
        const data = await apiFetch<WidgetConfig>(
          `/widget/${encodeURIComponent(appId)}/config`,
          { auth: false },
        );
        if (!cancelled) setConfig(data);
      } catch (err) {
        if (!cancelled) {
          setConfigError(
            err instanceof Error ? err.message : "Could not load the widget configuration.",
          );
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [appId]);

  // restore visitor identity
  useEffect(() => {
    if (!appId) return;
    const storedVisitor = readStorage(`${VISITOR_KEY_PREFIX}${appId}`);
    const profile = readStorage(`${PROFILE_KEY_PREFIX}${appId}`);
    if (storedVisitor) {
      setVisitorId(storedVisitor);
      // Returning visitor — resume the conversation without the pre-chat form.
      setStarted(true);
    }
    if (profile) {
      try {
        const parsed = JSON.parse(profile) as { name?: string; email?: string };
        if (parsed.name) setName(parsed.name);
        if (parsed.email) setEmail(parsed.email);
        setStarted(true);
      } catch {
        /* ignore malformed profile */
      }
    }
  }, [appId]);

  // create / restore session once we have an identity
  useEffect(() => {
    if (!appId || !started || session) return;
    let cancelled = false;
    (async () => {
      try {
        const data = await apiFetch<WidgetSessionResponse>(
          `/widget/${encodeURIComponent(appId)}/session`,
          {
            method: "POST",
            auth: false,
            json: {
              visitorId: visitorId ?? undefined,
              name: name.trim() || undefined,
              email: email.trim() || undefined,
            },
          },
        );
        if (cancelled) return;
        writeStorage(`${VISITOR_KEY_PREFIX}${appId}`, data.visitorId);
        writeStorage(
          `${PROFILE_KEY_PREFIX}${appId}`,
          JSON.stringify({ name: data.name ?? name, email: data.email ?? email }),
        );
        setSession(data);
        setVisitorId(data.visitorId);

        const existing = data.conversations?.[0] as WidgetConversation | undefined;
        if (existing) {
          setConversationId(existing.id);
          setMessages(
            (existing.messages ?? []).map((message) => ({
              id: message.id,
              authorType: message.authorType,
              body: message.body,
              createdAt: message.createdAt,
              citations: message.citations,
            })),
          );
        }
      } catch (err) {
        if (!cancelled) {
          setConfigError(
            err instanceof Error ? err.message : "Could not start the chat session.",
          );
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [appId, started, session, visitorId, name, email]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [messages.length, awaitingAi, open]);

  const refreshConversation = useCallback(async () => {
    if (!appId || !conversationId || !visitorId) return;
    const seq = ++refreshSeq.current;
    try {
      const data = await apiFetch<WidgetConversation>(
        `/widget/${encodeURIComponent(appId)}/conversations/${encodeURIComponent(conversationId)}`,
        { auth: false, params: { visitorId } },
      );
      if (seq !== refreshSeq.current) return;
      setMessages((current) => mergeSnapshot(current, data.messages ?? []));
    } catch {
      // A dropped refresh is harmless: the next live signal or reconnect
      // performs a full snapshot again.
    }
  }, [appId, conversationId, visitorId]);

  const loadContent = useCallback(async () => {
    if (!appId) return;
    setContentLoading(true);
    setContentError(null);
    try {
      const data = await apiFetch<WidgetContent[]>(
        `/widget/${encodeURIComponent(appId)}/content`,
        { auth: false },
      );
      setContent(data ?? []);
    } catch (err) {
      setContentError(
        err instanceof Error ? err.message : "Could not load the help center.",
      );
    } finally {
      setContentLoading(false);
    }
  }, [appId]);

  useEffect(() => {
    if (!open || content !== null) return;
    void loadContent();
  }, [open, content, loadContent]);

  // Live agent replies while the panel is open. EventSource reconnects on its
  // own; every successful (re)connect first re-reads the transcript so messages
  // sent during a brief disconnect are not lost.
  useEffect(() => {
    if (!open || !appId || !conversationId || !visitorId) return;
    const url = apiUrl(
      `/widget/${encodeURIComponent(appId)}/conversations/${encodeURIComponent(conversationId)}/events`,
      { visitorId },
    );
    const source = new EventSource(url);
    const onMessage = (event: MessageEvent) => {
      try {
        const payload = JSON.parse(String(event.data)) as { type?: string };
        if (payload.type === "ready") return;
      } catch {
        /* Not JSON: treat it as a generic change signal. */
      }
      void refreshConversation();
    };
    source.addEventListener("message", onMessage);
    source.onopen = () => {
      void refreshConversation();
    };
    return () => {
      source.removeEventListener("message", onMessage);
      source.close();
    };
  }, [open, appId, conversationId, visitorId, refreshConversation]);

  const startChat = useCallback(
    (event: FormEvent) => {
      event.preventDefault();
      writeStorage(
        `${PROFILE_KEY_PREFIX}${appId}`,
        JSON.stringify({ name: name.trim(), email: email.trim() }),
      );
      setStarted(true);
      window.setTimeout(() => inputRef.current?.focus(), 100);
    },
    [appId, name, email],
  );

  const openHelp = useCallback(() => {
    setView("help");
    setSelectedContent(null);
    if (content === null && !contentLoading) {
      void loadContent();
    }
  }, [content, contentLoading, loadContent]);

  const openArticle = useCallback((item: WidgetContent) => {
    setSelectedContent(item);
    setView("article");
  }, []);

  const backToChat = useCallback(() => {
    setView("chat");
    setSelectedContent(null);
  }, []);

  const backToHelp = useCallback(() => {
    setView("help");
    setSelectedContent(null);
  }, []);

  const closeWidget = useCallback(() => {
    setOpen(false);
    setView("chat");
    setSelectedContent(null);
  }, []);

  async function sendMessage(event: FormEvent) {
    event.preventDefault();
    const body = draft.trim();
    if (!body || sending || !appId) return;
    setDraft("");
    setSending(true);

    const optimistic: BubbleMessage = {
      id: `local-${Date.now()}`,
      authorType: "CONTACT",
      body,
      createdAt: new Date().toISOString(),
    };
    setMessages((current) => [...current, optimistic]);

    try {
      const response = await apiFetch<WidgetSendResponse>(
        `/widget/${encodeURIComponent(appId)}/messages`,
        {
          method: "POST",
          auth: false,
          json: {
            visitorId: visitorId ?? "",
            conversationId: conversationId ?? undefined,
            body,
            name: name.trim() || undefined,
            email: email.trim() || undefined,
          },
        },
      );

      setConversationId(response.conversationId);
      setMessages((current) => {
        const withoutOptimistic = current.filter((message) => message.id !== optimistic.id);
        if (withoutOptimistic.some((message) => message.id === response.message.id)) {
          return withoutOptimistic;
        }
        return [...withoutOptimistic, toBubble(response.message)];
      });

      if (response.aiReply) {
        setAwaitingAi(true);
        await new Promise((resolve) => window.setTimeout(resolve, 650));
        const reply = response.aiReply as WidgetMessage;
        setMessages((current) =>
          current.some((message) => message.id === reply.id)
            ? current
            : [...current, toBubble(reply)],
        );
        setAwaitingAi(false);
      }
    } catch (err) {
      setMessages((current) => current.filter((message) => message.id !== optimistic.id));
      setDraft(body);
      setConfigError(
        err instanceof Error ? err.message : "Your message could not be sent.",
      );
    } finally {
      setSending(false);
    }
  }

  const accent = config?.color ?? "#4f46e5";
  const displayName = config?.workspaceName ?? "Support";

  return (
    <div
      className={clsx(
        "min-h-screen",
        embedded === false ? "bg-surface-100" : "bg-transparent",
      )}
    >
      {/* The widget is embedded in an iframe on customer sites. Keep the
          document transparent so no box is painted around the launcher. */}
      <style>{`html,body{background-color:transparent!important}`}</style>

      {embedded === false ? (
        <p className="pointer-events-none fixed bottom-6 left-6 text-[11px] text-surface-400">
          Messenger preview
        </p>
      ) : null}

      {/* floating launcher + panel */}
      <div className="fixed bottom-5 right-5 z-50 flex flex-col items-end gap-3">
        {open ? (
          <section
            aria-label="Support chat"
            className="flex h-[600px] max-h-[calc(100vh-6rem)] w-[380px] max-w-[calc(100vw-2.5rem)] flex-col overflow-hidden rounded-2xl border border-surface-200 bg-white shadow-lift animate-fade-up"
          >
            <header
              className="flex items-start justify-between gap-2 px-4 py-3 text-white"
              style={{ backgroundColor: accent }}
            >
              <div className="flex min-w-0 items-start gap-2">
                {view !== "chat" ? (
                  <button
                    type="button"
                    onClick={view === "article" ? backToHelp : backToChat}
                    aria-label={view === "article" ? "Back to help center" : "Back to chat"}
                    title={view === "article" ? "Back to help center" : "Back to chat"}
                    className="mt-0.5 shrink-0 rounded-lg p-1.5 text-white/80 transition-colors hover:bg-white/15 hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
                  >
                    <ArrowLeftIcon className="h-4 w-4" />
                  </button>
                ) : null}
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold">
                    {view === "chat"
                      ? (config?.greeting ?? displayName)
                      : view === "help"
                        ? "Help center"
                        : (selectedContent?.title ?? "Help center")}
                  </p>
                  <p className="mt-0.5 truncate text-[11px] opacity-90">
                    {view === "chat"
                      ? (config?.subtitle ?? "We typically reply in a few minutes.")
                      : view === "help"
                        ? "Blogs, FAQs and news"
                        : selectedContent
                          ? contentKindLabel(selectedContent.kind)
                          : ""}
                  </p>
                </div>
              </div>
              <div className="flex shrink-0 items-center gap-1">
                {view === "chat" ? (
                  <button
                    type="button"
                    onClick={openHelp}
                    aria-label="Open help center"
                    title="Help center"
                    className="rounded-lg p-1.5 text-white/80 transition-colors hover:bg-white/15 hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
                  >
                    <MenuIcon className="h-4 w-4" />
                  </button>
                ) : null}
                <button
                  type="button"
                  onClick={closeWidget}
                  aria-label="Minimise chat"
                  className="rounded-lg p-1.5 text-white/80 transition-colors hover:bg-white/15 hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
                >
                  <CloseIcon className="h-4 w-4" />
                </button>
              </div>
            </header>

            {configError ? (
              <p className="border-b border-rose-200 bg-rose-50 px-3 py-2 text-[11.5px] text-rose-700">
                {configError}
              </p>
            ) : null}

            {view === "chat" ? (
              <>
                <div className="min-h-0 flex-1 space-y-3 overflow-y-auto bg-surface-50 px-3 py-4">
                  {config?.introMessage ? (
                    <div className="space-y-1.5">
                      <div className="max-w-[85%] rounded-2xl rounded-tl-sm bg-white px-3.5 py-2.5 text-sm leading-relaxed text-surface-700 shadow-sm">
                        {config.introMessage}
                      </div>
                      <button
                        type="button"
                        onClick={openHelp}
                        className="inline-flex items-center gap-1 rounded-full border border-surface-200 bg-white px-2.5 py-1 text-[10.5px] font-medium text-surface-500 shadow-sm transition-colors hover:border-surface-300 hover:text-surface-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
                      >
                        <MenuIcon className="h-3 w-3" /> Browse help
                      </button>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={openHelp}
                      className="inline-flex items-center gap-1 rounded-full border border-surface-200 bg-white px-2.5 py-1 text-[10.5px] font-medium text-surface-500 shadow-sm transition-colors hover:border-surface-300 hover:text-surface-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
                    >
                      <MenuIcon className="h-3 w-3" /> Browse help
                    </button>
                  )}

                  {messages.map((message) => {
                const isContact = message.authorType === "CONTACT";
                return (
                  <div
                    key={message.id}
                    className={clsx(
                      "flex",
                      isContact ? "justify-end" : "justify-start",
                    )}
                  >
                    <div
                      className={clsx(
                        "max-w-[82%] space-y-1.5",
                        isContact ? "items-end" : "items-start",
                      )}
                    >
                      {!isContact ? (
                        <span className="flex items-center gap-1.5 text-[11px] font-medium text-surface-500">
                          <span
                            className="flex h-4 w-4 items-center justify-center rounded-full text-white"
                            style={{ backgroundColor: accent }}
                          >
                            <SparklesIcon className="h-2.5 w-2.5" />
                          </span>
                          {message.authorType === "AI" ? "Assistant" : "Support"}
                        </span>
                      ) : null}

                      <div
                        className={clsx(
                          "whitespace-pre-wrap break-words rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed shadow-sm",
                          isContact
                            ? "rounded-br-sm bg-brand-600 text-white"
                            : "rounded-tl-sm bg-white text-surface-700",
                        )}
                      >
                        {message.body}
                      </div>

                      {message.citations && message.citations.length > 0 ? (
                        <div className="space-y-1">
                          {message.citations.map((citation, index) => (
                            <CitationChip
                              key={`${message.id}-${index}`}
                              citation={citation}
                            />
                          ))}
                        </div>
                      ) : null}

                      <span className="block text-[10px] text-surface-400">
                        {formatTime(message.createdAt)}
                      </span>
                    </div>
                  </div>
                );
              })}

              {awaitingAi ? (
                <div className="flex items-center gap-2">
                  <span
                    className="flex h-5 w-5 items-center justify-center rounded-full text-white"
                    style={{ backgroundColor: accent }}
                  >
                    <BotIcon className="h-3 w-3" />
                  </span>
                  <span className="flex items-center gap-1 rounded-2xl bg-white px-3 py-2.5 shadow-sm">
                    {[0, 1, 2].map((index) => (
                      <span
                        key={index}
                        className="h-1.5 w-1.5 animate-typing-dot rounded-full bg-surface-400"
                        style={{ animationDelay: `${index * 0.15}s` }}
                      />
                    ))}
                    <span className="sr-only">Assistant is typing</span>
                  </span>
                </div>
              ) : null}

              <div ref={bottomRef} />
            </div>

            {!started ? (
              <form onSubmit={startChat} className="space-y-2 border-t border-surface-100 p-3">
                <p className="text-xs text-surface-500">
                  Tell us who you are (optional) so we can help faster.
                </p>
                <input
                  type="text"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  placeholder="Your name"
                  aria-label="Your name"
                  className="w-full rounded-lg border border-surface-300 px-3 py-2 text-sm text-surface-900 placeholder:text-surface-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30"
                />
                <input
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="you@example.com"
                  aria-label="Your email"
                  className="w-full rounded-lg border border-surface-300 px-3 py-2 text-sm text-surface-900 placeholder:text-surface-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30"
                />
                <button
                  type="submit"
                  className="w-full rounded-lg px-4 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90"
                  style={{ backgroundColor: accent }}
                >
                  Start chat
                </button>
              </form>
            ) : (
              <form onSubmit={sendMessage} className="border-t border-surface-100 p-3">
                <div className="flex items-end gap-2">
                  <textarea
                    ref={inputRef}
                    value={draft}
                    onChange={(event) => setDraft(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" && !event.shiftKey) {
                        event.preventDefault();
                        void sendMessage(event);
                      }
                    }}
                    rows={2}
                    placeholder={config?.inputPlaceholder ?? "Send a message…"}
                    aria-label="Message"
                    className="max-h-28 min-h-[2.75rem] flex-1 resize-none rounded-lg border border-surface-300 px-3 py-2 text-sm text-surface-900 placeholder:text-surface-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30"
                  />
                  <button
                    type="submit"
                    disabled={!draft.trim() || sending}
                    aria-label="Send message"
                    className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg text-white transition-opacity disabled:opacity-40"
                    style={{ backgroundColor: accent }}
                  >
                    {sending ? <Spinner className="h-4 w-4" /> : <SendIcon className="h-4 w-4" />}
                  </button>
                </div>
              </form>
            )}
              </>
            ) : view === "help" ? (
              <HelpCenterView
                items={content ?? []}
                loading={contentLoading}
                error={contentError}
                query={helpQuery}
                onQueryChange={setHelpQuery}
                kind={helpKind}
                onKindChange={setHelpKind}
                onSelect={openArticle}
                onStartChat={backToChat}
                onRetry={() => void loadContent()}
                accent={accent}
              />
            ) : selectedContent ? (
              <ArticleView item={selectedContent} accent={accent} onStartChat={backToChat} />
            ) : null}

            {config?.showBranding ? (
              <p className="pb-2 text-center text-[10px] text-surface-400">
                Powered by Monotron
              </p>
            ) : null}
          </section>
        ) : null}

        <button
          type="button"
          onClick={() => {
            if (open) {
              closeWidget();
              return;
            }
            setView("chat");
            setSelectedContent(null);
            setOpen(true);
          }}
          aria-label={open ? "Close chat" : "Open chat"}
          aria-expanded={open}
          className="flex h-14 w-14 items-center justify-center rounded-full text-white shadow-lift transition-transform hover:scale-105 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
          style={{ backgroundColor: accent }}
        >
          {open ? (
            <CloseIcon className="h-6 w-6" />
          ) : (
            <MessageBubbleIcon />
          )}
        </button>
      </div>
    </div>
  );
}

function HelpCenterView({
  items,
  loading,
  error,
  query,
  onQueryChange,
  kind,
  onKindChange,
  onSelect,
  onStartChat,
  onRetry,
  accent,
}: {
  items: WidgetContent[];
  loading: boolean;
  error: string | null;
  query: string;
  onQueryChange: (value: string) => void;
  kind: HelpKind;
  onKindChange: (value: HelpKind) => void;
  onSelect: (item: WidgetContent) => void;
  onStartChat: () => void;
  onRetry: () => void;
  accent: string;
}) {
  const term = query.trim().toLowerCase();
  const filtered = items.filter((item) => {
    if (kind !== "ALL" && item.kind !== kind) return false;
    if (!term) return true;
    return `${item.title} ${item.summary} ${item.body}`.toLowerCase().includes(term);
  });


  return (
    <div className="min-h-0 flex-1 overflow-y-auto bg-surface-50">
      <div className="space-y-3 px-3 py-4">
        <div className="relative">
          <SearchIcon className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-surface-400" />
          <input
            type="search"
            value={query}
            onChange={(event) => onQueryChange(event.target.value)}
            placeholder="Search for help"
            aria-label="Search help center"
            className="w-full rounded-lg border border-surface-300 bg-white py-2 pl-8 pr-3 text-[12px] text-surface-900 placeholder:text-surface-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30"
          />
        </div>

        <div
          role="tablist"
          aria-label="Help center categories"
          className="flex items-end gap-1 border-b border-surface-200"
        >
          {HELP_TABS.map((tab) => {
            const active = kind === tab.value;
            return (
              <button
                key={tab.value}
                type="button"
                role="tab"
                aria-selected={active}
                onClick={() => onKindChange(tab.value)}
                className={clsx(
                  "relative flex-1 whitespace-nowrap px-1 pb-2 pt-1 text-[11px] font-semibold transition-colors",
                  active ? "text-surface-900" : "text-surface-400 hover:text-surface-600",
                )}
              >
                {tab.label}
                {active ? (
                  <span
                    className="absolute inset-x-1 -bottom-px h-0.5 rounded-full"
                    style={{ backgroundColor: accent }}
                  />
                ) : null}
              </button>
            );
          })}
        </div>

        {loading ? (
          <div className="space-y-2">
            {[0, 1, 2].map((index) => (
              <div
                key={index}
                className="rounded-xl border border-surface-200 bg-white p-3"
              >
                <div className="h-3 w-20 animate-pulse rounded bg-surface-200" />
                <div className="mt-2 h-4 w-3/4 animate-pulse rounded bg-surface-200" />
                <div className="mt-1.5 h-3 w-full animate-pulse rounded bg-surface-100" />
              </div>
            ))}
          </div>
        ) : error ? (
          <div className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-center">
            <p className="text-[11.5px] text-rose-700">{error}</p>
            <button
              type="button"
              onClick={onRetry}
              className="mt-2 rounded-lg border border-rose-200 bg-white px-3 py-1.5 text-[11px] font-semibold text-rose-700 transition-colors hover:bg-rose-100"
            >
              Try again
            </button>
          </div>
        ) : filtered.length === 0 ? (
          <div className="rounded-xl border border-dashed border-surface-300 bg-white/70 px-4 py-8 text-center">
            <MenuIcon className="mx-auto h-5 w-5 text-surface-400" />
            <p className="mt-2 text-[12.5px] font-semibold text-surface-700">
              {items.length === 0 ? "No help content yet" : "No matching help"}
            </p>
            <p className="mt-0.5 text-[11px] leading-relaxed text-surface-500">
              {items.length === 0
                ? "Start a conversation and we'll help you out."
                : "Try another search or browse a different topic."}
            </p>
          </div>
        ) : (
          <div className="space-y-2">
            {filtered.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => onSelect(item)}
                className="group flex w-full items-start gap-3 rounded-xl border border-surface-200 bg-white p-3 text-left shadow-sm transition-colors hover:border-surface-300 hover:bg-surface-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
              >
                <span
                  className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
                  style={{ backgroundColor: `${accent}14`, color: accent }}
                >
                  <ContentKindIcon kind={item.kind} className="h-4 w-4" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-1.5 text-[9px] font-semibold uppercase tracking-[0.12em] text-surface-400">
                    {contentKindLabel(item.kind)}
                    <span className="text-surface-300">·</span>
                    <span className="font-normal normal-case tracking-normal">
                      {formatDate(item.createdAt)}
                    </span>
                  </span>
                  <span className="mt-0.5 block text-[13px] font-semibold leading-snug text-surface-900">
                    {item.title}
                  </span>
                  {item.summary ? (
                    <span className="mt-0.5 line-clamp-2 block text-[11.5px] leading-relaxed text-surface-500">
                      {item.summary}
                    </span>
                  ) : null}
                </span>
                <ArrowRightIcon className="mt-1 h-3.5 w-3.5 shrink-0 text-surface-300 transition-colors group-hover:text-surface-500" />
              </button>
            ))}
          </div>
        )}

        <div className="rounded-xl border border-dashed border-surface-300 bg-white/60 p-3 text-center">
          <p className="text-[11.5px] font-medium text-surface-700">
            Still need a hand?
          </p>
          <p className="mt-0.5 text-[10.5px] text-surface-500">
            Start a conversation and we&apos;ll pick it up.
          </p>
          <button
            type="button"
            onClick={onStartChat}
            className="mt-2 inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-[11.5px] font-semibold text-white transition-opacity hover:opacity-90"
            style={{ backgroundColor: accent }}
          >
            <SendIcon className="h-3.5 w-3.5" /> Start a conversation
          </button>
        </div>
      </div>
    </div>
  );
}

function ArticleView({
  item,
  accent,
  onStartChat,
}: {
  item: WidgetContent;
  accent: string;
  onStartChat: () => void;
}) {
  return (
    <div className="min-h-0 flex-1 overflow-y-auto bg-surface-50">
      <div className="space-y-4 px-4 py-4">
        <div className="flex flex-wrap items-center gap-2">
          <span
            className="flex h-7 w-7 items-center justify-center rounded-lg"
            style={{ backgroundColor: `${accent}14`, color: accent }}
          >
            <ContentKindIcon kind={item.kind} className="h-4 w-4" />
          </span>
          <span className="text-[10px] font-semibold uppercase tracking-[0.12em] text-surface-500">
            {contentKindLabel(item.kind)}
          </span>
          <span className="text-[10px] text-surface-400">
            · {formatDate(item.updatedAt)}
          </span>
        </div>

        <div>
          <h3 className="text-[17px] font-semibold leading-snug text-surface-900">
            {item.title}
          </h3>
          {item.summary ? (
            <p className="mt-1.5 text-[12.5px] leading-relaxed text-surface-500">
              {item.summary}
            </p>
          ) : null}
        </div>

        <div className="whitespace-pre-wrap rounded-xl border border-surface-200 bg-white p-3.5 text-[12.5px] leading-relaxed text-surface-700 shadow-sm">
          {item.body}
        </div>

        <button
          type="button"
          onClick={onStartChat}
          className="inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-[11.5px] font-semibold text-white transition-opacity hover:opacity-90"
          style={{ backgroundColor: accent }}
        >
          <SendIcon className="h-3.5 w-3.5" /> Start a conversation
        </button>
      </div>
    </div>
  );
}

function CitationChip({ citation }: { citation: Citation }) {
  const content = (
    <>
      <p className="truncate text-[11px] font-semibold text-surface-700">
        {citation.title || "Source"}
      </p>
      <p className="text-[10px] uppercase tracking-wide text-surface-400">
        {citation.sourceType}
        {citation.score !== undefined ? ` · ${citation.score.toFixed(2)}` : ""}
      </p>
    </>
  );

  const className = "block rounded-lg border border-surface-200 bg-white px-2.5 py-1.5";

  if (citation.url) {
    return (
      <a
        href={citation.url}
        target="_blank"
        rel="noreferrer"
        className={`${className} transition-colors hover:bg-surface-50`}
      >
        {content}
      </a>
    );
  }

  return <div className={className}>{content}</div>;
}

function MessageBubbleIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-6 w-6"
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
