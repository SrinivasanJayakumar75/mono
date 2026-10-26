"use client";

import { useCallback, useEffect, useState } from "react";

import { PlusIcon, TrashIcon } from "@/components/icons";
import {
  Button,
  ErrorBanner,
  Modal,
  Select,
  Skeleton,
  useToast,
} from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api";
import { asNumber, asText, type CrmRecord } from "@/lib/crm";

const CURRENCY = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 2,
});

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return error instanceof Error ? error.message : "Something went wrong";
}

export function OrderItemsModal({
  order,
  products,
  onClose,
  onChanged,
}: {
  order: CrmRecord;
  products: { value: string; label: string }[];
  onClose: () => void;
  onChanged: () => void;
}) {
  const { toast } = useToast();
  const orderId = asText(order.id);

  const [items, setItems] = useState<CrmRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [removingId, setRemovingId] = useState<string | null>(null);
  const [productId, setProductId] = useState("");
  const [quantity, setQuantity] = useState("1");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiFetch<CrmRecord[]>(`/crm/orders/${orderId}/items`);
      setItems(data);
    } catch (problem) {
      setError(errorMessage(problem));
    } finally {
      setLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    if (orderId) void load();
  }, [load, orderId]);

  const total = items.reduce(
    (sum, item) => sum + asNumber(item.quantity) * asNumber(item.unitPrice),
    0,
  );

  async function addItem() {
    if (!productId) return;
    const parsedQuantity = Number(quantity);
    if (!Number.isFinite(parsedQuantity) || parsedQuantity < 1) {
      toast("Quantity must be at least 1", { tone: "error" });
      return;
    }

    setAdding(true);
    try {
      await apiFetch(`/crm/orders/${orderId}/items`, {
        method: "POST",
        json: { productId, quantity: Math.floor(parsedQuantity) },
      });
      setProductId("");
      setQuantity("1");
      toast("Product added to order", { tone: "success" });
      await load();
      onChanged();
    } catch (problem) {
      toast(errorMessage(problem), { tone: "error" });
    } finally {
      setAdding(false);
    }
  }

  async function removeItem(item: CrmRecord) {
    const itemId = asText(item.id);
    setRemovingId(itemId);
    try {
      await apiFetch(`/crm/orders/${orderId}/items/${itemId}`, { method: "DELETE" });
      await load();
      onChanged();
    } catch (problem) {
      toast(errorMessage(problem), { tone: "error" });
    } finally {
      setRemovingId(null);
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={`Line items · ${asText(order.orderNumber) || "Order"}`}
      description="Add catalogue products to this order. Line totals update automatically."
      size="lg"
      footer={
        <>
          <span className="mr-auto text-[13px] text-surface-500">
            Order total{" "}
            <span className="ml-1 text-sm font-semibold tabular-nums text-surface-900">
              {CURRENCY.format(total)}
            </span>
          </span>
          <Button variant="ghost" onClick={onClose}>
            Done
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {error ? <ErrorBanner message={error} onRetry={() => void load()} /> : null}

        <div className="rounded-md border border-surface-200 bg-surface-50/60 p-3">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
            <div className="min-w-0 flex-1">
              <Select
                label="Product"
                value={productId}
                onChange={(event) => setProductId(event.target.value)}
                placeholder={products.length ? "Select a product…" : "No products yet"}
                options={products}
              />
            </div>
            <div className="w-full sm:w-28">
              <label className="mb-1.5 block text-[12.5px] font-medium text-surface-600">
                Quantity
              </label>
              <input
                type="number"
                min={1}
                value={quantity}
                onChange={(event) => setQuantity(event.target.value)}
                className="h-[38px] w-full rounded-md border border-surface-300 bg-white px-3 text-[13.5px] text-surface-900 focus:border-surface-500 focus:outline-none focus:ring-[3px] focus:ring-brand-900/[0.07]"
              />
            </div>
            <Button
              onClick={() => void addItem()}
              disabled={!productId}
              loading={adding}
              icon={<PlusIcon className="h-4 w-4" />}
            >
              Add
            </Button>
          </div>
        </div>

        {loading && items.length === 0 ? (
          <div className="space-y-2">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : items.length === 0 ? (
          <p className="rounded-md border border-dashed border-surface-300 px-4 py-8 text-center text-[13px] text-surface-500">
            No line items yet. Pick a product above to start this order.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[420px] border-collapse text-left">
              <thead>
                <tr className="border-b border-surface-100">
                  <th className="px-2 py-2 text-[10.5px] font-medium uppercase tracking-[0.1em] text-surface-400">
                    Product
                  </th>
                  <th className="px-2 py-2 text-right text-[10.5px] font-medium uppercase tracking-[0.1em] text-surface-400">
                    Qty
                  </th>
                  <th className="px-2 py-2 text-right text-[10.5px] font-medium uppercase tracking-[0.1em] text-surface-400">
                    Unit price
                  </th>
                  <th className="px-2 py-2 text-right text-[10.5px] font-medium uppercase tracking-[0.1em] text-surface-400">
                    Total
                  </th>
                  <th className="w-10 px-2 py-2" />
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={asText(item.id)} className="border-b border-surface-100 last:border-b-0">
                    <td className="max-w-[220px] truncate px-2 py-2.5 text-[13px] text-surface-800">
                      {asText(item.productName)}
                    </td>
                    <td className="px-2 py-2.5 text-right text-[13px] tabular-nums text-surface-600">
                      {asNumber(item.quantity)}
                    </td>
                    <td className="px-2 py-2.5 text-right text-[13px] tabular-nums text-surface-600">
                      {CURRENCY.format(asNumber(item.unitPrice))}
                    </td>
                    <td className="px-2 py-2.5 text-right text-[13px] font-medium tabular-nums text-surface-900">
                      {CURRENCY.format(asNumber(item.quantity) * asNumber(item.unitPrice))}
                    </td>
                    <td className="px-1 py-2.5 text-right">
                      <button
                        type="button"
                        onClick={() => void removeItem(item)}
                        disabled={removingId === asText(item.id)}
                        aria-label="Remove line item"
                        title="Remove"
                        className="rounded p-1.5 text-surface-400 transition-colors hover:bg-rose-50 hover:text-rose-600 disabled:opacity-40"
                      >
                        <TrashIcon className="h-3.5 w-3.5" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </Modal>
  );
}
