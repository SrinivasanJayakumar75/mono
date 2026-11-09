"use client";

import clsx from "clsx";
import { useCallback, useEffect, useState } from "react";

import {
  CheckCircleIcon,
  ClockIcon,
  GlobeIcon,
  MailIcon,
  MessageIcon,
  RefreshIcon,
  ShieldIcon,
  SparklesIcon,
} from "@/components/icons";
import {
  Badge,
  Button,
  Card,
  ErrorBanner,
  Input,
  Skeleton,
  ToggleSwitch,
  useToast,
} from "@/components/ui";
import { apiFetch } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatRelative } from "@/lib/format";
import type {
  AutomationChannel,
  ChannelAutomation,
  ChannelConnection,
  ChannelOAuthStart,
} from "@/lib/types";

type ChannelMeta = {
  label: string;
  handleLabel: string;
  placeholder: string;
  description: string;
  icon: typeof MailIcon;
  iconTone: string;
};

const CHANNEL_META: Record<AutomationChannel, ChannelMeta> = {
  GMAIL: {
    label: "Gmail",
    handleLabel: "Support mailbox",
    placeholder: "support@yourcompany.com",
    description:
      "Turn customer emails into inbox conversations and let AI draft grounded replies.",
    icon: MailIcon,
    iconTone: "bg-rose-50 text-rose-600 ring-rose-200",
  },
  WHATSAPP: {
    label: "WhatsApp",
    handleLabel: "WhatsApp business number",
    placeholder: "+91 90000 00000",
    description:
      "Answer quick questions on WhatsApp with the same knowledge base and team handoff.",
    icon: MessageIcon,
    iconTone: "bg-emerald-50 text-emerald-600 ring-emerald-200",
  },
  INSTAGRAM: {
    label: "Instagram",
    handleLabel: "Instagram handle",
    placeholder: "@yourcompany",
    description:
      "Catch DMs, answer common questions and route high-intent conversations to your team.",
    icon: GlobeIcon,
    iconTone: "bg-fuchsia-50 text-fuchsia-600 ring-fuchsia-200",
  },
};

const CHANNELS: AutomationChannel[] = ["GMAIL", "WHATSAPP", "INSTAGRAM"];

