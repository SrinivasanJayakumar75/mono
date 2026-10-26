"use client";

import clsx from "clsx";
import { useState } from "react";

import { PencilIcon, TrashIcon } from "@/components/icons";
import { Skeleton } from "@/components/ui";
import { asNumber, asText, type CrmRecord } from "@/lib/crm";
import { formatDate } from "@/lib/format";

const CURRENCY = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

const STAGE_BAR: Record<string, string> = {
  NEW: "bg-sky-300",
  QUALIFIED: "bg-indigo-300",
  PROPOSAL: "bg-amber-300",
  NEGOTIATION: "bg-orange-300",
  WON: "bg-emerald-300",
  LOST: "bg-rose-300",
};

const STAGE_DOT: Record<string, string> = {
  NEW: "bg-sky-400",
  QUALIFIED: "bg-indigo-400",
  PROPOSAL: "bg-amber-400",
  NEGOTIATION: "bg-orange-400",
  WON: "bg-emerald-500",
  LOST: "bg-rose-400",
};

export interface DealStage {
  value: string;
  label: string;
}

export function DealBoard({
  deals,
  stages,
  loading,
  movingId,
  deletingId,
  onOpen,
  onDelete,
  onMove,
}: {
  deals: CrmRecord[];
  stages: DealStage[];
  loading: boolean;
  movingId: string | null;
  deletingId: string | null;
  onOpen: (deal: CrmRecord) => void;
  onDelete: (deal: CrmRecord) => Promise<boolean>;
  onMove: (deal: CrmRecord, stage: string) => void;
}) {
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const [dragOverStage, setDragOverStage] = useState<string | null>(null);

  if (stages.length === 0) {
    return (
      <div className="p-6 text-sm text-surface-500">
        This module has no stages configured.
      </div>
    );
  }

  const grouped = new Map<string, CrmRecord[]>();
  for (const stage of stages) grouped.set(stage.value, []);
  for (const deal of deals) {
    const stage = asText(deal.stage) || stages[0].value;
    const bucket = grouped.get(stage) ?? grouped.get(stages[0].value);
    bucket?.push(deal);
  }

  function drop(stage: string, event: React.DragEvent<HTMLElement>) {
    event.preventDefault();
    const id = event.dataTransfer.getData("text/plain");
    setDragOverStage(null);
    setDraggingId(null);
    if (!id) return;
    const deal = deals.find((candidate) => asText(candidate.id) === id);
    if (deal) onMove(deal, stage);
  }

  return (
    <div className="overflow-x-auto px-4 pb-4 pt-3">
      <div
        className={clsx(
          "flex min-w-max items-stretch gap-3 transition-opacity",
          loading && deals.length > 0 && "opacity-60",
        )}
      >
        {stages.map((stage) => {
          const cards = grouped.get(stage.value) ?? [];
          const total = cards.reduce(
            (sum, deal) => sum + asNumber(deal.valueAmount),
            0,
          );
          const activeDrop = dragOverStage === stage.value;

          return (
            <section
              key={stage.value}
              aria-label={`${stage.label} deals`}
              onDragOver={(event) => {
                event.preventDefault();
                event.dataTransfer.dropEffect = "move";
                if (dragOverStage !== stage.value) setDragOverStage(stage.value);
              }}
              onDragEnter={(event) => {
                event.preventDefault();
                setDragOverStage(stage.value);
              }}
              onDragLeave={(event) => {
                const next = event.relatedTarget as Node | null;
                if (!next || !event.currentTarget.contains(next)) {
                  setDragOverStage(null);
                }
              }}
              onDrop={(event) => drop(stage.value, event)}
              className={clsx(
                "flex w-[276px] shrink-0 flex-col overflow-hidden rounded-lg border bg-surface-50/50 transition-colors",
                activeDrop
                  ? "border-brand-300 ring-2 ring-brand-500/10"
                  : "border-surface-200",
              )}
            >
              <div
                className={clsx(
                  "h-1 w-full",
                  STAGE_BAR[stage.value] ?? "bg-surface-300",
                )}
              />

              <header className="flex items-center justify-between gap-2 px-3 pb-2 pt-3">
                <div className="flex min-w-0 items-center gap-2">
                  <span
                    className={clsx(
                      "h-1.5 w-1.5 shrink-0 rounded-full",
                      STAGE_DOT[stage.value] ?? "bg-surface-400",
                    )}
                  />
                  <h2 className="truncate text-[11px] font-semibold uppercase tracking-[0.1em] text-surface-500">
                    {stage.label}
                  </h2>
                </div>
                <span className="rounded-full bg-white px-1.5 py-0.5 text-[10px] font-medium tabular-nums text-surface-500 ring-1 ring-surface-200">
                  {cards.length}
                </span>
              </header>

              <div className="flex flex-1 flex-col gap-2 px-2 pb-2">
                {loading && deals.length === 0 ? (
                  <>
                    <Skeleton className="h-[104px] w-full" />
                    <Skeleton className="h-[104px] w-full" />
                  </>
                ) : (
                  <>
                    {cards.map((deal) => (
                      <DealCard
                        key={asText(deal.id)}
                        deal={deal}
                        draggingId={draggingId}
                        movingId={movingId}
                        deletingId={deletingId}
                        onOpen={onOpen}
                        onDelete={onDelete}
                        onDragStart={(event) => {
                          const id = asText(deal.id);
                          event.dataTransfer.setData("text/plain", id);
                          event.dataTransfer.effectAllowed = "move";
                          setDraggingId(id);
                        }}
                        onDragEnd={() => {
                          setDraggingId(null);
                          setDragOverStage(null);
                        }}
                      />
                    ))}

                    {activeDrop ? (
                      <div className="rounded-md border border-dashed border-brand-300 bg-brand-50/60 px-3 py-3 text-center text-[11px] font-medium text-brand-700">
                        Drop here
                      </div>
                    ) : cards.length === 0 ? (
                      <div className="flex min-h-[88px] flex-1 items-center justify-center rounded-md border border-dashed border-surface-200 px-3 py-4 text-center text-[11px] text-surface-400">
                        No deals
                      </div>
                    ) : null}
                  </>
                )}
              </div>

              <footer className="flex items-center justify-between gap-2 border-t border-surface-200/70 px-3 py-2">
                <span className="text-[10px] uppercase tracking-[0.08em] text-surface-400">
                  Total
                </span>
                <span className="text-[11px] font-medium tabular-nums text-surface-600">
                  {total > 0 ? CURRENCY.format(total) : "—"}
                </span>
              </footer>
            </section>
          );
        })}
      </div>
    </div>
  );
}

