"use client";

import clsx from "clsx";
import { useCallback, useEffect, useId, useState } from "react";

import {
  CartIcon,
  CloseIcon,
  DownloadIcon,
  FilterIcon,
  PencilIcon,
  PlusIcon,
  RefreshIcon,
  SearchIcon,
  TrashIcon,
  UploadIcon,
} from "@/components/icons";
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorBanner,
  Input,
  Modal,
  Select,
  Skeleton,
  Textarea,
  useToast,
} from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api";
import { formatDate, formatDateTime } from "@/lib/format";
import { downloadCsv, toCsv } from "@/lib/csv";
import {
  asBool,
  asNumber,
  asText,
  type CrmColumnKind,
  type CrmFieldConfig,
  type CrmRecord,
  type CrmResourceConfig,
  type CrmTone,
} from "@/lib/crm";

import { CrmImportModal } from "./crm-import";
import { DealBoard } from "./deal-board";
import { LeadProfileModal } from "./lead-profile";
import { OrderItemsModal } from "./order-items";

const PAGE_SIZE = 25;
const BOARD_LIMIT = 500;
const EXPORT_LIMIT = 10_000;

interface RelationOption {
  value: string;
  label: string;
}

export interface RelationOptions {
  companies: RelationOption[];
  leads: RelationOption[];
  products: RelationOption[];
}

const CURRENCY = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 2,
});

const HUMAN_LABELS: Record<string, string> = {
  NEW: "New",
  OPEN: "Open",
  IN_PROGRESS: "In progress",
  NO_ANSWER: "No answer",
  LEFT_MESSAGE: "Left a message",
  DONE: "Done",
  WON: "Won",
  LOST: "Lost",
  ACTIVE: "Active",
  INACTIVE: "Inactive",
  PENDING: "Pending",
  CONFIRMED: "Confirmed",
  SHIPPED: "Shipped",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
  SCHEDULED: "Scheduled",
  COMPLETED: "Completed",
  CONNECTED: "Connected",
  BUSY: "Busy",
  FAILED: "Failed",
  INBOUND: "Inbound",
  OUTBOUND: "Outbound",
};

function humanize(value: string): string {
  const known = HUMAN_LABELS[value];
  if (known) return known;
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return error instanceof Error ? error.message : "Something went wrong";
}

/** The backend reads `filter.<fieldKey>` so filters cannot collide with q/page/size. */
function filterParams(filters: Record<string, string>): Record<string, string> {
  const params: Record<string, string> = {};
  for (const [key, value] of Object.entries(filters)) {
    if (value) params[`filter.${key}`] = value;
  }
  return params;
}

function filterOptions(
  field: CrmFieldConfig,
  relations: RelationOptions,
): RelationOption[] {
  if (field.type === "relation" && field.relation) {
    return relations[field.relation];
  }
  if (field.type === "checkbox") {
    return [
      { value: "true", label: "Yes" },
      { value: "false", label: "No" },
    ];
  }
  return field.options ?? [];
}

function filterValueLabel(
  field: CrmFieldConfig,
  value: string,
  relations: RelationOptions,
): string {
  const option = filterOptions(field, relations).find(
    (candidate) => candidate.value === value,
  );
  return option?.label ?? humanize(value);
}

function exportValue(
  field: CrmFieldConfig,
  row: CrmRecord,
  relations: RelationOptions,
): string | number | boolean {
  const value = row[field.key];
  if (value === null || value === undefined) return "";

  if (field.type === "relation" && field.relation) {
    const id = asText(value);
    if (!id) return "";
    const option = relations[field.relation].find(
      (candidate) => candidate.value === id,
    );
    return option?.label ?? id;
  }

  if (field.type === "checkbox") return asBool(value);
  if (typeof value === "number" || typeof value === "boolean") return value;
  return asText(value);
}

