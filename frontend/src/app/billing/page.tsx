"use client";

import clsx from "clsx";
import { useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";

import {
  AlertIcon,
  ArrowRightIcon,
  CheckIcon,
  CreditCardIcon,
  LogoutIcon,
} from "@/components/icons";
import { MonotronLogo } from "@/components/logo";
import {
  Badge,
  Button,
  ErrorBanner,
  Modal,
  Skeleton,
  Spinner,
  useToast,
} from "@/components/ui";
import { apiFetch } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import {
  useBilling,
  type BillingInterval,
  type BillingPlan,
  type BillingSummary,
  type CheckoutSession,
} from "@/lib/billing";
import { formatDate, formatMoney } from "@/lib/format";

type BadgeVariant = "neutral" | "brand" | "success" | "warning" | "danger" | "info";

interface RazorpayResponse {
  razorpay_payment_id: string;
  razorpay_subscription_id: string;
  razorpay_signature: string;
}

interface RazorpayOptions {
  key: string;
  subscription_id: string;
  name: string;
  description?: string;
  prefill?: { name?: string; email?: string };
  notes?: Record<string, string>;
  theme?: { color?: string };
  handler: (response: RazorpayResponse) => void;
  modal?: { ondismiss?: () => void };
}

interface RazorpayInstance {
  open: () => void;
  on: (event: string, handler: (...args: unknown[]) => void) => void;
}

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayOptions) => RazorpayInstance;
  }
}

let razorpayScript: Promise<void> | null = null;

function loadRazorpayScript(): Promise<void> {
  if (typeof window === "undefined") {
    return Promise.reject(new Error("Razorpay Checkout is only available in the browser."));
  }
  if (window.Razorpay) return Promise.resolve();
  if (razorpayScript) return razorpayScript;

  razorpayScript = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      razorpayScript = null;
      reject(new Error("Could not load Razorpay Checkout. Check your connection and try again."));
    };
    document.body.appendChild(script);
  });
  return razorpayScript;
}

function statusMeta(billing: BillingSummary): {
  variant: BadgeVariant;
  label: string;
  heading: string;
  message: string;
} {
  switch (billing.status) {
    case "TRIALING":
      return {
        variant: "warning",
        label: "Trial",
        heading: "Your free trial is active",
        message:
          billing.daysLeft === null
            ? `Your trial ends on ${formatDate(billing.trialEndsAt)}.`
            : `${billing.daysLeft} ${billing.daysLeft === 1 ? "day" : "days"} left · ends ${formatDate(
                billing.trialEndsAt,
              )}.`,
      };
    case "ACTIVE":
      return {
        variant: "success",
        label: "Active",
        heading: "Subscription active",
        message: billing.cancelAtPeriodEnd
          ? `Your plan will end on ${formatDate(billing.currentPeriodEnd)}.`
          : `Your plan renews on ${formatDate(billing.currentPeriodEnd)}.`,
      };
    case "PENDING":
      return {
        variant: "info",
        label: "Payment pending",
        heading: "Finish your checkout",
        message:
          billing.daysLeft !== null
            ? `Your trial still has ${billing.daysLeft} ${
                billing.daysLeft === 1 ? "day" : "days"
              } left while you complete payment.`
            : "Complete your Razorpay checkout to activate the workspace.",
      };
    case "CANCELLED":
      return {
        variant: "info",
        label: "Cancelled",
        heading: "Subscription cancelled",
        message: `Access continues until ${formatDate(billing.currentPeriodEnd)}.`,
      };
    case "PAST_DUE":
      return {
        variant: "danger",
        label: "Payment issue",
        heading: "Payment needs attention",
        message: `We could not confirm the latest payment. Access continues until ${formatDate(
          billing.currentPeriodEnd,
        )}.`,
      };
    case "EXPIRED": {
      const subscriptionEnded =
        billing.trialEndsAt === null && billing.currentPeriodEnd !== null;
      return {
        variant: "danger",
        label: subscriptionEnded ? "Ended" : "Trial ended",
        heading: subscriptionEnded
          ? "Your subscription has ended"
          : "Your trial has ended",
        message:
          "Choose a plan below to restore access to your inbox, CRM and knowledge base.",
      };
    }
    default:
      return {
        variant: "neutral",
        label: "Billing",
        heading: "Billing",
        message: "Manage your Monotron subscription.",
      };
  }
}

