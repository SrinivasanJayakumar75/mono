"use client";

import clsx from "clsx";
import { useCallback, useEffect, useState } from "react";

import {
  BuildingIcon,
  CalendarIcon,
  CartIcon,
  ClockIcon,
  DealsIcon,
  DocumentIcon,
  ExternalLinkIcon,
  GlobeIcon,
  MailIcon,
  PencilIcon,
  PhoneIcon,
  TasksIcon,
  TrashIcon,
  UserIcon,
} from "@/components/icons";
import { Badge, Button, ErrorBanner, Modal, Skeleton } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api";
import {
  asNumber,
  asText,
  CRM_RESOURCES,
  type CrmKey,
  type CrmRecord,
  type CrmTone,
} from "@/lib/crm";
import { formatDate, formatDateTime, initials } from "@/lib/format";

interface LeadProfileData {
  lead: CrmRecord;
  deals: CrmRecord[];
  orders: CrmRecord[];
  tasks: CrmRecord[];
  calls: CrmRecord[];
  meetings: CrmRecord[];
  documents: CrmRecord[];
}

const CURRENCY = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

export function LeadProfileModal({
  lead,
  onClose,
  onEdit,
  onDelete,
}: {
  lead: CrmRecord;
  onClose: () => void;
  onEdit: (lead: CrmRecord) => void;
  onDelete: (lead: CrmRecord) => Promise<boolean>;
}) {
  const leadId = asText(lead.id);
  const [data, setData] = useState<LeadProfileData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await apiFetch<LeadProfileData>(`/crm/leads/${leadId}/profile`));
    } catch (problem) {
      setError(problem instanceof ApiError ? problem.message : "Could not load this lead");
    } finally {
      setLoading(false);
    }
  }, [leadId]);

  useEffect(() => {
    void load();
  }, [load]);

  const current = data?.lead ?? lead;
  const name =
    asText(current.name) ||
    [asText(current.firstName), asText(current.lastName)].filter(Boolean).join(" ");
  const title = asText(current.title);
  const company = asText(current.companyName) || asText(current.companyText);
  const status = asText(current.status);
  const priority = asText(current.priority);
  const source = asText(current.source);
  const intent = asText(current.buyingIntent);
  const score = asNumber(current.leadScore);
  const expected = asText(current.expectedPurchaseDate);
  const notes = asText(current.notes);

  const deals = data?.deals ?? [];
  const orders = data?.orders ?? [];
  const tasks = data?.tasks ?? [];
  const calls = data?.calls ?? [];
  const meetings = data?.meetings ?? [];
  const documents = data?.documents ?? [];
  const activityCount =
    deals.length + orders.length + tasks.length + calls.length + meetings.length + documents.length;

  async function handleDelete() {
    setDeleting(true);
    try {
      const removed = await onDelete(current);
      if (removed) onClose();
    } finally {
      setDeleting(false);
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title="Lead profile"
      description={company || undefined}
      size="xl"
      footer={
        <div className="flex w-full flex-wrap items-center justify-between gap-2">
          <Button
            variant="ghost"
            icon={<TrashIcon className="h-3.5 w-3.5" />}
            onClick={() => void handleDelete()}
            loading={deleting}
            className="text-rose-600 hover:bg-rose-50 hover:text-rose-700"
          >
            Delete lead
          </Button>
          <div className="flex items-center gap-2">
            <Button variant="ghost" onClick={onClose}>
              Close
            </Button>
            <Button
              icon={<PencilIcon className="h-3.5 w-3.5" />}
              onClick={() => onEdit(current)}
            >
              Edit lead
            </Button>
          </div>
        </div>
      }
    >
      <div className="space-y-4">
        <section className="rounded-xl border border-surface-200 bg-surface-50/60 p-4">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start">
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-brand-50 text-sm font-semibold text-brand-700 ring-1 ring-brand-200">
              {initials(name || "Lead")}
            </div>

            <div className="min-w-0 flex-1">
              <h3 className="text-[17px] font-semibold tracking-[-0.015em] text-surface-900">
                {name || "Unnamed lead"}
              </h3>
              <p className="mt-0.5 text-[13px] text-surface-500">
                {[title, company].filter(Boolean).join(" · ") || "No company or job title yet"}
              </p>
              <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
                {status ? (
                  <Badge variant={toneFor("leads", "status", status)}>
                    {labelFor("leads", "status", status)}
                  </Badge>
                ) : null}
                {priority ? (
                  <Badge variant={toneFor("leads", "priority", priority)}>
                    {labelFor("leads", "priority", priority)} priority
                  </Badge>
                ) : null}
                {intent ? (
                  <Badge variant={intent === "HIGH" ? "brand" : intent === "MEDIUM" ? "info" : "neutral"}>
                    {labelFor("leads", "buyingIntent", intent)} intent
                  </Badge>
                ) : null}
                {source ? (
                  <Badge variant="neutral">{labelFor("leads", "source", source)}</Badge>
                ) : null}
              </div>
            </div>

            <div className="grid shrink-0 grid-cols-2 gap-2 sm:w-[236px]">
              <Metric label="Lead score" value={score > 0 ? String(score) : "—"} />
              <Metric label="Expected purchase" value={expected ? formatDate(expected) : "—"} />
            </div>
          </div>
        </section>

        <div className="grid gap-4 lg:grid-cols-[minmax(0,1.25fr)_minmax(0,1fr)]">
          <div className="space-y-4">
            <Panel title="Contact details" icon={<UserIcon className="h-3.5 w-3.5" />}>
              <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
                <ContactRow
                  icon={<MailIcon className="h-3.5 w-3.5" />}
                  label="Email"
                  value={asText(current.email)}
                  href={asText(current.email) ? `mailto:${asText(current.email)}` : undefined}
                />
                <ContactRow
                  icon={<PhoneIcon className="h-3.5 w-3.5" />}
                  label="Phone"
                  value={asText(current.phone)}
                  href={phoneHref(asText(current.phone))}
                />
                <ContactRow
                  icon={<PhoneIcon className="h-3.5 w-3.5" />}
                  label="WhatsApp"
                  value={asText(current.whatsapp)}
                  href={phoneHref(asText(current.whatsapp))}
                />
                <ContactRow
                  icon={<GlobeIcon className="h-3.5 w-3.5" />}
                  label="Website"
                  value={asText(current.website)}
                  href={safeHref(asText(current.website))}
                  external
                />
                <ContactRow
                  icon={<BuildingIcon className="h-3.5 w-3.5" />}
                  label="Location"
                  value={asText(current.location)}
                />
                <ContactRow
                  icon={<GlobeIcon className="h-3.5 w-3.5" />}
                  label="Country"
                  value={asText(current.country)}
                />
              </dl>
            </Panel>

            <Panel title="Qualification" icon={<DealsIcon className="h-3.5 w-3.5" />}>
              <dl className="grid gap-x-6 gap-y-2.5 sm:grid-cols-2">
                <Fact label="Company" value={company} />
                <Fact label="Industry" value={asText(current.industry)} />
                <Fact
                  label="Source"
                  value={source ? labelFor("leads", "source", source) : ""}
                />
                <Fact
                  label="Buying intent"
                  value={intent ? labelFor("leads", "buyingIntent", intent) : ""}
                />
              </dl>
            </Panel>

            {notes ? (
              <Panel title="Notes" icon={<DocumentIcon className="h-3.5 w-3.5" />}>
                <p className="whitespace-pre-wrap text-[13px] leading-relaxed text-surface-700">
                  {notes}
                </p>
              </Panel>
            ) : null}

            <p className="px-0.5 text-[11px] text-surface-400">
              Added {formatDateTime(asText(current.createdAt))} · Updated{" "}
              {formatDateTime(asText(current.updatedAt))}
            </p>
          </div>

          <Panel
            title="Activity"
            icon={<ClockIcon className="h-3.5 w-3.5" />}
            className="self-start"
          >
            {loading ? (
              <div className="space-y-2">
                {Array.from({ length: 4 }).map((_, index) => (
                  <Skeleton key={index} className="h-11 w-full" />
                ))}
              </div>
            ) : error ? (
              <ErrorBanner message={error} onRetry={() => void load()} />
            ) : activityCount === 0 ? (
              <div className="rounded-lg border border-dashed border-surface-200 px-3 py-6 text-center">
                <p className="text-[12.5px] font-medium text-surface-600">Nothing linked yet</p>
                <p className="mx-auto mt-1 max-w-[230px] text-[11.5px] leading-relaxed text-surface-400">
                  Deals, orders, tasks, calls, meetings and documents linked to this lead will
                  appear here.
                </p>
              </div>
            ) : (
              <div className="space-y-4">
                <ActivityGroup
                  label="Deals"
                  icon={<DealsIcon className="h-3.5 w-3.5" />}
                  count={deals.length}
                >
                  {deals.map((deal) => {
                    const stage = asText(deal.stage);
                    const value = asNumber(deal.valueAmount);
                    const close = asText(deal.expectedCloseDate);
                    return (
                      <ActivityRow
                        key={asText(deal.id)}
                        title={asText(deal.title) || "Untitled deal"}
                        badge={
                          stage ? (
                            <Badge variant={toneFor("deals", "stage", stage)}>
                              {labelFor("deals", "stage", stage)}
                            </Badge>
                          ) : null
                        }
                        meta={[
                          value > 0 ? CURRENCY.format(value) : null,
                          close ? `Close ${formatDate(close)}` : null,
                        ]
                          .filter(Boolean)
                          .join(" · ")}
                      />
                    );
                  })}
                </ActivityGroup>

                <ActivityGroup
                  label="Orders"
                  icon={<CartIcon className="h-3.5 w-3.5" />}
                  count={orders.length}
                >
                  {orders.map((order) => {
                    const orderStatus = asText(order.status);
                    const total = asNumber(order.totalAmount);
                    const itemCount = asNumber(order.itemCount);
                    return (
                      <ActivityRow
                        key={asText(order.id)}
                        title={asText(order.orderNumber) || "Order"}
                        badge={
                          orderStatus ? (
                            <Badge variant={toneFor("orders", "status", orderStatus)}>
                              {labelFor("orders", "status", orderStatus)}
                            </Badge>
                          ) : null
                        }
                        meta={[
                          `${itemCount} ${itemCount === 1 ? "item" : "items"}`,
                          total > 0 ? CURRENCY.format(total) : null,
                        ]
                          .filter(Boolean)
                          .join(" · ")}
                      />
                    );
                  })}
                </ActivityGroup>

                <ActivityGroup
                  label="Tasks"
                  icon={<TasksIcon className="h-3.5 w-3.5" />}
                  count={tasks.length}
                >
                  {tasks.map((task) => {
                    const taskStatus = asText(task.status);
                    const due = asText(task.dueDate);
                    const taskPriority = asText(task.priority);
                    return (
                      <ActivityRow
                        key={asText(task.id)}
                        title={asText(task.title) || "Task"}
                        badge={
                          taskStatus ? (
                            <Badge variant={toneFor("tasks", "status", taskStatus)}>
                              {labelFor("tasks", "status", taskStatus)}
                            </Badge>
                          ) : null
                        }
                        meta={[
                          taskPriority
                            ? `${labelFor("tasks", "priority", taskPriority)} priority`
                            : null,
                          due ? `Due ${formatDate(due)}` : null,
                        ]
                          .filter(Boolean)
                          .join(" · ")}
                      />
                    );
                  })}
                </ActivityGroup>

                <ActivityGroup
                  label="Calls"
                  icon={<PhoneIcon className="h-3.5 w-3.5" />}
                  count={calls.length}
                >
                  {calls.map((call) => {
                    const outcome = asText(call.outcome);
                    const direction = asText(call.direction);
                    const duration = asNumber(call.durationSeconds);
                    return (
                      <ActivityRow
                        key={asText(call.id)}
                        title={asText(call.subject) || "Call"}
                        badge={
                          outcome ? (
                            <Badge variant={toneFor("calls", "outcome", outcome)}>
                              {labelFor("calls", "outcome", outcome)}
                            </Badge>
                          ) : null
                        }
                        meta={[
                          direction ? labelFor("calls", "direction", direction) : null,
                          duration > 0 ? `${Math.round(duration / 60)} min` : null,
                          formatDateTime(asText(call.occurredAt)),
                        ]
                          .filter(Boolean)
                          .join(" · ")}
                      />
                    );
                  })}
                </ActivityGroup>

                <ActivityGroup
                  label="Meetings"
                  icon={<CalendarIcon className="h-3.5 w-3.5" />}
                  count={meetings.length}
                >
                  {meetings.map((meeting) => {
                    const meetingStatus = asText(meeting.status);
                    const minutes = asNumber(meeting.durationMinutes);
                    return (
                      <ActivityRow
                        key={asText(meeting.id)}
                        title={asText(meeting.title) || "Meeting"}
                        badge={
                          meetingStatus ? (
                            <Badge variant={toneFor("meetings", "status", meetingStatus)}>
                              {labelFor("meetings", "status", meetingStatus)}
                            </Badge>
                          ) : null
                        }
                        meta={[
                          formatDateTime(asText(meeting.startsAt)),
                          minutes > 0 ? `${minutes} min` : null,
                          asText(meeting.location),
                        ]
                          .filter(Boolean)
                          .join(" · ")}
                      />
                    );
                  })}
                </ActivityGroup>

                <ActivityGroup
                  label="Documents"
                  icon={<DocumentIcon className="h-3.5 w-3.5" />}
                  count={documents.length}
                >
                  {documents.map((document) => {
                    const category = asText(document.category);
                    const url = safeHref(asText(document.url));
                    return (
                      <ActivityRow
                        key={asText(document.id)}
                        title={asText(document.name) || "Document"}
                        href={url}
                        badge={category ? <Badge variant="neutral">{category}</Badge> : null}
                        meta={`Added ${formatDate(asText(document.createdAt))}`}
                      />
                    );
                  })}
                </ActivityGroup>
              </div>
            )}
          </Panel>
        </div>
      </div>
    </Modal>
  );
}