export function CrmResourcePanel({
  config,
  relations,
  onRelationsChanged,
}: {
  config: CrmResourceConfig;
  relations: RelationOptions;
  onRelationsChanged?: () => void;
}) {
  const { toast } = useToast();

  const [items, setItems] = useState<CrmRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [movingId, setMovingId] = useState<string | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<CrmRecord | null>(null);
  const [profileRow, setProfileRow] = useState<CrmRecord | null>(null);
  const [orderForItems, setOrderForItems] = useState<CrmRecord | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebounced(query.trim());
      setPage(0);
    }, 280);
    return () => window.clearTimeout(timer);
  }, [query]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      if (config.key === "deals") {
        // A board needs the whole pipeline, not just the current table page.
        const all: CrmRecord[] = [];
        let matched = 0;
        let pageToLoad = 0;

        while (all.length < BOARD_LIMIT) {
          const data = await apiFetch<{ items: CrmRecord[]; total: number }>(
            `/crm/${config.key}`,
            {
              params: {
                q: debounced || undefined,
                page: pageToLoad,
                size: 200,
                ...filterParams(filters),
              },
            },
          );
          matched = data.total;
          all.push(...data.items);
          if (data.items.length === 0 || all.length >= data.total) break;
          pageToLoad += 1;
        }

        setItems(all);
        setTotal(matched);
      } else {
        const data = await apiFetch<{ items: CrmRecord[]; total: number }>(`/crm/${config.key}`, {
          params: {
            q: debounced || undefined,
            page,
            size: PAGE_SIZE,
            ...filterParams(filters),
          },
        });
        setItems(data.items);
        setTotal(data.total);
      }
    } catch (problem) {
      setError(errorMessage(problem));
    } finally {
      setLoading(false);
    }
  }, [config.key, debounced, page, filters]);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const dealStages =
    config.key === "deals"
      ? (config.fields.find((field) => field.key === "stage")?.options ?? [])
      : [];
  const filterFields = (config.filters ?? [])
    .map((key) => config.fields.find((field) => field.key === key))
    .filter((field): field is CrmFieldConfig => Boolean(field));
  const activeFilters = filterFields
    .filter((field) => Boolean(filters[field.key]))
    .map((field) => ({
      field,
      label: filterValueLabel(field, filters[field.key], relations),
    }));
  const activeFilterCount = activeFilters.length;
  const hasSearch = debounced.length > 0;

  function updateFilter(key: string, value: string) {
    setFilters((current) => {
      if (!value) {
        if (!(key in current)) return current;
        const next = { ...current };
        delete next[key];
        return next;
      }
      if (current[key] === value) return current;
      return { ...current, [key]: value };
    });
    setPage(0);
  }

  function clearFilters() {
    setFilters({});
    setPage(0);
  }

  function openCreate() {
    setEditing(null);
    setFormOpen(true);
  }

  function openEdit(row: CrmRecord) {
    setEditing(row);
    setFormOpen(true);
  }

  function openRow(row: CrmRecord) {
    if (config.key === "leads") {
      setProfileRow(row);
    } else {
      openEdit(row);
    }
  }

  async function onDelete(row: CrmRecord): Promise<boolean> {
    const label = config.columns
      .map((column) => asText(row[column.key]))
      .find((value) => value.length > 0);

    const confirmed = window.confirm(
      `Delete ${label ? `“${label}”` : `this ${config.singular.toLowerCase()}`}? This cannot be undone.`,
    );
    if (!confirmed) return false;

    const id = asText(row.id);
    setDeletingId(id);
    try {
      await apiFetch(`/crm/${config.key}/${id}`, { method: "DELETE" });
      toast(`${config.singular} deleted`, { tone: "success" });
      if (config.key === "companies" || config.key === "leads" || config.key === "products") {
        onRelationsChanged?.();
      }
      if (items.length === 1 && page > 0) {
        setPage((current) => current - 1);
      } else {
        setRefreshKey((key) => key + 1);
      }
      return true;
    } catch (problem) {
      toast(errorMessage(problem), { tone: "error" });
      return false;
    } finally {
      setDeletingId(null);
    }
  }

  async function moveDeal(deal: CrmRecord, stage: string) {
    const id = asText(deal.id);
    const previous = asText(deal.stage);
    if (!id || previous === stage) return;

    setItems((current) =>
      current.map((item) =>
        asText(item.id) === id ? { ...item, stage } : item,
      ),
    );
    setMovingId(id);
    try {
      await apiFetch(`/crm/deals/${id}`, {
        method: "PATCH",
        json: { stage },
      });
      const label =
        dealStages.find((candidate) => candidate.value === stage)?.label ??
        humanize(stage);
      toast(`Deal moved to ${label}`, { tone: "success" });
    } catch (problem) {
      setItems((current) =>
        current.map((item) =>
          asText(item.id) === id ? { ...item, stage: previous } : item,
        ),
      );
      toast(errorMessage(problem), { tone: "error" });
    } finally {
      setMovingId(null);
    }
  }

  async function exportCsv() {
    setExporting(true);
    try {
      const rows: CrmRecord[] = [];
      let matched = 0;
      let pageToLoad = 0;

      while (rows.length < EXPORT_LIMIT) {
        const data = await apiFetch<{ items: CrmRecord[]; total: number }>(
          `/crm/${config.key}`,
          {
            params: {
              q: debounced || undefined,
              page: pageToLoad,
              size: 200,
              ...filterParams(filters),
            },
          },
        );
        matched = data.total;
        rows.push(...data.items);
        if (data.items.length === 0 || rows.length >= data.total) break;
        pageToLoad += 1;
      }

      if (rows.length === 0) {
        toast("No records to export", { tone: "error" });
        return;
      }

      const headers = config.fields.map((field) => field.label);
      const body = rows.map((row) =>
        config.fields.map((field) => exportValue(field, row, relations)),
      );
      downloadCsv(
        `${config.key}-${new Date().toISOString().slice(0, 10)}.csv`,
        toCsv([headers, ...body]),
      );

      const capped = rows.length < matched;
      toast(
        capped
          ? `Exported the first ${rows.length.toLocaleString()} of ${matched.toLocaleString()} ${config.label.toLowerCase()}`
          : `Exported ${rows.length.toLocaleString()} ${
              rows.length === 1
                ? config.singular.toLowerCase()
                : config.label.toLowerCase()
            }`,
        { tone: "success" },
      );
    } catch (problem) {
      toast(errorMessage(problem), { tone: "error" });
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="mx-auto w-full max-w-[1500px] px-4 py-6 sm:px-6 lg:px-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0">
          <p className="text-[10px] font-medium uppercase tracking-[0.16em] text-surface-400">
            CRM
          </p>
          <h1 className="mt-1 text-xl font-semibold tracking-[-0.02em] text-surface-900">
            {config.label}
          </h1>
          <p className="mt-1 max-w-2xl text-sm text-surface-500">{config.description}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            onClick={() => setImportOpen(true)}
            icon={<UploadIcon className="h-4 w-4" />}
          >
            Import
          </Button>
          <Button
            variant="outline"
            onClick={() => void exportCsv()}
            loading={exporting}
            icon={<DownloadIcon className="h-4 w-4" />}
            title="Export all records matching the current search and filters"
          >
            Export
          </Button>
          <Button onClick={openCreate} icon={<PlusIcon className="h-4 w-4" />}>
            New {config.singular.toLowerCase()}
          </Button>
        </div>
      </header>

      <Card className="mt-5" padded={false}>
        <div className="flex flex-wrap items-center gap-2.5 border-b border-surface-100 px-4 py-3">
          <div className="relative w-full sm:max-w-xs">
            <SearchIcon className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-surface-400" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={`Search ${config.label.toLowerCase()}…`}
              aria-label={`Search ${config.label.toLowerCase()}`}
              className="h-9 w-full rounded-md border border-surface-300 bg-white pl-9 pr-3 text-[13px] text-surface-900 placeholder:text-surface-400 transition-colors focus:border-surface-500 focus:outline-none focus:ring-[3px] focus:ring-brand-900/[0.07]"
            />
            {query ? (
              <button
                type="button"
                onClick={() => setQuery("")}
                aria-label="Clear search"
                className="absolute right-1.5 top-1/2 -translate-y-1/2 rounded p-1 text-surface-400 hover:bg-surface-100 hover:text-surface-700"
              >
                <CloseIcon className="h-3.5 w-3.5" />
              </button>
            ) : null}
          </div>

          {filterFields.length > 0 ? (
            <Button
              variant={activeFilterCount > 0 ? "secondary" : "outline"}
              onClick={() => setFiltersOpen((open) => !open)}
              icon={<FilterIcon className="h-4 w-4" />}
              aria-expanded={filtersOpen}
              aria-controls="crm-filters"
            >
              Filters
              {activeFilterCount > 0 ? (
                <span className="rounded-full bg-brand-900 px-1.5 py-0.5 text-[10px] font-semibold leading-none text-white">
                  {activeFilterCount}
                </span>
              ) : null}
            </Button>
          ) : null}

          <div className="ml-auto flex items-center gap-2">
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setRefreshKey((key) => key + 1)}
              aria-label="Refresh"
              title="Refresh"
            >
              <RefreshIcon className={clsx("h-4 w-4", loading && "animate-spin")} />
            </Button>
            <span className="text-xs tabular-nums text-surface-500">
              {total} {total === 1 ? config.singular.toLowerCase() : config.label.toLowerCase()}
            </span>
          </div>
        </div>

        {filtersOpen && filterFields.length > 0 ? (
          <div
            id="crm-filters"
            className="border-b border-surface-100 bg-surface-50/50 px-4 py-3"
          >
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {filterFields.map((field) => (
                <Select
                  key={field.key}
                  label={field.label}
                  value={filters[field.key] ?? ""}
                  onChange={(event) => updateFilter(field.key, event.target.value)}
                  placeholder="All"
                  options={filterOptions(field, relations)}
                />
              ))}
            </div>
            <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
              <p className="text-[11px] text-surface-400">
                Filters apply instantly to the list and export.
              </p>
              <div className="flex items-center gap-2">
                {activeFilterCount > 0 ? (
                  <Button variant="ghost" size="sm" onClick={clearFilters}>
                    Clear all
                  </Button>
                ) : null}
                <Button variant="outline" size="sm" onClick={() => setFiltersOpen(false)}>
                  Done
                </Button>
              </div>
            </div>
          </div>
        ) : null}

        {activeFilterCount > 0 ? (
          <div className="flex flex-wrap items-center gap-1.5 border-b border-surface-100 bg-white px-4 py-2">
            <span className="text-[10px] font-medium uppercase tracking-[0.12em] text-surface-400">
              Filtered by
            </span>
            {activeFilters.map(({ field, label }) => (
              <button
                key={field.key}
                type="button"
                onClick={() => updateFilter(field.key, "")}
                aria-label={`Remove ${field.label} filter`}
                className="inline-flex items-center gap-1 rounded-full border border-surface-200 bg-surface-50 px-2 py-0.5 text-[11px] text-surface-600 transition-colors hover:border-surface-300 hover:bg-surface-100"
              >
                <span className="text-surface-400">{field.label}:</span>
                <span className="font-medium text-surface-700">{label}</span>
                <CloseIcon className="h-3 w-3 text-surface-400" />
              </button>
            ))}
            {activeFilterCount > 1 ? (
              <button
                type="button"
                onClick={clearFilters}
                className="ml-1 text-[11px] font-medium text-surface-500 underline-offset-2 hover:text-surface-900 hover:underline"
              >
                Clear all
              </button>
            ) : null}
          </div>
        ) : null}

        {error ? (
          <div className="p-4">
            <ErrorBanner message={error} onRetry={() => setRefreshKey((key) => key + 1)} />
          </div>
        ) : null}

        {config.key === "deals" ? (
          <DealBoard
            deals={items}
            stages={dealStages}
            loading={loading}
            movingId={movingId}
            deletingId={deletingId}
            onOpen={openEdit}
            onDelete={onDelete}
            onMove={(deal, stage) => void moveDeal(deal, stage)}
          />
        ) : loading && items.length === 0 ? (
          <div className="space-y-2 p-4">
            {Array.from({ length: 6 }).map((_, index) => (
              <Skeleton key={index} className="h-10 w-full" />
            ))}
          </div>
        ) : items.length === 0 && !error ? (
          <div className="p-4">
            <EmptyState
              title={
                activeFilterCount > 0
                  ? `No ${config.label.toLowerCase()} match these filters`
                  : hasSearch
                    ? `No ${config.label.toLowerCase()} match “${debounced}”`
                    : `No ${config.label.toLowerCase()} yet`
              }
              description={
                activeFilterCount > 0
                  ? "Try removing a filter or broadening your search."
                  : hasSearch
                    ? "Try a different search term."
                    : config.emptyDescription
              }
              action={
                activeFilterCount > 0 ? (
                  <Button variant="outline" onClick={clearFilters}>
                    Clear filters
                  </Button>
                ) : hasSearch ? (
                  <Button variant="outline" onClick={() => setQuery("")}>
                    Clear search
                  </Button>
                ) : (
                  <Button onClick={openCreate} icon={<PlusIcon className="h-4 w-4" />}>
                    New {config.singular.toLowerCase()}
                  </Button>
                )
              }
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] border-collapse text-left">
              <thead>
                <tr className="border-b border-surface-100 bg-surface-50/60">
                  {config.columns.map((column) => (
                    <th
                      key={column.key}
                      scope="col"
                      className={clsx(
                        "whitespace-nowrap px-4 py-2.5 text-[10.5px] font-medium uppercase tracking-[0.1em] text-surface-400",
                        column.width,
                      )}
                    >
                      {column.label}
                    </th>
                  ))}
                  <th scope="col" className="w-24 px-4 py-2.5">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody className={clsx(loading && "opacity-60")}>
                {items.map((row) => (
                  <tr
                    key={asText(row.id)}
                    tabIndex={0}
                    onClick={() => openRow(row)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" && event.target === event.currentTarget) {
                        openRow(row);
                      }
                    }}
                    title={config.key === "leads" ? "Open lead profile" : undefined}
                    className="group cursor-pointer border-b border-surface-100 last:border-b-0 hover:bg-surface-50/70 focus:bg-surface-50 focus:outline-none"
                  >
                    {config.columns.map((column) => (
                      <td
                        key={column.key}
                        className={clsx(
                          "max-w-[280px] truncate px-4 py-2.5 text-[13px] text-surface-700",
                          column.width,
                        )}
                      >
                        {renderCell(row[column.key], column.kind, column.tone)}
                      </td>
                    ))}
                    <td className="px-2 py-1.5">
                      <div className="flex items-center justify-end gap-0.5 opacity-60 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
                        {config.key === "orders" ? (
                          <button
                            type="button"
                            onClick={(event) => {
                              event.stopPropagation();
                              setOrderForItems(row);
                            }}
                            aria-label="Manage line items"
                            title="Line items"
                            className="rounded-md p-1.5 text-surface-500 transition-colors hover:bg-surface-200 hover:text-surface-900"
                          >
                            <CartIcon className="h-4 w-4" />
                          </button>
                        ) : null}
                        <button
                          type="button"
                          onClick={(event) => {
                            event.stopPropagation();
                            openEdit(row);
                          }}
                          aria-label="Edit"
                          title="Edit"
                          className="rounded-md p-1.5 text-surface-500 transition-colors hover:bg-surface-200 hover:text-surface-900"
                        >
                          <PencilIcon className="h-3.5 w-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={(event) => {
                            event.stopPropagation();
                            void onDelete(row);
                          }}
                          disabled={deletingId === asText(row.id)}
                          aria-label="Delete"
                          title="Delete"
                          className="rounded-md p-1.5 text-surface-400 transition-colors hover:bg-rose-50 hover:text-rose-600 disabled:opacity-40"
                        >
                          <TrashIcon className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <footer className="flex flex-wrap items-center justify-between gap-3 border-t border-surface-100 px-4 py-3">
          {config.key === "deals" ? (
            <>
              <p className="text-xs text-surface-500">
                {loading
                  ? "Loading the pipeline…"
                  : total === 0
                    ? activeFilterCount > 0
                      ? "No deals match the current filters."
                      : hasSearch
                        ? "No deals match the current search."
                        : "No deals yet. Create one to start the pipeline."
                    : items.length < total
                      ? `Showing ${items.length.toLocaleString()} of ${total.toLocaleString()} deals. Narrow the search or filters to see the rest.`
                      : "Drag a card to change its stage. Click a card to edit."}
              </p>
              <p className="text-xs tabular-nums text-surface-400">
                {items.length.toLocaleString()} {items.length === 1 ? "card" : "cards"}
              </p>
            </>
          ) : (
            <>
              <p className="text-xs text-surface-500">
                Page {Math.min(page + 1, totalPages)} of {totalPages}
              </p>
              <div className="flex items-center gap-1.5">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0 || loading}
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                >
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page + 1 >= totalPages || loading}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Next
                </Button>
              </div>
            </>
          )}
        </footer>
      </Card>

      {profileRow ? (
        <LeadProfileModal
          lead={profileRow}
          onClose={() => setProfileRow(null)}
          onEdit={(row) => {
            setProfileRow(null);
            openEdit(row);
          }}
          onDelete={onDelete}
        />
      ) : null}

      {formOpen ? (
        <CrmForm
          config={config}
          record={editing}
          relations={relations}
          onClose={() => setFormOpen(false)}
          onSaved={() => {
            setFormOpen(false);
            setRefreshKey((key) => key + 1);
            if (config.key === "companies" || config.key === "leads" || config.key === "products") {
              onRelationsChanged?.();
            }
          }}
        />
      ) : null}

      {orderForItems ? (
        <OrderItemsModal
          order={orderForItems}
          products={relations.products}
          onClose={() => setOrderForItems(null)}
          onChanged={() => setRefreshKey((key) => key + 1)}
        />
      ) : null}

      {importOpen ? (
        <CrmImportModal
          config={config}
          relations={relations}
          onClose={() => setImportOpen(false)}
          onImported={() => {
            setRefreshKey((key) => key + 1);
            if (config.key === "companies" || config.key === "leads" || config.key === "products") {
              onRelationsChanged?.();
            }
          }}
        />
      ) : null}
    </div>
  );
}

