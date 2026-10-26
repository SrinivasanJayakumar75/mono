"use client";

import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";

import { AppShell } from "@/components/app-shell";
import { Button, Spinner } from "@/components/ui";
import { useAuth } from "@/lib/auth";
import { useBilling } from "@/lib/billing";

export default function AuthenticatedLayout({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const { billing, loading, error, refresh } = useBilling();
  const router = useRouter();

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login");
    }
  }, [status, router]);

  useEffect(() => {
    if (status === "authenticated" && !loading && billing && !billing.access) {
      router.replace("/billing");
    }
  }, [status, loading, billing, router]);

  if (status !== "authenticated") {
    return <LoadingScreen message="Loading your workspace…" />;
  }

  if (loading && !billing) {
    return <LoadingScreen message="Checking your subscription…" />;
  }

  if (error && !billing) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-surface-50 px-6 text-center">
        <div className="space-y-1">
          <p className="text-sm font-medium text-surface-800">Could not check your subscription</p>
          <p className="max-w-sm text-sm text-surface-500">{error}</p>
        </div>
        <Button onClick={() => void refresh()}>Try again</Button>
      </div>
    );
  }

  if (!billing) {
    return <LoadingScreen message="Checking your subscription…" />;
  }

  if (!billing.access) {
    return <LoadingScreen message="Taking you to billing…" />;
  }

  return <AppShell>{children}</AppShell>;
}

function LoadingScreen({ message }: { message: string }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-3 bg-surface-50">
      <Spinner className="h-7 w-7 text-brand-600" />
      <p className="text-sm text-surface-500">{message}</p>
    </div>
  );
}
