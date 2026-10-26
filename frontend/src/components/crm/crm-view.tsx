"use client";

import clsx from "clsx";
import { useCallback, useEffect, useState, type ReactNode } from "react";

import {
  ArrowRightIcon,
  BuildingIcon,
  CalendarIcon,
  CartIcon,
  DealsIcon,
  DocumentIcon,
  LeadsIcon,
  PackageIcon,
  PhoneIcon,
  RefreshIcon,
  TasksIcon,
  UsersIcon,
} from "@/components/icons";
import { Button, ErrorBanner, Skeleton } from "@/components/ui";
import { apiFetch } from "@/lib/api";
import {
  CRM_GROUPS,
  CRM_RESOURCES,
  asNumber,
  type CrmKey,
  type CrmRecord,
  type CrmTab,
} from "@/lib/crm";

import { CrmResourcePanel, type RelationOptions } from "./crm-resource";

const ICONS: Record<CrmKey, ReactNode> = {
  leads: <LeadsIcon className="h-4 w-4" />,
  deals: <DealsIcon className="h-4 w-4" />,
  companies: <BuildingIcon className="h-4 w-4" />,
  contacts: <UsersIcon className="h-4 w-4" />,
  tasks: <TasksIcon className="h-4 w-4" />,
  calls: <PhoneIcon className="h-4 w-4" />,
  meetings: <CalendarIcon className="h-4 w-4" />,
  products: <PackageIcon className="h-4 w-4" />,
  orders: <CartIcon className="h-4 w-4" />,
  documents: <DocumentIcon className="h-4 w-4" />,
};

const CURRENCY = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

function formatMoney(value: unknown): string {
  return CURRENCY.format(asNumber(value));
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "Something went wrong";
}

export function CrmView() {
  const [tab, setTab] = useState<CrmTab>("overview");
  const [relations, setRelations] = useState<RelationOptions>({
    companies: [],
    leads: [],
    products: [],
  });

  const loadRelations = useCallback(async () => {
    try {
      const [companies, leads, products] = await Promise.all([
        apiFetch<{ items: CrmRecord[] }>("/crm/companies", { params: { size: 200 } }),
        apiFetch<{ items: CrmRecord[] }>("/crm/leads", { params: { size: 200 } }),
        apiFetch<{ items: CrmRecord[] }>("/crm/products", { params: { size: 200 } }),
      ]);
      setRelations({
        companies: companies.items.map((row) => ({
          value: String(row.id),
          label: String(row.name ?? "Untitled"),
        })),
        leads: leads.items.map((row) => ({
          value: String(row.id),
          label: String(row.name ?? "Untitled"),
        })),
        products: products.items.map((row) => ({
          value: String(row.id),
          label: [row.name, row.sku].filter(Boolean).join(" · ") || "Untitled",
        })),
      });
    } catch {
      // Relations only enrich the forms; the CRM keeps working without them.
    }
  }, []);

  useEffect(() => {
    void loadRelations();
  }, [loadRelations]);

  return (
    <div className="flex min-h-0 flex-1 flex-col lg:flex-row">
      <CrmNav tab={tab} onChange={setTab} />

      <div className="min-h-0 min-w-0 flex-1 overflow-y-auto">
        {tab === "overview" ? (
          <CrmOverview onOpen={setTab} />
        ) : (
          <CrmResourcePanel
            key={tab}
            config={CRM_RESOURCES[tab]}
            relations={relations}
            onRelationsChanged={() => void loadRelations()}
          />
        )}
      </div>
    </div>
  );
}

function CrmNav({ tab, onChange }: { tab: CrmTab; onChange: (tab: CrmTab) => void }) {
  return (
    <nav
      className="shrink-0 border-b border-surface-200 bg-white px-2 py-2 lg:w-56 lg:border-b-0 lg:border-r lg:px-3 lg:py-4"
      aria-label="CRM sections"
    >
      <div className="flex flex-wrap items-center gap-1 lg:flex-col lg:items-stretch lg:gap-0.5">
        <NavButton
          icon={<ArrowRightIcon className="h-3.5 w-3.5" />}
          label="Overview"
          active={tab === "overview"}
          onClick={() => onChange("overview")}
        />

        {CRM_GROUPS.map((group) => (
          <div key={group.label} className="contents lg:mt-4 lg:block">
            <p className="hidden px-2.5 pb-1.5 text-[10px] font-medium uppercase tracking-[0.14em] text-surface-400 lg:block">
              {group.label}
            </p>
            {group.items.map((key) => (
              <NavButton
                key={key}
                icon={ICONS[key]}
                label={CRM_RESOURCES[key].label}
                active={tab === key}
                onClick={() => onChange(key)}
              />
            ))}
          </div>
        ))}
      </div>
    </nav>
  );
}

