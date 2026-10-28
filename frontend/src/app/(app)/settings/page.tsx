"use client";

import clsx from "clsx";
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";

import {
  BookIcon,
  BuildingIcon,
  CheckIcon,
  CopyIcon,
  FileIcon,
  LinkIcon,
  MegaphoneIcon,
  PencilIcon,
  PlusIcon,
  RefreshIcon,
  SearchIcon,
  SparklesIcon,
  TrashIcon,
  UsersIcon,
} from "@/components/icons";
import {
  Badge,
  Button,
  Card,
  ErrorBanner,
  Input,
  Modal,
  Select,
  Skeleton,
  Textarea,
  ToggleSwitch,
  useToast,
} from "@/components/ui";
import { apiFetch } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { avatarColor, formatDate, initials } from "@/lib/format";
import type {
  Role,
  Team,
  WidgetContent,
  WidgetContentInput,
  WidgetContentKind,
  WidgetSettings,
  WidgetSettingsUpdate,
  WorkspaceDetail,
  WorkspaceMember,
} from "@/lib/types";

const ROLES: Role[] = ["ADMIN", "AGENT", "VIEWER"];
const CONTENT_KINDS: WidgetContentKind[] = ["FAQ", "BLOG", "ANNOUNCEMENT"];

type ContentDraft = {
  id: string | null;
  kind: WidgetContentKind;
  title: string;
  summary: string;
  body: string;
  published: boolean;
};

function newContentDraft(): ContentDraft {
  return {
    id: null,
    kind: "FAQ",
    title: "",
    summary: "",
    body: "",
    published: true,
  };
}

function contentKindLabel(kind: WidgetContentKind): string {
  if (kind === "BLOG") return "Blog";
  if (kind === "ANNOUNCEMENT") return "News";
  return "FAQ";
}

function contentKindIcon(kind: WidgetContentKind, className?: string) {
  if (kind === "BLOG") return <FileIcon className={className} />;
  if (kind === "ANNOUNCEMENT") return <MegaphoneIcon className={className} />;
  return <SearchIcon className={className} />;
}

function CopyButton({ value, label }: { value: string; label: string }) {
  const { toast } = useToast();
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
    } catch {
      const area = document.createElement("textarea");
      area.value = value;
      area.style.position = "fixed";
      area.style.opacity = "0";
      document.body.appendChild(area);
      area.select();
      document.execCommand("copy");
      document.body.removeChild(area);
    }
    setCopied(true);
    toast(`${label} copied`, { tone: "success" });
    window.setTimeout(() => setCopied(false), 2000);
  }

  return (
    <Button
      size="icon"
      variant="ghost"
      onClick={() => void copy()}
      aria-label={`Copy ${label.toLowerCase()}`}
      title={`Copy ${label.toLowerCase()}`}
    >
      {copied ? (
        <CheckIcon className="h-4 w-4 text-emerald-600" />
      ) : (
        <CopyIcon className="h-4 w-4" />
      )}
    </Button>
  );
}

