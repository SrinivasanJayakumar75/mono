"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";

import { apiFetch } from "./api";
import { useAuth } from "./auth";

export type BillingStatus =
  | "TRIALING"
  | "PENDING"
  | "ACTIVE"
  | "PAST_DUE"
  | "CANCELLED"
  | "EXPIRED";

export type BillingInterval = "MONTHLY" | "YEARLY";

export interface BillingPlan {
  interval: BillingInterval;
  label: string;
  amount: number;
  currency: string;
  description: string;
  recommended: boolean;
}

export interface BillingSummary {
  workspaceId: string;
  status: BillingStatus;
  interval: BillingInterval | null;
  provider: string;
  trialEndsAt: string | null;
  currentPeriodEnd: string | null;
  cancelAtPeriodEnd: boolean;
  access: boolean;
  daysLeft: number | null;
  configured: boolean;
  webhookConfigured: boolean;
  testMode: boolean;
  canManage: boolean;
  shortUrl: string | null;
  trialDays: number;
  plans: BillingPlan[];
}

export interface CheckoutSession {
  subscriptionId: string;
  keyId: string;
  shortUrl: string | null;
  name: string;
  description: string;
  prefillName: string;
  prefillEmail: string;
  interval: BillingInterval;
  amount: number;
  currency: string;
}

export interface VerifyPaymentPayload {
  razorpay_payment_id: string;
  razorpay_subscription_id: string;
  razorpay_signature: string;
}

interface BillingContextValue {
  billing: BillingSummary | null;
  loading: boolean;
  error: string | null;
  refresh: () => Promise<BillingSummary | null>;
}

const BillingContext = createContext<BillingContextValue | null>(null);

export function BillingProvider({ children }: { children: ReactNode }) {
  const { status, workspace } = useAuth();
  const [billing, setBilling] = useState<BillingSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const mounted = useRef(true);
  const requestId = useRef(0);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const workspaceId = workspace?.id ?? null;

  const refresh = useCallback(async (): Promise<BillingSummary | null> => {
    if (status !== "authenticated" || !workspaceId) {
      if (mounted.current) {
        setBilling(null);
        setLoading(false);
        setError(null);
      }
      return null;
    }

    const id = ++requestId.current;
    if (mounted.current) {
      setLoading(true);
      setError(null);
    }
    try {
      const data = await apiFetch<BillingSummary>("/billing");
      if (mounted.current && id === requestId.current) {
        setBilling(data);
        setLoading(false);
      }
      return data;
    } catch (err) {
      if (mounted.current && id === requestId.current) {
        setLoading(false);
        setError(err instanceof Error ? err.message : "Could not load billing details.");
      }
      return null;
    }
  }, [status, workspaceId]);

  useEffect(() => {
    if (status === "authenticated" && workspaceId) {
      void refresh();
    } else {
      setBilling(null);
      setLoading(false);
      setError(null);
    }
  }, [status, workspaceId, refresh]);

  const value = useMemo<BillingContextValue>(
    () => ({ billing, loading, error, refresh }),
    [billing, loading, error, refresh],
  );

  return <BillingContext.Provider value={value}>{children}</BillingContext.Provider>;
}

export function useBilling(): BillingContextValue {
  const ctx = useContext(BillingContext);
  if (!ctx) {
    throw new Error("useBilling must be used inside <BillingProvider>");
  }
  return ctx;
}