/* -------------------------------- fragments ------------------------------- */

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-surface-200 bg-white px-3 py-2">
      <p className="text-[9.5px] font-medium uppercase tracking-[0.1em] text-surface-400">
        {label}
      </p>
      <p className="mt-1 truncate text-[14px] font-semibold tabular-nums text-surface-900">
        {value}
      </p>
    </div>
  );
}

function Panel({
  title,
  icon,
  children,
  className,
}: {
  title: string;
  icon: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section className={clsx("rounded-xl border border-surface-200 bg-white p-4", className)}>
      <h3 className="flex items-center gap-1.5 text-[10.5px] font-medium uppercase tracking-[0.12em] text-surface-400">
        {icon}
        {title}
      </h3>
      <div className="mt-3">{children}</div>
    </section>
  );
}

function ContactRow({
  icon,
  label,
  value,
  href,
  external,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  href?: string;
  external?: boolean;
}) {
  return (
    <div className="flex min-w-0 items-start gap-2.5">
      <span className="mt-0.5 shrink-0 text-surface-400">{icon}</span>
      <div className="min-w-0">
        <dt className="text-[10px] font-medium uppercase tracking-[0.08em] text-surface-400">
          {label}
        </dt>
        <dd className="mt-0.5 truncate text-[12.5px] font-medium text-surface-800">
          {value && href ? (
            <a
              href={href}
              {...(external ? { target: "_blank", rel: "noreferrer" } : {})}
              className="inline-flex max-w-full items-center gap-1 hover:text-brand-700 hover:underline"
            >
              <span className="truncate">{value}</span>
              {external ? <ExternalLinkIcon className="h-3 w-3 shrink-0" /> : null}
            </a>
          ) : value ? (
            value
          ) : (
            <span className="font-normal text-surface-300">—</span>
          )}
        </dd>
      </div>
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <dt className="text-[12px] text-surface-500">{label}</dt>
      <dd className="text-right text-[12.5px] font-medium text-surface-800">
        {value || <span className="font-normal text-surface-300">—</span>}
      </dd>
    </div>
  );
}