export default function SettingsPage() {
  const { toast } = useToast();
  const { workspace, refresh: refreshAuth } = useAuth();
  const canManageContent = workspace?.role === "ADMIN" || workspace?.role === "AGENT";

  /* ------------------------------- workspace ------------------------------ */
  const [workspaceDetail, setWorkspaceDetail] = useState<WorkspaceDetail | null>(null);
  const [workspaceLoading, setWorkspaceLoading] = useState(true);
  const [workspaceError, setWorkspaceError] = useState<string | null>(null);
  const [workspaceName, setWorkspaceName] = useState("");
  const [savingWorkspace, setSavingWorkspace] = useState(false);
  const [origin, setOrigin] = useState("");

  useEffect(() => {
    setOrigin(window.location.origin);
  }, []);

  const loadWorkspace = useCallback(async () => {
    setWorkspaceLoading(true);
    setWorkspaceError(null);
    try {
      const data = await apiFetch<WorkspaceDetail>("/workspace");
      setWorkspaceDetail(data);
      setWorkspaceName(data.name);
    } catch (err) {
      setWorkspaceError(err instanceof Error ? err.message : "Could not load workspace.");
    } finally {
      setWorkspaceLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadWorkspace();
  }, [loadWorkspace]);

  async function saveWorkspace(event: FormEvent) {
    event.preventDefault();
    const name = workspaceName.trim();
    if (!name) return;
    setSavingWorkspace(true);
    try {
      const updated = await apiFetch<WorkspaceDetail>("/workspace", {
        method: "PATCH",
        json: { name },
      });
      setWorkspaceDetail(updated);
      setWorkspaceName(updated.name);
      await refreshAuth();
      toast("Workspace updated", { tone: "success" });
    } catch (err) {
      toast("Could not update the workspace", {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    } finally {
      setSavingWorkspace(false);
    }
  }

  const appId = workspaceDetail?.appId ?? workspace?.appId ?? "";
  const embedSnippet = useMemo(() => {
    if (!appId) return "";
    return [
      "<script>",
      "(function(){",
      'var i=document.createElement("iframe");',
      `i.src="${origin}/widget/${appId}";`,
      'i.title="Monotron chat";',
      'i.style.cssText="position:fixed;bottom:0;right:0;width:420px;height:680px;max-width:100vw;border:0;z-index:2147483000;background:transparent";',
      "document.body.appendChild(i);",
      "})();",
      "</script>",
    ].join("");
  }, [appId, origin]);
  const widgetUrl = appId ? `${origin}/widget/${appId}` : "";

  /* -------------------------------- widget -------------------------------- */
  const [widget, setWidget] = useState<WidgetSettings | null>(null);
  const [widgetDraft, setWidgetDraft] = useState<WidgetSettings | null>(null);
  const [widgetLoading, setWidgetLoading] = useState(true);
  const [widgetError, setWidgetError] = useState<string | null>(null);
  const [savingWidget, setSavingWidget] = useState(false);
  const [autoOpenSupported, setAutoOpenSupported] = useState(true);

  const loadWidget = useCallback(async () => {
    setWidgetLoading(true);
    setWidgetError(null);
    try {
      const data = await apiFetch<WidgetSettings>("/widget-settings");
      setAutoOpenSupported(typeof data.autoOpen === "boolean");
      const normalized = { ...data, autoOpen: data.autoOpen ?? true };
      setWidget(normalized);
      setWidgetDraft(normalized);
    } catch (err) {
      setWidgetError(err instanceof Error ? err.message : "Could not load widget settings.");
    } finally {
      setWidgetLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadWidget();
  }, [loadWidget]);

  function updateDraft(patch: Partial<WidgetSettings>) {
    setWidgetDraft((current) => (current ? { ...current, ...patch } : current));
  }

  async function saveWidget(event: FormEvent) {
    event.preventDefault();
    if (!widgetDraft) return;
    setSavingWidget(true);
    try {
      const payload: WidgetSettingsUpdate = {
        workspaceName: widgetDraft.workspaceName,
        greeting: widgetDraft.greeting,
        subtitle: widgetDraft.subtitle,
        color: widgetDraft.color,
        introMessage: widgetDraft.introMessage,
        inputPlaceholder: widgetDraft.inputPlaceholder,
        showBranding: widgetDraft.showBranding,
        ...(autoOpenSupported ? { autoOpen: widgetDraft.autoOpen } : {}),
        aiEnabled: widgetDraft.aiEnabled,
        autoReply: widgetDraft.autoReply,
      };
      const updated = await apiFetch<WidgetSettings>("/widget-settings", {
        method: "PUT",
        json: payload,
      });
      setAutoOpenSupported(typeof updated.autoOpen === "boolean");
      const normalized = { ...updated, autoOpen: updated.autoOpen ?? true };
      setWidget(normalized);
      setWidgetDraft(normalized);
      toast("Widget settings saved", { tone: "success" });
    } catch (err) {
      toast("Could not save widget settings", {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    } finally {
      setSavingWidget(false);
    }
  }

  /* --------------------------- help center content ------------------------ */
  const [contentItems, setContentItems] = useState<WidgetContent[]>([]);
  const [contentLoading, setContentLoading] = useState(true);
  const [contentError, setContentError] = useState<string | null>(null);
  const [contentEditorOpen, setContentEditorOpen] = useState(false);
  const [contentDraft, setContentDraft] = useState<ContentDraft>(newContentDraft);
  const [contentFormError, setContentFormError] = useState<string | null>(null);
  const [savingContent, setSavingContent] = useState(false);
  const [deleteContentTarget, setDeleteContentTarget] = useState<WidgetContent | null>(null);
  const [deletingContent, setDeletingContent] = useState(false);

  const loadContent = useCallback(async () => {
    setContentLoading(true);
    setContentError(null);
    try {
      const data = await apiFetch<WidgetContent[]>("/widget-content");
      setContentItems(data ?? []);
    } catch (err) {
      setContentError(
        err instanceof Error ? err.message : "Could not load help-center content.",
      );
    } finally {
      setContentLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadContent();
  }, [loadContent]);

  function openNewContent() {
    setContentDraft(newContentDraft());
    setContentFormError(null);
    setContentEditorOpen(true);
  }

  function openEditContent(item: WidgetContent) {
    setContentDraft({
      id: item.id,
      kind: item.kind,
      title: item.title,
      summary: item.summary,
      body: item.body,
      published: item.published,
    });
    setContentFormError(null);
    setContentEditorOpen(true);
  }

  async function saveContent(event: FormEvent) {
    event.preventDefault();
    const title = contentDraft.title.trim();
    const body = contentDraft.body.trim();
    if (!title || !body) {
      setContentFormError("Add a title and body before publishing.");
      return;
    }
    setContentFormError(null);

    const editingId = contentDraft.id;
    const payload: WidgetContentInput = {
      kind: contentDraft.kind,
      title,
      summary: contentDraft.summary.trim(),
      body,
      published: contentDraft.published,
    };

    setSavingContent(true);
    try {
      const saved = editingId
        ? await apiFetch<WidgetContent>(`/widget-content/${encodeURIComponent(editingId)}`, {
            method: "PUT",
            json: payload,
          })
        : await apiFetch<WidgetContent>("/widget-content", {
            method: "POST",
            json: payload,
          });

      setContentItems((current) =>
        editingId
          ? current.map((item) => (item.id === saved.id ? saved : item))
          : [saved, ...current],
      );
      setContentEditorOpen(false);
      setContentDraft(newContentDraft());
      toast(editingId ? "Content updated" : payload.published ? "Content published" : "Draft saved", {
        tone: "success",
      });
    } catch (err) {
      toast("Could not save the content", {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    } finally {
      setSavingContent(false);
    }
  }

  async function deleteContent() {
    if (!deleteContentTarget) return;
    const target = deleteContentTarget;
    setDeletingContent(true);
    try {
      await apiFetch<void>(`/widget-content/${encodeURIComponent(target.id)}`, {
        method: "DELETE",
      });
      setContentItems((current) => current.filter((item) => item.id !== target.id));
      setDeleteContentTarget(null);
      toast("Content deleted", { tone: "success" });
    } catch (err) {
      toast("Could not delete the content", {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    } finally {
      setDeletingContent(false);
    }
  }

  /* -------------------------------- members ------------------------------- */
  const [members, setMembers] = useState<WorkspaceMember[]>([]);
  const [membersLoading, setMembersLoading] = useState(true);
  const [membersError, setMembersError] = useState<string | null>(null);
  const [memberEmail, setMemberEmail] = useState("");
  const [memberRole, setMemberRole] = useState<Role>("AGENT");
  const [addingMember, setAddingMember] = useState(false);
  const [memberError, setMemberError] = useState<string | null>(null);
  const [removeTarget, setRemoveTarget] = useState<WorkspaceMember | null>(null);

  const loadMembers = useCallback(async () => {
    setMembersLoading(true);
    setMembersError(null);
    try {
      const data = await apiFetch<{ items: WorkspaceMember[] }>("/workspace/members");
      setMembers(data.items ?? []);
    } catch (err) {
      setMembersError(err instanceof Error ? err.message : "Could not load members.");
    } finally {
      setMembersLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadMembers();
  }, [loadMembers]);

  async function addMember(event: FormEvent) {
    event.preventDefault();
    const email = memberEmail.trim();
    if (!email) return;
    setAddingMember(true);
    setMemberError(null);
    try {
      const created = await apiFetch<WorkspaceMember>("/workspace/members", {
        method: "POST",
        json: { email, role: memberRole },
      });
      setMembers((current) => [...current, created]);
      setMemberEmail("");
      toast(`${created.name} added`, { tone: "success" });
    } catch (err) {
      setMemberError(err instanceof Error ? err.message : "Could not add the member.");
    } finally {
      setAddingMember(false);
    }
  }

  async function removeMember() {
    if (!removeTarget) return;
    const target = removeTarget;
    try {
      await apiFetch<void>(
        `/workspace/members/${encodeURIComponent(target.userId)}`,
        { method: "DELETE" },
      );
      setMembers((current) => current.filter((member) => member.userId !== target.userId));
      setRemoveTarget(null);
      toast(`${target.name} removed`, { tone: "success" });
    } catch (err) {
      toast("Could not remove the member", {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    }
  }

  /* --------------------------------- teams -------------------------------- */
  const [teams, setTeams] = useState<Team[]>([]);
  const [teamsLoading, setTeamsLoading] = useState(true);
  const [teamsError, setTeamsError] = useState<string | null>(null);
  const [teamName, setTeamName] = useState("");
  const [teamDescription, setTeamDescription] = useState("");
  const [creatingTeam, setCreatingTeam] = useState(false);
  const [teamError, setTeamError] = useState<string | null>(null);
  const [teamMembersTarget, setTeamMembersTarget] = useState<Team | null>(null);

  const loadTeams = useCallback(async () => {
    setTeamsLoading(true);
    setTeamsError(null);
    try {
      const data = await apiFetch<{ items: Team[] }>("/teams");
      setTeams(data.items ?? []);
    } catch (err) {
      setTeamsError(err instanceof Error ? err.message : "Could not load teams.");
    } finally {
      setTeamsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadTeams();
  }, [loadTeams]);

  async function createTeam(event: FormEvent) {
    event.preventDefault();
    const name = teamName.trim();
    if (!name) return;
    setCreatingTeam(true);
    setTeamError(null);
    try {
      const created = await apiFetch<Team>("/teams", {
        method: "POST",
        json: { name, description: teamDescription.trim() || undefined },
      });
      setTeams((current) => [...current, created]);
      setTeamName("");
      setTeamDescription("");
      toast("Team created", { tone: "success" });
    } catch (err) {
      setTeamError(err instanceof Error ? err.message : "Could not create the team.");
    } finally {
      setCreatingTeam(false);
    }
  }

  async function updateTeam(team: Team, memberIds: string[], removeUserId?: string) {
    try {
      if (removeUserId) {
        const updated = await apiFetch<Team>(
          `/teams/${encodeURIComponent(team.id)}/members/${encodeURIComponent(removeUserId)}`,
          { method: "DELETE" },
        );
        setTeams((current) =>
          current.map((item) => (item.id === updated.id ? updated : item)),
        );
        setTeamMembersTarget(updated);
        return;
      }
      const added = await apiFetch<Team>(
        `/teams/${encodeURIComponent(team.id)}/members`,
        { method: "POST", json: { userId: memberIds[0] } },
      );
      setTeams((current) =>
        current.map((item) => (item.id === added.id ? added : item)),
      );
      setTeamMembersTarget(added);
      toast("Team updated", { tone: "success" });
    } catch (err) {
      toast("Could not update the team", {
        tone: "error",
        description: err instanceof Error ? err.message : undefined,
      });
    }
  }

  const widgetColor = widgetDraft?.color ?? "#4f46e5";
  const contentTitleLabel =
    contentDraft.kind === "FAQ"
      ? "Question"
      : contentDraft.kind === "BLOG"
        ? "Title"
        : "Headline";
  const contentBodyLabel =
    contentDraft.kind === "FAQ"
      ? "Answer"
      : contentDraft.kind === "BLOG"
        ? "Article"
        : "Announcement";

  return (
    <div className="space-y-6">
      <div className="grid gap-6 xl:grid-cols-2">
        {/* --------------------------- workspace --------------------------- */}
        <Card
          title="Workspace"
          description="Identity and embed settings for this workspace."
          action={
            <Button
              size="icon"
              variant="ghost"
              onClick={() => void loadWorkspace()}
              aria-label="Refresh workspace"
            >
              <RefreshIcon className="h-4 w-4" />
            </Button>
          }
        >
          {workspaceError ? (
            <ErrorBanner message={workspaceError} onRetry={() => void loadWorkspace()} />
          ) : workspaceLoading && !workspaceDetail ? (
            <div className="space-y-3">
              <Skeleton className="h-9 w-full" />
              <Skeleton className="h-9 w-full" />
              <Skeleton className="h-20 w-full" />
            </div>
          ) : (
            <div className="space-y-4">
              <form onSubmit={saveWorkspace} className="flex items-end gap-2">
                <Input
                  label="Workspace name"
                  value={workspaceName}
                  onChange={(event) => setWorkspaceName(event.target.value)}
                  className="flex-1"
                />
                <Button type="submit" loading={savingWorkspace}>
                  Save
                </Button>
              </form>

              <div className="grid gap-3 sm:grid-cols-2">
                <div className="rounded-lg border border-surface-200 bg-surface-50/60 p-3">
                  <p className="flex items-center gap-1.5 text-xs font-medium text-surface-500">
                    <LinkIcon className="h-3.5 w-3.5" /> Slug
                  </p>
                  <div className="mt-1 flex items-center gap-1">
                    <p className="min-w-0 flex-1 truncate font-mono text-sm text-surface-800">
                      {workspaceDetail?.slug ?? "—"}
                    </p>
                    {workspaceDetail?.slug ? (
                      <CopyButton value={workspaceDetail.slug} label="Slug" />
                    ) : null}
                  </div>
                </div>

                <div className="rounded-lg border border-surface-200 bg-surface-50/60 p-3">
                  <p className="flex items-center gap-1.5 text-xs font-medium text-surface-500">
                    <BuildingIcon className="h-3.5 w-3.5" /> App ID
                  </p>
                  <div className="mt-1 flex items-center gap-1">
                    <p className="min-w-0 flex-1 truncate font-mono text-sm text-surface-800">
                      {appId || "—"}
                    </p>
                    {appId ? <CopyButton value={appId} label="App ID" /> : null}
                  </div>
                </div>
              </div>

              <div className="rounded-lg border border-surface-200 bg-surface-50/60 p-3">
                <div className="flex items-center justify-between gap-2">
                  <p className="text-xs font-medium text-surface-500">
                    Embed the messenger widget
                  </p>
                  {embedSnippet ? <CopyButton value={embedSnippet} label="Snippet" /> : null}
                </div>
                <pre className="mt-2 overflow-x-auto whitespace-pre-wrap break-all rounded-md bg-surface-900 p-3 font-mono text-[11px] leading-relaxed text-surface-100">
                  {embedSnippet || "Loading app id…"}
                </pre>
                {widgetUrl ? (
                  <a
                    href={widgetUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="mt-2 inline-flex text-xs font-medium text-brand-700 hover:text-brand-800"
                  >
                    Open the widget preview →
                  </a>
                ) : null}
              </div>

              {workspaceDetail?.createdAt ? (
                <p className="text-xs text-surface-400">
                  Workspace created {formatDate(workspaceDetail.createdAt)}.
                </p>
              ) : null}
            </div>
          )}
        </Card>

        {/* ---------------------------- widget ----------------------------- */}
        <Card
          title="Messenger widget"
          description="Branding and behaviour of the embedded messenger."
          action={
            <Button
              size="icon"
              variant="ghost"
              onClick={() => void loadWidget()}
              aria-label="Refresh widget settings"
            >
              <RefreshIcon className="h-4 w-4" />
            </Button>
          }
        >
          {widgetError ? (
            <ErrorBanner message={widgetError} onRetry={() => void loadWidget()} />
          ) : widgetLoading || !widgetDraft ? (
            <div className="space-y-3">
              <Skeleton className="h-9 w-full" />
              <Skeleton className="h-9 w-full" />
              <Skeleton className="h-9 w-full" />
              <Skeleton className="h-40 w-full" />
            </div>
          ) : (
            <form onSubmit={saveWidget} className="space-y-4">
              <Input
                label="Workspace display name"
                value={widgetDraft.workspaceName}
                onChange={(event) => updateDraft({ workspaceName: event.target.value })}
              />
              <Input
                label="Greeting"
                value={widgetDraft.greeting}
                onChange={(event) => updateDraft({ greeting: event.target.value })}
              />
              <Input
                label="Subtitle"
                value={widgetDraft.subtitle}
                onChange={(event) => updateDraft({ subtitle: event.target.value })}
              />
              <Input
                label="Intro message"
                value={widgetDraft.introMessage}
                onChange={(event) => updateDraft({ introMessage: event.target.value })}
              />
              <Input
                label="Input placeholder"
                value={widgetDraft.inputPlaceholder}
                onChange={(event) => updateDraft({ inputPlaceholder: event.target.value })}
              />

              <div>
                <label
                  htmlFor="widget-color"
                  className="mb-1.5 block text-sm font-medium text-surface-700"
                >
                  Accent colour
                </label>
                <div className="flex items-center gap-3">
                  <input
                    id="widget-color"
                    type="color"
                    value={widgetColor}
                    onChange={(event) => updateDraft({ color: event.target.value })}
                    className="h-9 w-14 cursor-pointer rounded-lg border border-surface-300 bg-white p-1"
                    aria-label="Accent colour picker"
                  />
                  <input
                    type="text"
                    value={widgetColor}
                    onChange={(event) => updateDraft({ color: event.target.value })}
                    aria-label="Accent colour hex value"
                    className="w-32 rounded-lg border border-surface-300 bg-white px-3 py-2 font-mono text-sm text-surface-900 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30"
                  />
                </div>
              </div>

              <div className="space-y-3 rounded-lg border border-surface-200 p-3.5">
                <ToggleSwitch
                  checked={widgetDraft.showBranding}
                  onChange={(value) => updateDraft({ showBranding: value })}
                  label="Show branding"
                  description="Display the “Powered by Monotron” footer."
                />
                {autoOpenSupported ? (
                  <ToggleSwitch
                    checked={widgetDraft.autoOpen}
                    onChange={(value) => updateDraft({ autoOpen: value })}
                    label="Auto-open on page load"
                    description="Open the chat panel automatically when a visitor lands on your site."
                  />
                ) : null}
                <ToggleSwitch
                  checked={widgetDraft.aiEnabled}
                  onChange={(value) => updateDraft({ aiEnabled: value })}
                  label="AI enabled"
                  description="Let the assistant answer visitor questions."
                />
                <ToggleSwitch
                  checked={widgetDraft.autoReply}
                  onChange={(value) => updateDraft({ autoReply: value })}
                  label="Auto reply"
                  description="Send an AI reply automatically after each message."
                />
              </div>

              {/* live preview */}
              <div className="rounded-xl border border-surface-200 bg-surface-50 p-4">
                <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-surface-400">
                  Live preview
                </p>
                <div className="mx-auto flex max-w-sm flex-col overflow-hidden rounded-xl border border-surface-200 bg-white shadow-lift">
                  <div
                    className="px-4 py-3 text-white"
                    style={{ backgroundColor: widgetColor }}
                  >
                    <p className="text-sm font-semibold">{widgetDraft.greeting}</p>
                    <p className="mt-0.5 text-[11px] opacity-90">{widgetDraft.subtitle}</p>
                  </div>
                  <div className="flex-1 space-y-2 bg-surface-50 p-3">
                    <div
                      className="max-w-[85%] rounded-2xl px-3 py-2 text-xs leading-relaxed"
                      style={{ backgroundColor: `${widgetColor}1a`, color: "#1e1b4b" }}
                    >
                      {widgetDraft.introMessage}
                    </div>
                    <div className="ml-auto max-w-[80%] rounded-2xl bg-white px-3 py-2 text-xs text-surface-700 shadow-sm">
                      Hi! How do I reset my password?
                    </div>
                  </div>
                  <div className="border-t border-surface-100 p-2">
                    <div className="flex items-center gap-2 rounded-lg border border-surface-200 px-2.5 py-1.5">
                      <span className="flex-1 truncate text-xs text-surface-400">
                        {widgetDraft.inputPlaceholder}
                      </span>
                      <span
                        className="flex h-6 w-6 items-center justify-center rounded-md text-white"
                        style={{ backgroundColor: widgetColor }}
                      >
                        <SparklesIcon className="h-3 w-3" />
                      </span>
                    </div>
                  </div>
                  {widgetDraft.showBranding ? (
                    <p className="pb-2 text-center text-[10px] text-surface-400">
                      Powered by Monotron
                    </p>
                  ) : null}
                </div>
              </div>

              <Button type="submit" loading={savingWidget} className="w-full">
                Save widget settings
              </Button>
            </form>
          )}
        </Card>
      </div>

      {/* ------------------------- help center content ------------------------- */}
      <Card
        title="Help center content"
        description="Blogs, FAQs and news announcements shown in the widget's help screen."
        action={
          <div className="flex items-center gap-1">
            <Button
              size="icon"
              variant="ghost"
              onClick={() => void loadContent()}
              aria-label="Refresh help-center content"
            >
              <RefreshIcon className="h-4 w-4" />
            </Button>
            {canManageContent ? (
              <Button
                size="sm"
                icon={<PlusIcon className="h-4 w-4" />}
                onClick={openNewContent}
              >
                New content
              </Button>
            ) : null}
          </div>
        }
      >
        {contentError ? (
          <ErrorBanner message={contentError} onRetry={() => void loadContent()} />
        ) : contentLoading && contentItems.length === 0 ? (
          <div className="space-y-2">
            {[0, 1, 2].map((index) => (
              <div key={index} className="rounded-lg border border-surface-200 p-4">
                <Skeleton className="h-4 w-40" />
                <Skeleton className="mt-2 h-3 w-64" />
              </div>
            ))}
          </div>
        ) : contentItems.length === 0 ? (
          <div className="rounded-lg border border-dashed border-surface-300 bg-surface-50 px-4 py-8 text-center">
            <BookIcon className="mx-auto h-5 w-5 text-surface-400" />
            <p className="mt-2 text-sm font-medium text-surface-700">
              No help content yet
            </p>
            <p className="mt-0.5 text-xs text-surface-500">
              Add a blog post, FAQ or news announcement for visitors to browse.
            </p>
            {canManageContent ? (
              <Button
                size="sm"
                variant="outline"
                className="mt-3"
                icon={<PlusIcon className="h-4 w-4" />}
                onClick={openNewContent}
              >
                Add content
              </Button>
            ) : null}
          </div>
        ) : (
          <ul className="divide-y divide-surface-100">
            {contentItems.map((item) => (
              <li
                key={item.id}
                className="flex items-start gap-3 py-3 first:pt-0 last:pb-0"
              >
                <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-surface-100 text-surface-500">
                  {contentKindIcon(item.kind, "h-4 w-4")}
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge
                      variant={
                        item.kind === "ANNOUNCEMENT"
                          ? "warning"
                          : item.kind === "BLOG"
                            ? "info"
                            : "brand"
                      }
                    >
                      {contentKindLabel(item.kind)}
                    </Badge>
                    <Badge variant={item.published ? "success" : "neutral"}>
                      {item.published ? "Published" : "Draft"}
                    </Badge>
                    <span className="text-[10px] text-surface-400">
                      Updated {formatDate(item.updatedAt)}
                    </span>
                  </div>
                  <p className="mt-1 truncate text-sm font-medium text-surface-900">
                    {item.title}
                  </p>
                  {item.summary ? (
                    <p className="mt-0.5 line-clamp-2 text-xs text-surface-500">
                      {item.summary}
                    </p>
                  ) : null}
                </div>
                {canManageContent ? (
                  <div className="flex shrink-0 items-center gap-1">
                    <Button
                      size="icon"
                      variant="ghost"
                      onClick={() => openEditContent(item)}
                      aria-label={`Edit ${item.title}`}
                      title="Edit"
                    >
                      <PencilIcon className="h-4 w-4" />
                    </Button>
                    <Button
                      size="icon"
                      variant="ghost"
                      onClick={() => setDeleteContentTarget(item)}
                      aria-label={`Delete ${item.title}`}
                      title="Delete"
                    >
                      <TrashIcon className="h-4 w-4" />
                    </Button>
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </Card>

      <div className="grid gap-6 xl:grid-cols-2">
        {/* ---------------------------- members ---------------------------- */}
        <Card
          title="Team members"
          description="People with access to this workspace."
          action={
            <Button
              size="icon"
              variant="ghost"
              onClick={() => void loadMembers()}
              aria-label="Refresh members"
            >
              <RefreshIcon className="h-4 w-4" />
            </Button>
          }
        >
          {membersError ? (
            <ErrorBanner message={membersError} onRetry={() => void loadMembers()} />
          ) : null}

          <form onSubmit={addMember} className="mb-4 grid gap-2 sm:grid-cols-[minmax(0,1fr)_9rem_auto]">
            <Input
              label="Add by email"
              type="email"
              value={memberEmail}
              onChange={(event) => setMemberEmail(event.target.value)}
              placeholder="agent@company.com"
              error={memberError}
            />
            <Select
              label="Role"
              value={memberRole}
              onChange={(event) => setMemberRole(event.target.value as Role)}
              options={ROLES.map((role) => ({ value: role, label: role }))}
            />
            <Button
              type="submit"
              loading={addingMember}
              className="sm:mt-7"
              icon={<PlusIcon className="h-4 w-4" />}
            >
              Add
            </Button>
          </form>

          {membersLoading && members.length === 0 ? (
            <div className="space-y-2">
              {[0, 1, 2].map((index) => (
                <div key={index} className="flex items-center gap-3 rounded-lg border border-surface-200 p-3">
                  <Skeleton className="h-9 w-9 rounded-full" />
                  <div className="flex-1">
                    <Skeleton className="h-4 w-32" />
                    <Skeleton className="mt-1.5 h-3 w-44" />
                  </div>
                </div>
              ))}
            </div>
          ) : members.length === 0 ? (
            <div className="rounded-lg border border-dashed border-surface-300 bg-surface-50 px-4 py-8 text-center">
              <UsersIcon className="mx-auto h-5 w-5 text-surface-400" />
              <p className="mt-2 text-sm font-medium text-surface-700">No members yet</p>
              <p className="text-xs text-surface-500">
                Add teammates by their platform email address.
              </p>
            </div>
          ) : (
            <ul className="divide-y divide-surface-100">
              {members.map((member) => (
                <li key={member.userId} className="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
                  <span
                    className={clsx(
                      "flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-xs font-semibold text-white",
                      avatarColor(member.name),
                    )}
                    aria-hidden="true"
                  >
                    {initials(member.name)}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-surface-900">
                      {member.name}
                    </p>
                    <p className="truncate text-xs text-surface-500">{member.email}</p>
                  </div>
                  <Badge variant={member.role === "ADMIN" ? "brand" : "neutral"}>
                    {member.role}
                  </Badge>
                  <Badge variant={member.status === "ONLINE" ? "success" : "neutral"}>
                    {member.status}
                  </Badge>
                  <button
                    type="button"
                    onClick={() => setRemoveTarget(member)}
                    aria-label={`Remove ${member.name}`}
                    className="rounded-md p-1.5 text-surface-400 transition-colors hover:bg-rose-50 hover:text-rose-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-rose-500"
                  >
                    <TrashIcon className="h-4 w-4" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {/* ----------------------------- teams ----------------------------- */}
        <Card
          title="Teams"
          description="Group members to route conversations."
          action={
            <Button
              size="icon"
              variant="ghost"
              onClick={() => void loadTeams()}
              aria-label="Refresh teams"
            >
              <RefreshIcon className="h-4 w-4" />
            </Button>
          }
        >
          {teamsError ? (
            <ErrorBanner message={teamsError} onRetry={() => void loadTeams()} />
          ) : null}

          <form onSubmit={createTeam} className="mb-4 grid gap-2 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]">
            <Input
              label="Team name"
              value={teamName}
              onChange={(event) => setTeamName(event.target.value)}
              placeholder="Tier 1"
              error={teamError}
            />
            <Input
              label="Description"
              value={teamDescription}
              onChange={(event) => setTeamDescription(event.target.value)}
              placeholder="Front-line support"
            />
            <Button
              type="submit"
              loading={creatingTeam}
              className="sm:mt-7"
              icon={<PlusIcon className="h-4 w-4" />}
            >
              Create
            </Button>
          </form>

          {teamsLoading && teams.length === 0 ? (
            <div className="space-y-2">
              {[0, 1].map((index) => (
                <div key={index} className="rounded-lg border border-surface-200 p-4">
                  <Skeleton className="h-4 w-32" />
                  <Skeleton className="mt-2 h-3 w-48" />
                </div>
              ))}
            </div>
          ) : teams.length === 0 ? (
            <div className="rounded-lg border border-dashed border-surface-300 bg-surface-50 px-4 py-8 text-center">
              <UsersIcon className="mx-auto h-5 w-5 text-surface-400" />
              <p className="mt-2 text-sm font-medium text-surface-700">No teams yet</p>
              <p className="text-xs text-surface-500">
                Create a team to route incoming conversations.
              </p>
            </div>
          ) : (
            <ul className="space-y-2.5">
              {teams.map((team) => (
                <li
                  key={team.id}
                  className="flex items-center gap-3 rounded-lg border border-surface-200 px-3.5 py-3"
                >
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-surface-900">{team.name}</p>
                    <p className="truncate text-xs text-surface-500">
                      {team.description || "No description"} ·{" "}
                      {team.memberIds?.length ?? 0} member
                      {(team.memberIds?.length ?? 0) === 1 ? "" : "s"}
                    </p>
                  </div>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => setTeamMembersTarget(team)}
                  >
                    Members
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      {/* help center content editor */}
      <Modal
        open={contentEditorOpen}
        onClose={() => setContentEditorOpen(false)}
        title={contentDraft.id ? "Edit help content" : "New help content"}
        description="Publish a blog post, FAQ or announcement to the widget help center."
        size="lg"
        footer={
          <>
            <Button variant="ghost" onClick={() => setContentEditorOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" form="content-form" loading={savingContent}>
              {contentDraft.id
                ? "Save changes"
                : contentDraft.published
                  ? "Publish"
                  : "Save draft"}
            </Button>
          </>
        }
      >
        <form id="content-form" onSubmit={saveContent} className="space-y-4">
          <Select
            label="Content type"
            value={contentDraft.kind}
            onChange={(event) =>
              setContentDraft((current) => ({
                ...current,
                kind: event.target.value as WidgetContentKind,
              }))
            }
            options={CONTENT_KINDS.map((kind) => ({
              value: kind,
              label: contentKindLabel(kind),
            }))}
          />

          {contentFormError ? (
            <p className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
              {contentFormError}
            </p>
          ) : null}

          <Input
            label={contentTitleLabel}
            value={contentDraft.title}
            onChange={(event) =>
              setContentDraft((current) => ({ ...current, title: event.target.value }))
            }
            placeholder={
              contentDraft.kind === "FAQ"
                ? "How do I reset my password?"
                : contentDraft.kind === "BLOG"
                  ? "A practical guide to faster support"
                  : "We just shipped something new"
            }
            required
          />

          <Textarea
            label="Summary"
            value={contentDraft.summary}
            onChange={(event) =>
              setContentDraft((current) => ({ ...current, summary: event.target.value }))
            }
            placeholder="A short preview shown in the widget list."
            rows={2}
            hint="Optional. Keep it to one or two lines."
          />

          <Textarea
            label={contentBodyLabel}
            value={contentDraft.body}
            onChange={(event) =>
              setContentDraft((current) => ({ ...current, body: event.target.value }))
            }
            placeholder="Write the full content here."
            rows={8}
            required
          />

          <ToggleSwitch
            checked={contentDraft.published}
            onChange={(value) =>
              setContentDraft((current) => ({ ...current, published: value }))
            }
            label="Published"
            description="Show this item in the widget help center."
          />
        </form>
      </Modal>

      {/* delete help content confirm */}
      <Modal
        open={Boolean(deleteContentTarget)}
        onClose={() => setDeleteContentTarget(null)}
        title="Delete content"
        description="This removes the item from the widget help center immediately."
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleteContentTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={deletingContent}
              onClick={() => void deleteContent()}
            >
              Delete
            </Button>
          </>
        }
      >
        <p className="text-sm text-surface-600">
          Delete{" "}
          <span className="font-semibold text-surface-900">
            {deleteContentTarget?.title}
          </span>
          ?
        </p>
      </Modal>

      {/* remove member confirm */}
      <Modal
        open={Boolean(removeTarget)}
        onClose={() => setRemoveTarget(null)}
        title="Remove member"
        description="They will lose access to this workspace immediately."
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setRemoveTarget(null)}>
              Cancel
            </Button>
            <Button variant="danger" onClick={() => void removeMember()}>
              Remove
            </Button>
          </>
        }
      >
        <p className="text-sm text-surface-600">
          <span className="font-semibold text-surface-900">{removeTarget?.name}</span> (
          {removeTarget?.email}) will be removed from this workspace.
        </p>
      </Modal>

      {/* team members */}
      <Modal
        open={Boolean(teamMembersTarget)}
        onClose={() => setTeamMembersTarget(null)}
        title={teamMembersTarget ? `${teamMembersTarget.name} members` : "Team members"}
        description="Add or remove teammates from this team."
        footer={
          <Button variant="ghost" onClick={() => setTeamMembersTarget(null)}>
            Done
          </Button>
        }
      >
        {teamMembersTarget ? (
          <div className="space-y-4">
            <ul className="space-y-1.5">
              {(teamMembersTarget.memberIds ?? []).length === 0 ? (
                <li className="rounded-lg border border-dashed border-surface-300 bg-surface-50 px-4 py-6 text-center text-sm text-surface-500">
                  No members in this team yet.
                </li>
              ) : (
                (teamMembersTarget.memberIds ?? []).map((userId) => {
                  const member = members.find((item) => item.userId === userId);
                  return (
                    <li
                      key={userId}
                      className="flex items-center gap-3 rounded-lg border border-surface-200 px-3 py-2.5"
                    >
                      <span
                        className={clsx(
                          "flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[11px] font-semibold text-white",
                          avatarColor(member?.name ?? userId),
                        )}
                      >
                        {initials(member?.name)}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-medium text-surface-800">
                          {member?.name ?? userId}
                        </span>
                        {member?.email ? (
                          <span className="block truncate text-xs text-surface-500">
                            {member.email}
                          </span>
                        ) : null}
                      </span>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() =>
                          void updateTeam(teamMembersTarget, [], userId)
                        }
                        aria-label={`Remove ${member?.name ?? "member"} from team`}
                      >
                        Remove
                      </Button>
                    </li>
                  );
                })
              )}
            </ul>

            <div className="border-t border-surface-100 pt-4">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-surface-400">
                Add a teammate
              </p>
              <ul className="space-y-1.5">
                {members
                  .filter(
                    (member) =>
                      !(teamMembersTarget.memberIds ?? []).includes(member.userId),
                  )
                  .map((member) => (
                    <li key={member.userId}>
                      <button
                        type="button"
                        onClick={() =>
                          void updateTeam(teamMembersTarget, [member.userId])
                        }
                        className="flex w-full items-center gap-3 rounded-lg border border-surface-200 px-3 py-2.5 text-left transition-colors hover:bg-surface-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
                      >
                        <span
                          className={clsx(
                            "flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[11px] font-semibold text-white",
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
                        <PlusIcon className="h-4 w-4 text-surface-400" />
                      </button>
                    </li>
                  ))}
              </ul>
            </div>
          </div>
        ) : null}
      </Modal>
    </div>
  );
}