export default function ChannelsPage() {
  const { toast } = useToast();
  const { workspace } = useAuth();
  const canManage = workspace?.role !== "VIEWER";

  const [items, setItems] = useState<ChannelAutomation[]>([]);
  const [connections, setConnections] = useState<ChannelConnection[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState<Partial<Record<AutomationChannel, boolean>>>({});
  const [connecting, setConnecting] =
    useState<Partial<Record<AutomationChannel, boolean>>>({});
  const [disconnecting, setDisconnecting] =
    useState<Partial<Record<AutomationChannel, boolean>>>({});
  const [syncing, setSyncing] = useState<Partial<Record<AutomationChannel, boolean>>>({});

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [automationData, connectionData] = await Promise.all([
        apiFetch<ChannelAutomation[]>("/channel-automations"),
        apiFetch<ChannelConnection[]>("/channel-connections"),
      ]);
      setItems(automationData ?? []);
      setConnections(connectionData ?? []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load channel automation.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (typeof window === "undefined") return;
    const params = new URLSearchParams(window.location.search);
    const connected = params.get("connected");
    const oauthError = params.get("error");
    if (!connected && !oauthError) return;
    if (connected) {
      const label =
        CHANNEL_META[connected as AutomationChannel]?.label ?? connected;
      toast(`${label} connected`, { tone: "success" });
    }
    if (oauthError) {
      toast("Could not connect channel", {
        tone: "error",
        description: oauthError,
      });
    }
    params.delete("connected");
    params.delete("error");
    const query = params.toString();
    window.history.replaceState({}, "", `/channels${query ? `?${query}` : ""}`);
    void load();
  }, [load, toast]);

  function update(channel: AutomationChannel, patch: Partial<ChannelAutomation>) {
    setItems((current) =>
      current.map((item) => (item.channel === channel ? { ...item, ...patch } : item)),
    );
  }

  async function save(item: ChannelAutomation) {
    const meta = CHANNEL_META[item.channel];
    setSaving((current) => ({ ...current, [item.channel]: true }));
    try {
      const updated = await apiFetch<ChannelAutomation>(
        `/channel-automations/${encodeURIComponent(item.channel)}`,
        {
          method: "PUT",
          json: {
            accountLabel: item.accountLabel,
            enabled: item.enabled,
            autoReply: item.autoReply,
            handoffToHuman: item.handoffToHuman,
          },
        },
      );
      setItems((current) =>
        current.map((currentItem) =>
          currentItem.channel === updated.channel ? updated : currentItem,
        ),
      );
      toast(`${meta.label} automation saved`, { tone: "success" });
    } catch (err) {
      toast(`Could not save ${meta.label} automation`, {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    } finally {
      setSaving((current) => ({ ...current, [item.channel]: false }));
    }
  }

  async function connect(channel: AutomationChannel) {
    const meta = CHANNEL_META[channel];
    setConnecting((current) => ({ ...current, [channel]: true }));
    try {
      const data = await apiFetch<ChannelOAuthStart>(
        `/channel-connections/${encodeURIComponent(channel)}/oauth/start`,
      );
      window.location.assign(data.authorizationUrl);
    } catch (err) {
      toast(`Could not start ${meta.label} connection`, {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
      setConnecting((current) => ({ ...current, [channel]: false }));
    }
  }

  async function disconnect(channel: AutomationChannel) {
    const meta = CHANNEL_META[channel];
    if (!window.confirm(`Disconnect ${meta.label} from this workspace?`)) return;
    setDisconnecting((current) => ({ ...current, [channel]: true }));
    try {
      await apiFetch(`/channel-connections/${encodeURIComponent(channel)}`, {
        method: "DELETE",
      });
      setConnections((current) =>
        current.map((connection) =>
          connection.channel === channel
            ? { ...connection, connected: false, accountLabel: "", externalAccountId: null }
            : connection,
        ),
      );
      toast(`${meta.label} disconnected`, { tone: "success" });
    } catch (err) {
      toast(`Could not disconnect ${meta.label}`, {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    } finally {
      setDisconnecting((current) => ({ ...current, [channel]: false }));
    }
  }

  async function syncNow(channel: AutomationChannel) {
    const meta = CHANNEL_META[channel];
    setSyncing((current) => ({ ...current, [channel]: true }));
    try {
      const result = await apiFetch<{ processed: number }>(
        `/channel-connections/${encodeURIComponent(channel)}/sync`,
        { method: "POST" },
      );
      toast(
        result.processed === 0
          ? `No new ${meta.label} messages`
          : `Imported ${result.processed} new ${meta.label} message${
              result.processed === 1 ? "" : "s"
            }`,
        { tone: "success" },
      );
      await load();
    } catch (err) {
      toast(`Could not check ${meta.label}`, {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    } finally {
      setSyncing((current) => ({ ...current, [channel]: false }));
    }
  }

  const activeCount = items.filter((item) => item.enabled).length;
  const autoReplyCount = items.filter((item) => item.enabled && item.autoReply).length;

  return (
    <div className="space-y-6">
      <section className="overflow-hidden rounded-card border border-surface-200 bg-gradient-to-br from-brand-950 via-brand-900 to-brand-800 p-6 text-white shadow-card sm:p-8">
        <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-2xl">
            <span className="inline-flex items-center gap-1.5 rounded-md bg-white/10 px-2 py-1 text-[10.5px] font-medium uppercase tracking-[0.14em] text-brand-100 ring-1 ring-inset ring-white/15">
              <SparklesIcon className="h-3 w-3" />
              Channel automation
            </span>
            <h1 className="mt-3 text-2xl font-semibold tracking-[-0.03em] sm:text-3xl">
              One AI inbox for Gmail, WhatsApp and Instagram
            </h1>
            <p className="mt-2 max-w-xl text-sm leading-relaxed text-brand-100/90">
              Meet customers where they already message you. Monotron unifies those
              conversations with your website chat, answers from your knowledge base and
              hands the important ones to your team.
            </p>
          </div>

          <div className="grid shrink-0 grid-cols-2 gap-2 sm:gap-3">
            <div className="rounded-lg bg-white/10 px-4 py-3 ring-1 ring-inset ring-white/15">
              <p className="text-[10px] font-medium uppercase tracking-[0.12em] text-brand-200">
                Active channels
              </p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">
                {activeCount}
                <span className="text-sm font-normal text-brand-200"> / 3</span>
              </p>
            </div>
            <div className="rounded-lg bg-white/10 px-4 py-3 ring-1 ring-inset ring-white/15">
              <p className="text-[10px] font-medium uppercase tracking-[0.12em] text-brand-200">
                Auto-replying
              </p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">{autoReplyCount}</p>
            </div>
          </div>
        </div>

        <div className="mt-6 grid gap-2 sm:grid-cols-3">
          {[
            ["Grounded answers", "The AI uses your docs, FAQs and help content."],
            ["One shared inbox", "Every channel lands with the same team and CRM."],
            ["Human when needed", "Escalate sensitive or uncertain conversations."],
          ].map(([title, body]) => (
            <div
              key={title}
              className="rounded-lg border border-white/10 bg-white/[0.06] px-3.5 py-3"
            >
              <p className="text-xs font-semibold text-white">{title}</p>
              <p className="mt-0.5 text-[11px] leading-relaxed text-brand-100/80">{body}</p>
            </div>
          ))}
        </div>
      </section>

      {error ? <ErrorBanner message={error} onRetry={() => void load()} /> : null}

      <div className="flex items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-semibold text-surface-900">Connected channels</h2>
          <p className="mt-0.5 text-xs text-surface-500">
            Configure the account and automation rules for each channel.
          </p>
        </div>
        <Button
          size="icon"
          variant="ghost"
          onClick={() => void load()}
          aria-label="Refresh channel automation"
        >
          <RefreshIcon className="h-4 w-4" />
        </Button>
      </div>

      {loading && items.length === 0 ? (
        <div className="grid gap-6 xl:grid-cols-3">
          {CHANNELS.map((channel) => (
            <Card key={channel}>
              <div className="space-y-4">
                <Skeleton className="h-10 w-10" />
                <Skeleton className="h-5 w-28" />
                <Skeleton className="h-3 w-full" />
                <Skeleton className="h-9 w-full" />
                <Skeleton className="h-24 w-full" />
                <Skeleton className="h-9 w-full" />
              </div>
            </Card>
          ))}
        </div>
      ) : (
        <div className="grid gap-6 xl:grid-cols-3">
          {CHANNELS.map((channel) => {
            const meta = CHANNEL_META[channel];
            const item = items.find((candidate) => candidate.channel === channel);
            const connection = connections.find(
              (candidate) => candidate.channel === channel,
            );
            if (!item) return null;
            const Icon = meta.icon;
            const gmailScopes = (connection?.scopes ?? "").split(/\s+/);
            const gmailReadMissing =
              channel === "GMAIL" &&
              Boolean(connection?.connected) &&
              !gmailScopes.includes("https://www.googleapis.com/auth/gmail.readonly");
            const gmailSendMissing =
              channel === "GMAIL" &&
              Boolean(connection?.connected) &&
              item.autoReply &&
              !gmailScopes.includes("https://www.googleapis.com/auth/gmail.send");
            const gmailSetupIncomplete = gmailReadMissing || gmailSendMissing;
            return (
              <Card key={channel} bodyClassName="flex h-full flex-col p-5" padded={false}>
                <div className="flex items-start justify-between gap-3">
                  <span
                    className={clsx(
                      "flex h-10 w-10 items-center justify-center rounded-lg ring-1 ring-inset",
                      meta.iconTone,
                    )}
                  >
                    <Icon className="h-5 w-5" />
                  </span>
                  <Badge
                    variant={
                      !connection?.connected
                        ? "warning"
                        : item.enabled
                          ? "success"
                          : "neutral"
                    }
                  >
                    {!connection?.connected
                      ? "Setup required"
                      : item.enabled
                        ? "Active"
                        : "Paused"}
                  </Badge>
                </div>

                <h3 className="mt-4 text-base font-semibold text-surface-900">{meta.label}</h3>
                <p className="mt-1 text-xs leading-relaxed text-surface-500">
                  {meta.description}
                </p>

                <div className="mt-4 rounded-lg border border-surface-200 bg-white p-3.5">
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-xs font-semibold text-surface-900">Connection</p>
                    <Badge variant={connection?.connected ? "success" : "neutral"}>
                      {connection?.connected ? "Connected" : "Not connected"}
                    </Badge>
                  </div>
                  <p className="mt-1.5 text-[11px] leading-relaxed text-surface-500">
                    {connection?.connected
                      ? `Authorized as ${
                          connection.accountLabel ||
                          connection.externalAccountId ||
                          meta.label
                        }. Tokens are encrypted and scoped to this workspace.`
                      : connection?.oauthAvailable
                        ? `Authorize ${meta.label} so Monotron can receive and answer customer messages.`
                        : `Your platform owner must configure the ${meta.label} OAuth app before accounts can connect.`}
                  </p>
                  {gmailSetupIncomplete ? (
                    <div className="mt-3 rounded-md border border-amber-200 bg-amber-50 px-3 py-2.5 text-[11px] leading-relaxed text-amber-800">
                      <p>
                        Gmail needs permission to read your mailbox
                        {item.autoReply ? " and send replies" : ""}. Reconnect and keep
                        those permissions selected before Monotron can import and
                        answer customer emails.
                      </p>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        loading={connecting[channel]}
                        disabled={!canManage}
                        onClick={() => void connect(channel)}
                        className="mt-2 w-full"
                      >
                        Reconnect Gmail
                      </Button>
                    </div>
                  ) : null}
                  <div className="mt-3 flex gap-2">
                    {connection?.connected ? (
                      <>
                        {channel === "GMAIL" ? (
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            loading={syncing[channel]}
                            disabled={!canManage || loading || !item.enabled}
                            onClick={() => void syncNow(channel)}
                            className="flex-1"
                          >
                            Check now
                          </Button>
                        ) : null}
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          loading={disconnecting[channel]}
                          disabled={!canManage}
                          onClick={() => void disconnect(channel)}
                          className="flex-1"
                        >
                          Disconnect {meta.label}
                        </Button>
                      </>
                    ) : (
                      <Button
                        type="button"
                        size="sm"
                        loading={connecting[channel]}
                        disabled={!canManage || !connection?.oauthAvailable || loading}
                        onClick={() => void connect(channel)}
                        className="w-full"
                      >
                        Connect {meta.label}
                      </Button>
                    )}
                  </div>
                </div>

                <fieldset disabled={!canManage} className="mt-5 flex-1 space-y-4">
                  <Input
                    label={meta.handleLabel}
                    value={item.accountLabel}
                    onChange={(event) =>
                      update(channel, { accountLabel: event.target.value })
                    }
                    placeholder={meta.placeholder}
                  />

                  <div className="space-y-3 rounded-lg border border-surface-200 bg-surface-50/60 p-3.5">
                    <ToggleSwitch
                      checked={item.enabled}
                      onChange={(value) => update(channel, { enabled: value })}
                      label="Enable channel"
                      description="Route new conversations into Monotron."
                    />
                    <ToggleSwitch
                      checked={item.autoReply}
                      onChange={(value) => update(channel, { autoReply: value })}
                      label="AI auto-reply"
                      description="Answer automatically using your knowledge base."
                    />
                    <ToggleSwitch
                      checked={item.handoffToHuman}
                      onChange={(value) => update(channel, { handoffToHuman: value })}
                      label="Human handoff"
                      description="Escalate low-confidence or sensitive replies."
                    />
                  </div>

                  <Button
                    type="button"
                    onClick={() => void save(item)}
                    loading={saving[channel]}
                    disabled={!canManage}
                    className="w-full"
                  >
                    Save {meta.label} settings
                  </Button>
                </fieldset>

                <p className="mt-3 flex items-center gap-1.5 text-[10.5px] text-surface-400">
                  <ClockIcon className="h-3 w-3" />
                  {item.updatedAt
                    ? `Updated ${formatRelative(item.updatedAt)}`
                    : "Not configured yet"}
                </p>
              </Card>
            );
          })}
        </div>
      )}

      {!canManage ? (
        <p className="rounded-lg border border-amber-200 bg-amber-50 px-3.5 py-2.5 text-xs text-amber-800">
          You have view-only access. Ask a workspace admin or agent to change channel
          automation.
        </p>
      ) : null}

      <Card
        title="How channel automation works"
        description="The same AI, knowledge and CRM follow the customer across every channel."
      >
        <div className="grid gap-4 md:grid-cols-3">
          {[
            {
              icon: <MailIcon className="h-4 w-4" />,
              title: "1. Bring the conversation in",
              body: "Connect the Gmail mailbox, WhatsApp business number or Instagram handle used by your support team.",
            },
            {
              icon: <SparklesIcon className="h-4 w-4" />,
              title: "2. Let the AI answer",
              body: "Monotron searches your knowledge base and replies with grounded, cited answers in the customer's channel.",
            },
            {
              icon: <ShieldIcon className="h-4 w-4" />,
              title: "3. Hand off with context",
              body: "Agents see the full history in the shared inbox and can take over without asking the customer to repeat themselves.",
            },
          ].map((step) => (
            <div
              key={step.title}
              className="rounded-lg border border-surface-200 bg-surface-50/50 p-4"
            >
              <span className="flex h-8 w-8 items-center justify-center rounded-md bg-white text-brand-700 ring-1 ring-inset ring-surface-200">
                {step.icon}
              </span>
              <p className="mt-3 text-sm font-semibold text-surface-900">{step.title}</p>
              <p className="mt-1 text-xs leading-relaxed text-surface-500">{step.body}</p>
            </div>
          ))}
        </div>

        <div className="mt-4 flex items-start gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3.5 py-2.5 text-xs text-emerald-800">
          <CheckCircleIcon className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          <p>
            Gmail, WhatsApp and Instagram conversations use the same AI, teams and CRM
            records as your website widget. Your workspace administrator connects each
            account with OAuth; the platform owner configures the provider apps.
          </p>
        </div>
      </Card>
    </div>
  );
}