function NavButton({
  icon,
  label,
  active,
  onClick,
}: {
  icon: ReactNode;
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-current={active ? "page" : undefined}
      className={clsx(
        "flex items-center gap-2.5 whitespace-nowrap rounded-md px-2.5 py-1.5 text-[13px] font-medium transition-colors",
        active
          ? "bg-surface-900 text-white shadow-[0_1px_2px_rgb(26_24_21_/_0.08)]"
          : "text-surface-600 hover:bg-surface-100 hover:text-surface-900",
      )}
    >
      <span
        className={clsx(
          "flex h-4 w-4 items-center justify-center",
          active ? "text-white/80" : "text-surface-400",
        )}
      >
        {icon}
      </span>
      {label}
    </button>
  );
}

/* -------------------------------- Overview -------------------------------- */

function CrmOverview({ onOpen }: { onOpen: (tab: CrmTab) => void }) {
  const [stats, setStats] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiFetch<Record<string, unknown>>("/crm/overview");
      setStats(data);
    } catch (problem) {
      setError(errorMessage(problem));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="mx-auto w-full max-w-[1400px] px-4 py-6 sm:px-6 lg:px-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-[10px] font-medium uppercase tracking-[0.16em] text-surface-400">
            Customer relationships
          </p>
          <h1 className="mt-1 text-xl font-semibold tracking-[-0.02em] text-surface-900">
            Revenue at a glance
          </h1>
          <p className="mt-1 max-w-2xl text-sm text-surface-500">
            Every lead, conversation and order in one calm workspace.
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => void load()}
          icon={<RefreshIcon className={clsx("h-3.5 w-3.5", loading && "animate-spin")} />}
        >
          Refresh
        </Button>
      </header>

      {error ? <ErrorBanner className="mt-5" message={error} onRetry={() => void load()} /> : null}

      <div className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi
          label="Open pipeline"
          value={stats ? formatMoney(stats.pipelineValue) : undefined}
          hint={stats ? `${asNumber(stats.deals)} deals in play` : undefined}
          loading={loading}
        />
        <Kpi
          label="Won revenue"
          value={stats ? formatMoney(stats.wonValue) : undefined}
          hint="Closed-won deals"
          loading={loading}
        />
        <Kpi
          label="Open tasks"
          value={stats ? String(asNumber(stats.openTasks)) : undefined}
          hint="Across the team"
          loading={loading}
        />
        <Kpi
          label="Upcoming meetings"
          value={stats ? String(asNumber(stats.upcomingMeetings)) : undefined}
          hint="Scheduled from now"
          loading={loading}
        />
      </div>

      <section className="mt-8">
        <h2 className="text-[13px] font-semibold tracking-[-0.005em] text-surface-900">
          Your modules
        </h2>
        <div className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-5">
          {(Object.keys(CRM_RESOURCES) as CrmKey[]).map((key) => {
            const config = CRM_RESOURCES[key];
            const count = stats ? asNumber(stats[key]) : 0;
            return (
              <button
                key={key}
                type="button"
                onClick={() => onOpen(key)}
                className="group flex items-start gap-3 rounded-card border border-surface-200 bg-white p-4 text-left shadow-card transition-all hover:-translate-y-0.5 hover:border-surface-300 hover:shadow-lift"
              >
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-surface-200 bg-surface-50 text-surface-600 transition-colors group-hover:border-surface-300 group-hover:bg-surface-100 group-hover:text-surface-900">
                  {ICONS[key]}
                </span>
                <span className="min-w-0 flex-1">
                  <div className="flex items-baseline justify-between gap-2">
                    <div className="truncate text-[13px] font-medium text-surface-900">
                      {config.label}
                    </div>
                    {loading ? (
                      <Skeleton className="h-4 w-6" />
                    ) : (
                      <span className="text-sm font-semibold tabular-nums text-surface-500">{count}</span>
                    )}
                  </div>
                  <p className="mt-0.5 truncate text-[11.5px] text-surface-500">
                    {config.description}
                  </p>
                </span>
                <ArrowRightIcon className="mt-1 h-3.5 w-3.5 shrink-0 text-surface-300 transition-transform group-hover:translate-x-0.5 group-hover:text-surface-600" />
              </button>
            );
          })}
        </div>
      </section>
    </div>
  );
}

function Kpi({
  label,
  value,
  hint,
  loading,
}: {
  label: string;
  value?: string;
  hint?: string;
  loading: boolean;
}) {
  return (
    <div className="rounded-card border border-surface-200 bg-white px-5 py-4 shadow-card">
      <p className="text-[10.5px] font-medium uppercase tracking-[0.12em] text-surface-400">{label}</p>
      {loading ? (
        <Skeleton className="mt-2.5 h-7 w-28" />
      ) : (
        <p className="mt-1.5 text-2xl font-semibold tracking-[-0.02em] tabular-nums text-surface-900">
          {value ?? "—"}
        </p>
      )}
      {hint ? <p className="mt-1 text-[11.5px] text-surface-500">{hint}</p> : null}
    </div>
  );
}