/* --------------------------------- cells ---------------------------------- */

function renderCell(
  value: unknown,
  kind: CrmColumnKind | undefined,
  tone?: Record<string, CrmTone>,
) {
  if (kind === "badge") {
    const text = asText(value);
    if (!text) return <span className="text-surface-300">—</span>;
    return <Badge variant={tone?.[text] ?? "neutral"}>{humanize(text)}</Badge>;
  }
  if (kind === "money") {
    const amount = asNumber(value);
    return (
      <span className="tabular-nums">
        {amount === 0 ? <span className="text-surface-300">—</span> : CURRENCY.format(amount)}
      </span>
    );
  }
  if (kind === "date") {
    const text = asText(value);
    return text ? formatDate(text) : <span className="text-surface-300">—</span>;
  }
  if (kind === "datetime") {
    const text = asText(value);
    return text ? formatDateTime(text) : <span className="text-surface-300">—</span>;
  }
  if (kind === "number") {
    return <span className="tabular-nums">{asNumber(value)}</span>;
  }
  if (kind === "bool") {
    return asBool(value) ? (
      <Badge variant="success">Active</Badge>
    ) : (
      <Badge variant="neutral">Inactive</Badge>
    );
  }
  const text = asText(value);
  return text ? text : <span className="text-surface-300">—</span>;
}