function ActivityGroup({
  label,
  icon,
  count,
  children,
}: {
  label: string;
  icon: React.ReactNode;
  count: number;
  children: React.ReactNode;
}) {
  if (count === 0) return null;
  return (
    <div>
      <div className="flex items-center justify-between gap-2">
        <h4 className="flex items-center gap-1.5 text-[10px] font-medium uppercase tracking-[0.1em] text-surface-400">
          {icon}
          {label}
        </h4>
        <span className="rounded-full bg-surface-100 px-1.5 py-0.5 text-[10px] font-medium tabular-nums text-surface-500">
          {count}
        </span>
      </div>
      <ul className="mt-1.5 space-y-1.5">{children}</ul>
    </div>
  );
}

function ActivityRow({
  title,
  meta,
  badge,
  href,
}: {
  title: string;
  meta?: string;
  badge?: React.ReactNode;
  href?: string;
}) {
  return (
    <li className="rounded-lg border border-surface-100 bg-surface-50/40 px-2.5 py-2">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          {href ? (
            <a
              href={href}
              target="_blank"
              rel="noreferrer"
              className="inline-flex max-w-full items-center gap-1 truncate text-[12.5px] font-medium text-surface-800 hover:text-brand-700 hover:underline"
            >
              <span className="truncate">{title}</span>
              <ExternalLinkIcon className="h-3 w-3 shrink-0" />
            </a>
          ) : (
            <p className="truncate text-[12.5px] font-medium text-surface-800">{title}</p>
          )}
          {meta ? <p className="mt-0.5 truncate text-[11px] text-surface-500">{meta}</p> : null}
        </div>
        {badge ? <span className="shrink-0">{badge}</span> : null}
      </div>
    </li>
  );
}

/* --------------------------------- helpers -------------------------------- */

function toneFor(resource: CrmKey, columnKey: string, value: string): CrmTone {
  const column = CRM_RESOURCES[resource].columns.find((entry) => entry.key === columnKey);
  return column?.tone?.[value] ?? "neutral";
}

function labelFor(resource: CrmKey, fieldKey: string, value: string): string {
  const field = CRM_RESOURCES[resource].fields.find((entry) => entry.key === fieldKey);
  const option = field?.options?.find((entry) => entry.value === value);
  if (option) return option.label;
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function phoneHref(value: string): string | undefined {
  if (!value) return undefined;
  const digits = value.replace(/[^\d+]/g, "");
  return digits ? `tel:${digits}` : undefined;
}

function safeHref(value: string): string | undefined {
  if (!value) return undefined;
  try {
    const url = new URL(value.includes("://") ? value : `https://${value}`);
    return url.protocol === "http:" || url.protocol === "https:" ? url.toString() : undefined;
  } catch {
    return undefined;
  }
}