export default function BillingPage() {
  const router = useRouter();
  const { user, workspace, status, logout } = useAuth();
  const { billing, loading, error, refresh } = useBilling();
  const { toast } = useToast();

  const [checkingOut, setCheckingOut] = useState<BillingInterval | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login");
    }
  }, [status, router]);

  async function onLogout() {
    await logout();
    router.replace("/login");
  }

  async function completePayment(response: RazorpayResponse) {
    setVerifying(true);
    setLocalError(null);
    try {
      const updated = await apiFetch<BillingSummary>("/billing/verify", {
        method: "POST",
        json: {
          razorpay_payment_id: response.razorpay_payment_id,
          razorpay_subscription_id: response.razorpay_subscription_id,
          razorpay_signature: response.razorpay_signature,
        },
      });
      await refresh();
      toast("Subscription active", {
        tone: "success",
        description: "Your workspace is ready to use.",
      });
      if (updated.access) {
        router.replace("/inbox");
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not verify the payment.";
      setLocalError(message);
      toast(message, { tone: "error" });
    } finally {
      setVerifying(false);
      setCheckingOut(null);
    }
  }

  async function startCheckout(interval: BillingInterval) {
    if (!billing?.configured) {
      toast("Razorpay is not configured yet.", { tone: "error" });
      return;
    }
    setCheckingOut(interval);
    setLocalError(null);
    try {
      const session = await apiFetch<CheckoutSession>("/billing/checkout", {
        method: "POST",
        json: { interval },
      });
      await loadRazorpayScript();
      if (!window.Razorpay) {
        throw new Error("Razorpay Checkout did not load.");
      }

      const checkout = new window.Razorpay({
        key: session.keyId,
        subscription_id: session.subscriptionId,
        name: session.name,
        description: session.description,
        prefill: { name: session.prefillName, email: session.prefillEmail },
        notes: { workspace_id: billing.workspaceId },
        theme: { color: "#1e1b4b" },
        handler: (response) => {
          void completePayment(response);
        },
        modal: {
          ondismiss: () => {
            setCheckingOut(null);
            toast("Checkout closed", { tone: "info" });
          },
        },
      });
      checkout.on("payment.failed", () => {
        setCheckingOut(null);
        toast("Payment failed. Please try again.", { tone: "error" });
      });
      checkout.open();
    } catch (err) {
      setCheckingOut(null);
      const message = err instanceof Error ? err.message : "Could not start checkout.";
      setLocalError(message);
      toast(message, { tone: "error" });
    }
  }

  async function confirmCancel() {
    setCancelling(true);
    setLocalError(null);
    try {
      await apiFetch<BillingSummary>("/billing/cancel", { method: "POST" });
      await refresh();
      setCancelOpen(false);
      toast("Subscription cancelled", {
        tone: "success",
        description: "You keep access until the end of the current billing period.",
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not cancel the subscription.";
      setLocalError(message);
      toast(message, { tone: "error" });
    } finally {
      setCancelling(false);
    }
  }

  if (status !== "authenticated") {
    return <FullPageSpinner message="Loading your workspace…" />;
  }

  if (loading && !billing) {
    return <BillingSkeleton />;
  }

  if (error && !billing) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-surface-50 px-6 text-center">
        <div className="w-full max-w-md space-y-4">
          <p className="text-sm font-medium text-surface-800">Could not load billing</p>
          <ErrorBanner message={error} onRetry={() => void refresh()} />
          <Button onClick={() => void refresh()}>Try again</Button>
        </div>
      </div>
    );
  }

  if (!billing) {
    return <FullPageSpinner message="Loading billing…" />;
  }

  const meta = statusMeta(billing);
  const monthly = billing.plans.find((plan) => plan.interval === "MONTHLY");
  const yearly = billing.plans.find((plan) => plan.interval === "YEARLY");
  const yearlySavings =
    monthly && yearly ? Math.max(0, monthly.amount * 12 - yearly.amount) : 0;
  const currency = monthly?.currency ?? "INR";

  return (
    <div className="relative min-h-screen overflow-hidden bg-surface-50">
      <div
        className="pointer-events-none absolute inset-x-0 top-0 h-[28rem] bg-[radial-gradient(60%_80%_at_50%_0%,rgba(79,70,229,0.09),transparent_70%)]"
        aria-hidden="true"
      />

      <header className="relative border-b border-surface-200 bg-surface-50/80 backdrop-blur-md">
        <div className="mx-auto flex h-14 max-w-5xl items-center gap-3 px-4 sm:px-6">
          <MonotronLogo
            href={billing.access ? "/inbox" : "/billing"}
            markClassName="h-7 w-7 rounded-md"
            wordmarkClassName="text-[14px]"
            className="rounded-md focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
          />
          <span className="hidden text-surface-300 sm:inline">/</span>
          <span className="hidden text-[13px] font-medium text-surface-500 sm:inline">Billing</span>

          <div className="ml-auto flex items-center gap-1.5">
            {billing.access ? (
              <Button
                variant="ghost"
                size="sm"
                icon={<ArrowRightIcon className="h-3.5 w-3.5" />}
                onClick={() => router.push("/inbox")}
              >
                Back to inbox
              </Button>
            ) : null}
            <Button
              variant="ghost"
              size="sm"
              icon={<LogoutIcon className="h-3.5 w-3.5" />}
              onClick={() => void onLogout()}
            >
              Sign out
            </Button>
          </div>
        </div>
      </header>

      <main className="relative mx-auto max-w-5xl px-4 py-10 sm:px-6 lg:py-14">
        <div className="max-w-2xl">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-brand-700">
              Billing
            </p>
            {billing.testMode ? <Badge variant="warning">Razorpay test mode</Badge> : null}
          </div>
          <h1 className="mt-2 text-3xl font-semibold tracking-[-0.04em] text-surface-900 sm:text-4xl">
            Simple plans for {workspace?.name ?? "your workspace"}
          </h1>
          <p className="mt-3 text-[14px] leading-6 text-surface-600">
            Start with a {billing.trialDays}-day free trial. Pick monthly or yearly billing when
            you&apos;re ready — cancel anytime.
          </p>
        </div>

        {localError ? (
          <ErrorBanner
            className="mt-6"
            message={localError}
            onRetry={() => setLocalError(null)}
          />
        ) : null}
        {error ? (
          <ErrorBanner className="mt-6" message={error} onRetry={() => void refresh()} />
        ) : null}

        <section className="mt-7 rounded-card border border-surface-200 bg-white p-5 shadow-[0_1px_2px_rgb(26_24_21_/_0.04)]">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="flex min-w-0 items-start gap-3">
              <span
                className={clsx(
                  "flex h-9 w-9 shrink-0 items-center justify-center rounded-md",
                  meta.variant === "success"
                    ? "bg-emerald-50 text-emerald-700"
                    : meta.variant === "danger"
                      ? "bg-rose-50 text-rose-700"
                      : meta.variant === "warning"
                        ? "bg-amber-50 text-amber-700"
                        : "bg-surface-100 text-surface-600",
                )}
              >
                <CreditCardIcon className="h-4 w-4" />
              </span>
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="text-[15px] font-semibold tracking-[-0.01em] text-surface-900">
                    {meta.heading}
                  </h2>
                  <Badge variant={meta.variant}>{meta.label}</Badge>
                </div>
                <p className="mt-1 text-[13px] leading-5 text-surface-600">{meta.message}</p>
                {billing.status === "TRIALING" ? (
                  <div className="mt-3 h-1.5 w-full max-w-xs overflow-hidden rounded-full bg-amber-100">
                    <div
                      className="h-full rounded-full bg-amber-500 transition-[width]"
                      style={{
                        width: `${
                          billing.trialDays > 0
                            ? Math.min(
                                100,
                                Math.max(
                                  0,
                                  ((billing.trialDays - (billing.daysLeft ?? 0)) /
                                    billing.trialDays) *
                                    100,
                                ),
                              )
                            : 100
                        }%`,
                      }}
                    />
                  </div>
                ) : null}
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              {billing.status === "PENDING" && billing.shortUrl ? (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    window.open(billing.shortUrl ?? "", "_blank", "noopener,noreferrer")
                  }
                >
                  Complete payment
                </Button>
              ) : null}
              {billing.status === "ACTIVE" && billing.canManage && !billing.cancelAtPeriodEnd ? (
                <Button variant="ghost" size="sm" onClick={() => setCancelOpen(true)}>
                  Cancel at period end
                </Button>
              ) : null}
            </div>
          </div>
        </section>

        {!billing.configured || !billing.webhookConfigured ? (
          <div className="mt-5 rounded-lg border border-amber-200 bg-amber-50/70 p-4 text-[13px] text-amber-900">
            <div className="flex items-start gap-3">
              <AlertIcon className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
              <div className="min-w-0">
                <p className="font-medium">Online payments are temporarily unavailable</p>
                <p className="mt-1 text-amber-800">
                  Please try again shortly or contact support if this continues.
                </p>
              </div>
            </div>
          </div>
        ) : null}

        <div className="mt-8 grid gap-4 md:grid-cols-2">
          {billing.plans.map((plan) => (
            <PlanCard
              key={plan.interval}
              plan={plan}
              billing={billing}
              checkingOut={checkingOut}
              verifying={verifying}
              yearlySavings={yearlySavings}
              onSubscribe={(interval) => void startCheckout(interval)}
            />
          ))}
        </div>

        {!billing.canManage ? (
          <p className="mt-4 text-center text-[12.5px] text-surface-500">
            Only workspace admins can start or cancel a subscription. You can still use the
            workspace while it is active.
          </p>
        ) : null}

        <p className="mt-8 text-center text-[11.5px] leading-5 text-surface-500">
          Payments are processed securely by Razorpay. Prices are in {currency}. Cancel at the end
          of your billing period; access continues until then.
        </p>

        <p className="mt-2 text-center text-[11.5px] text-surface-400">
          Signed in as {user?.email ?? "—"} · {workspace?.name ?? "—"}
        </p>
      </main>

      <Modal
        open={cancelOpen}
        onClose={() => setCancelOpen(false)}
        title="Cancel subscription"
        description="Your plan stays active until the end of the current billing period."
        footer={
          <>
            <Button variant="ghost" onClick={() => setCancelOpen(false)}>
              Keep plan
            </Button>
            <Button
              variant="danger"
              loading={cancelling}
              onClick={() => void confirmCancel()}
            >
              Cancel at period end
            </Button>
          </>
        }
      >
        <p className="text-sm leading-6 text-surface-600">
          You will keep full access until{" "}
          <span className="font-medium text-surface-800">
            {formatDate(billing.currentPeriodEnd)}
          </span>
          . After that, the workspace will be locked until you subscribe again.
        </p>
      </Modal>
    </div>
  );
}