/* ---------------------------------- form ---------------------------------- */

function CrmForm({
  config,
  record,
  relations,
  onClose,
  onSaved,
}: {
  config: CrmResourceConfig;
  record: CrmRecord | null;
  relations: RelationOptions;
  onClose: () => void;
  onSaved: () => void;
}) {
  const formId = useId();
  const { toast } = useToast();
  const [values, setValues] = useState<Record<string, string | boolean>>(() =>
    initialValues(config, record),
  );
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const title = record
    ? `Edit ${config.singular.toLowerCase()}`
    : `New ${config.singular.toLowerCase()}`;

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setFormError(null);

    const nextErrors: Record<string, string> = {};
    for (const field of config.fields) {
      if (field.required && field.type !== "checkbox") {
        const value = values[field.key];
        if (typeof value !== "string" || value.trim() === "") {
          nextErrors[field.key] = `${field.label} is required`;
        }
      }
    }
    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors);
      return;
    }
    setErrors({});

    const payload: Record<string, unknown> = {};
    for (const field of config.fields) {
      const value = values[field.key];
      if (field.type === "checkbox") {
        payload[field.key] = Boolean(value);
        continue;
      }
      const text = typeof value === "string" ? value.trim() : "";
      if (text === "") {
        if (record && !field.required) payload[field.key] = null;
        continue;
      }
      if (field.type === "number" || field.type === "money") {
        payload[field.key] = Number(text);
      } else {
        payload[field.key] = text;
      }
    }

    setSaving(true);
    try {
      if (record) {
        await apiFetch(`/crm/${config.key}/${asText(record.id)}`, {
          method: "PATCH",
          json: payload,
        });
        toast(`${config.singular} updated`, { tone: "success" });
      } else {
        await apiFetch(`/crm/${config.key}`, { method: "POST", json: payload });
        toast(`${config.singular} created`, { tone: "success" });
      }
      onSaved();
    } catch (problem) {
      setFormError(errorMessage(problem));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={title}
      description={record ? "Update the details below." : config.description}
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form={formId} loading={saving}>
            {record ? "Save changes" : `Create ${config.singular.toLowerCase()}`}
          </Button>
        </>
      }
    >
      <form id={formId} onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
        {formError ? <ErrorBanner className="sm:col-span-2" message={formError} /> : null}
        {config.fields.map((field) => (
          <FieldRenderer
            key={field.key}
            field={field}
            value={values[field.key]}
            error={errors[field.key]}
            relations={relations}
            onChange={(next) =>
              setValues((current) => ({
                ...current,
                [field.key]: next,
              }))
            }
          />
        ))}
      </form>
    </Modal>
  );
}