function DealCard({
  deal,
  draggingId,
  movingId,
  deletingId,
  onOpen,
  onDelete,
  onDragStart,
  onDragEnd,
}: {
  deal: CrmRecord;
  draggingId: string | null;
  movingId: string | null;
  deletingId: string | null;
  onOpen: (deal: CrmRecord) => void;
  onDelete: (deal: CrmRecord) => Promise<boolean>;
  onDragStart: (event: React.DragEvent<HTMLElement>) => void;
  onDragEnd: () => void;
}) {
  const id = asText(deal.id);
  const title = asText(deal.title) || "Untitled deal";
  const company = asText(deal.companyName) || asText(deal.leadName);
  const value = asNumber(deal.valueAmount);
  const closeDate = asText(deal.expectedCloseDate);
  const isMoving = movingId === id;

  return (
    <article
      draggable={!isMoving}
      tabIndex={0}
      onClick={() => onOpen(deal)}
      onKeyDown={(event) => {
        if (event.target !== event.currentTarget) return;
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onOpen(deal);
        }
      }}
      onDragStart={onDragStart}
      onDragEnd={onDragEnd}
      title="Drag to change stage"
      aria-busy={isMoving}
      className={clsx(
        "group relative cursor-grab rounded-lg border border-surface-200 bg-white p-3 shadow-[0_1px_2px_rgb(26_24_21_/_0.04)] transition-all",
        "hover:-translate-y-0.5 hover:border-surface-300 hover:shadow-[0_8px_20px_-16px_rgb(26_24_21_/_0.35)]",
        "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500",
        "active:cursor-grabbing",
        draggingId === id && "opacity-40",
        isMoving && "opacity-70",
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <h3
          title={title}
          className="line-clamp-2 min-w-0 flex-1 text-[13px] font-medium leading-5 text-surface-900"
        >
          {title}
        </h3>
        <div
          className="flex shrink-0 items-center gap-0.5 opacity-60 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100"
          onPointerDown={(event) => event.stopPropagation()}
          onDragStart={(event) => {
            event.preventDefault();
            event.stopPropagation();
          }}
        >
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              onOpen(deal);
            }}
            aria-label={`Edit ${title}`}
            title="Edit"
            className="rounded-md p-1.5 text-surface-500 transition-colors hover:bg-surface-100 hover:text-surface-900"
          >
            <PencilIcon className="h-3.5 w-3.5" />
          </button>
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              void onDelete(deal);
            }}
            disabled={deletingId === id}
            aria-label={`Delete ${title}`}
            title="Delete"
            className="rounded-md p-1.5 text-surface-400 transition-colors hover:bg-rose-50 hover:text-rose-600 disabled:opacity-40"
          >
            <TrashIcon className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>

      <p className="mt-1 truncate text-[11.5px] text-surface-500">
        {company || "No company"}
      </p>

      <div className="mt-2.5 flex items-end justify-between gap-2">
        <span
          className={clsx(
            "text-[13px] font-semibold tabular-nums",
            value > 0 ? "text-surface-900" : "text-surface-300",
          )}
        >
          {value > 0 ? CURRENCY.format(value) : "—"}
        </span>
        {closeDate ? (
          <span className="text-[11px] tabular-nums text-surface-400">
            {formatDate(closeDate)}
          </span>
        ) : null}
      </div>
    </article>
  );
}