function PlanCard({
  plan,
  billing,
  checkingOut,
  verifying,
  yearlySavings,
  onSubscribe,
}: {
  plan: BillingPlan;
  billing: BillingSummary;
  checkingOut: BillingInterval | null;
  verifying: boolean;
  yearlySavings: number;
  onSubscribe: (interval: BillingInterval) => void;
}) {
  const isCurrent =
    billing.interval === plan.interval &&
    ["ACTIVE", "CANCELLED", "PAST_DUE", "PENDING"].includes(billing.status);
  const activeElsewhere =
    !isCurrent && ["ACTIVE", "CANCELLED", "PAST_DUE"].includes(billing.status);

  let label = `Start ${plan.label.toLowerCase()}`;
  let disabled = false;
  let action: (() => void) | undefined = () => onSubscribe(plan.interval);

  if (isCurrent) {
    if (billing.status === "PENDING") {
      if (billing.shortUrl) {
        label = "Complete payment";
        action = () =>
          window.open(billing.shortUrl ?? "", "_blank", "noopener,noreferrer");
      } else {
        label = "Start new checkout";
        action = () => onSubscribe(plan.interval);
      }
    } else {
      label = "Current plan";
      disabled = true;
      action = undefined;
    }
  } else if (activeElsewhere) {
    label = "Plan already active";
    disabled = true;
    action = undefined;
  } else if (!billing.configured) {
    label = "Payments unavailable";
    disabled = true;
    action = undefined;
  } else if (!billing.canManage) {
    label = "Admin required";
    disabled = true;
    action = undefined;
  } else if (billing.status === "PENDING") {
    label = `Switch to ${plan.label.toLowerCase()}`;
  }

  const loading = checkingOut === plan.interval || (verifying && isCurrent);
  const buttonDisabled = disabled || checkingOut !== null || verifying;

  return (
    <div
      className={clsx(
        "relative flex flex-col rounded-card border bg-white p-5 shadow-[0_1px_2px_rgb(26_24_21_/_0.04)]",
        plan.recommended
          ? "border-brand-300 ring-1 ring-brand-200"
          : "border-surface-200",
      )}
    >
      {plan.recommended ? (
        <span className="absolute -top-2.5 left-5 rounded-full bg-brand-900 px-2.5 py-1 text-[9px] font-semibold uppercase tracking-[0.14em] text-white">
          Best value
        </span>
      ) : null}

      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-[13.5px] font-semibold text-surface-900">{plan.label}</p>
          <p className="mt-0.5 text-[12px] text-surface-500">{plan.description}</p>
        </div>
        {isCurrent ? (
          <Badge variant={billing.status === "CANCELLED" ? "info" : "success"}>
            {billing.status === "PENDING" ? "Selected" : billing.status === "CANCELLED" ? "Cancelled" : "Current"}
          </Badge>
        ) : plan.recommended && yearlySavings > 0 ? (
          <Badge variant="brand">Save {formatMoney(yearlySavings, plan.currency)}</Badge>
        ) : null}
      </div>

      <div className="mt-4 flex items-baseline gap-1">
        <span className="text-[30px] font-semibold tracking-[-0.04em] text-surface-900">
          {formatMoney(plan.amount, plan.currency)}
        </span>
        <span className="text-[13px] text-surface-500">
          /{plan.interval === "MONTHLY" ? "month" : "year"}
        </span>
      </div>
      {plan.interval === "YEARLY" && yearlySavings > 0 ? (
        <p className="mt-1 text-[11.5px] font-medium text-emerald-700">
          Two months free compared with monthly billing
        </p>
      ) : (
        <p className="mt-1 text-[11.5px] text-surface-400">Billed every month</p>
      )}

      <ul className="mt-4 space-y-2 text-[12.5px] leading-5 text-surface-600">
        <PlanFeature>Full access to Inbox, CRM, Knowledge and Contacts</PlanFeature>
        <PlanFeature>AI replies and the messenger widget</PlanFeature>
        <PlanFeature>Unlimited teammates</PlanFeature>
        <PlanFeature>Secure recurring billing by Razorpay</PlanFeature>
      </ul>

      <Button
        className="mt-5 w-full"
        variant={plan.recommended ? "primary" : "outline"}
        loading={loading}
        disabled={buttonDisabled}
        onClick={action}
      >
        {label}
      </Button>
    </div>
  );
}