function FieldRenderer({
  field,
  value,
  error,
  relations,
  onChange,
}: {
  field: CrmFieldConfig;
  value: string | boolean | undefined;
  error?: string;
  relations: RelationOptions;
  onChange: (value: string | boolean) => void;
}) {
  const span = field.full ? "sm:col-span-2" : undefined;

  if (field.type === "checkbox") {
    return (
      <label
        className={clsx(
          "flex items-center gap-2.5 rounded-md border border-surface-200 bg-surface-50/60 px-3 py-2.5",
          span,
        )}
      >
        <input
          type="checkbox"
          checked={Boolean(value)}
          onChange={(event) => onChange(event.target.checked)}
          className="h-4 w-4 rounded border-surface-300 text-brand-900 focus:ring-brand-900/20"
        />
        <span className="text-[13px] font-medium text-surface-700">{field.label}</span>
      </label>
    );
  }

  if (field.type === "textarea") {
    return (
      <Textarea
        label={field.label}
        required={field.required}
        error={error}
        hint={field.hint}
        className={span}
        rows={3}
        value={typeof value === "string" ? value : ""}
        onChange={(event) => onChange(event.target.value)}
        placeholder={field.placeholder}
      />
    );
  }

  if (field.type === "select" || field.type === "relation") {
    const options =
      field.type === "relation" && field.relation
        ? relations[field.relation]
        : field.options ?? [];
    return (
      <Select
        label={field.label}
        required={field.required}
        error={error}
        hint={field.hint}
        className={span}
        value={typeof value === "string" ? value : ""}
        onChange={(event) => onChange(event.target.value)}
        placeholder={field.placeholder ?? "Select…"}
        options={options}
      />
    );
  }

  const inputType =
    field.type === "money" ? "number" : field.type === "datetime" ? "datetime-local" : field.type ?? "text";

  return (
    <Input
      label={field.label}
      required={field.required}
      error={error}
      hint={field.hint}
      className={span}
      type={inputType}
      step={field.type === "money" ? "0.01" : undefined}
      min={field.type === "number" || field.type === "money" ? 0 : undefined}
      value={typeof value === "string" ? value : ""}
      onChange={(event) => onChange(event.target.value)}
      placeholder={field.placeholder}
    />
  );
}

/* -------------------------------- helpers --------------------------------- */

function initialValues(
  config: CrmResourceConfig,
  record: CrmRecord | null,
): Record<string, string | boolean> {
  const values: Record<string, string | boolean> = {};
  for (const field of config.fields) {
    const raw = record?.[field.key];

    if (field.type === "checkbox") {
      values[field.key] = record ? asBool(raw) : field.defaultValue === true;
      continue;
    }

    if (record && raw !== null && raw !== undefined && asText(raw) !== "") {
      if (field.type === "datetime") {
        values[field.key] = toDateTimeInput(raw);
      } else if (field.type === "date") {
        values[field.key] = asText(raw).slice(0, 10);
      } else {
        values[field.key] = asText(raw);
      }
      continue;
    }

    values[field.key] =
      !record && typeof field.defaultValue === "string" ? field.defaultValue : "";
  }
  return values;
}

function toDateTimeInput(value: unknown): string {
  const text = asText(value);
  if (!text) return "";
  const date = new Date(text);
  if (Number.isNaN(date.getTime())) return text.slice(0, 16);
  const pad = (part: number) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`;
}
