"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { GoogleAuthButton } from "@/components/google-auth-button";
import { AlertIcon, ArrowRightIcon } from "@/components/icons";
import { MonotronLogo } from "@/components/logo";
import { Button, ErrorBanner, Input } from "@/components/ui";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";

export default function LoginPage() {
  const router = useRouter();
  const { login, status } = useAuth();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (status === "authenticated") {
      router.replace("/inbox");
    }
  }, [status, router]);

  const handleGoogleError = useCallback((message: string) => {
    setFieldErrors({});
    setFormError(message);
  }, []);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    setFieldErrors({});
    setSubmitting(true);
    try {
      await login({ email: email.trim(), password });
      router.replace("/inbox");
    } catch (err) {
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors ?? {});
        setFormError(err.message || "Unable to sign in.");
      } else {
        setFormError("Something went wrong. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-surface-50 px-4 py-10">
      <div className="pointer-events-none absolute inset-0 mn-mesh" aria-hidden="true" />

      <div className="relative w-full max-w-sm">
        <div className="mb-7 text-center">
          <MonotronLogo
            href="/"
            markClassName="h-8 w-8 rounded-md"
            wordmarkClassName="text-[15px]"
          />
          <h1 className="mt-7 text-xl font-semibold tracking-[-0.02em] text-surface-900">
            Welcome back
          </h1>
          <p className="mt-1.5 text-[13px] text-surface-500">
            Sign in to pick up where your team left off.
          </p>
        </div>

        <div className="rounded-card border border-surface-200 bg-white p-6 shadow-card">
          {formError ? (
            <ErrorBanner message={formError} className="mb-5" />
          ) : null}

          <GoogleAuthButton intent="LOGIN" onError={handleGoogleError} />

          <form onSubmit={onSubmit} className="space-y-3.5" noValidate>
            <Input
              label="Work email"
              type="email"
              name="email"
              autoComplete="email"
              required
              placeholder="you@company.com"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              error={fieldErrors.email ?? fieldErrors["email"] ?? null}
            />

            <Input
              label="Password"
              type="password"
              name="password"
              autoComplete="current-password"
              required
              placeholder="••••••••"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              error={fieldErrors.password ?? null}
            />

            <Button type="submit" loading={submitting} className="w-full" size="lg">
              {submitting ? "Signing in…" : "Sign in"}
              {!submitting ? <ArrowRightIcon className="h-4 w-4" /> : null}
            </Button>
          </form>

          <p className="mt-5 text-center text-[12.5px] text-surface-500">
            New to Monotron?{" "}
            <Link
              href="/register"
              className="font-medium text-brand-700 hover:text-brand-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
            >
              Create an account
            </Link>
          </p>
        </div>

        <p className="mt-6 flex items-start gap-2 text-[11px] leading-relaxed text-surface-400">
          <AlertIcon className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          By signing in you agree to your workspace policies. Sessions refresh automatically.
        </p>
      </div>
    </div>
  );
}