function PlanFeature({ children }: { children: ReactNode }) {
  return (
    <li className="flex items-start gap-2">
      <CheckIcon className="mt-0.5 h-3.5 w-3.5 shrink-0 text-emerald-600" />
      <span>{children}</span>
    </li>
  );
}

function FullPageSpinner({ message }: { message: string }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-3 bg-surface-50">
      <Spinner className="h-7 w-7 text-brand-600" />
      <p className="text-sm text-surface-500">{message}</p>
    </div>
  );
}

function BillingSkeleton() {
  return (
    <div className="min-h-screen bg-surface-50">
      <header className="border-b border-surface-200">
        <div className="mx-auto flex h-14 max-w-5xl items-center gap-2.5 px-4 sm:px-6">
          <MonotronLogo
            markClassName="h-7 w-7 rounded-md"
            wordmarkClassName="text-[14px]"
          />
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-10 sm:px-6 lg:py-14">
        <div className="max-w-2xl space-y-3">
          <Skeleton className="h-3 w-20" />
          <Skeleton className="h-9 w-full max-w-lg" />
          <Skeleton className="h-4 w-full max-w-md" />
        </div>
        <div className="mt-7 space-y-4">
          <Skeleton className="h-28 w-full rounded-card" />
          <div className="grid gap-4 md:grid-cols-2">
            <Skeleton className="h-80 w-full rounded-card" />
            <Skeleton className="h-80 w-full rounded-card" />
          </div>
        </div>
      </main>
    </div>
  );
}
