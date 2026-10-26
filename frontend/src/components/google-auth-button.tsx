"use client";

import { useEffect, useRef, useState } from "react";

import { ApiError, apiFetch } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { GoogleAuthConfig, GoogleAuthIntent } from "@/lib/types";

interface GoogleCredentialResponse {
  credential?: string;
  select_by?: string;
}

interface GoogleIdentityServices {
  initialize(options: {
    client_id: string;
    callback: (response: GoogleCredentialResponse) => void;
    ux_mode?: "popup" | "redirect";
  }): void;
  renderButton(
    parent: HTMLElement,
    options: {
      type?: "standard";
      theme?: "outline" | "filled_blue" | "filled_black";
      size?: "large" | "medium" | "small";
      text?: "signin_with" | "signup_with" | "continue_with" | "signin";
      shape?: "rectangular" | "pill" | "circle" | "square";
      logo_alignment?: "left" | "center";
      width?: number;
    },
  ): void;
}

declare global {
  interface Window {
    google?: {
      accounts?: {
        id?: GoogleIdentityServices;
      };
    };
  }
}

let googleScriptPromise: Promise<void> | null = null;

function loadGoogleIdentity(): Promise<void> {
  if (typeof window === "undefined") return Promise.resolve();
  if (window.google?.accounts?.id) return Promise.resolve();
  if (googleScriptPromise) return googleScriptPromise;

  googleScriptPromise = new Promise((resolve, reject) => {
    document
      .querySelectorAll<HTMLScriptElement>('script[src="https://accounts.google.com/gsi/client"]')
      .forEach((script) => script.remove());

    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => {
      googleScriptPromise = null;
      reject(new Error("Unable to load Google"));
    };
    document.head.appendChild(script);
  });

  return googleScriptPromise;
}

interface GoogleAuthButtonProps {
  intent: GoogleAuthIntent;
  workspaceName?: string;
  onError: (message: string) => void;
}

/**
 * Official Google Identity Services button. It stays hidden unless the backend
 * reports a configured GOOGLE_CLIENT_ID, so email/password auth keeps working
 * in environments where Google sign-in has not been set up yet.
 */
export function GoogleAuthButton({ intent, workspaceName, onError }: GoogleAuthButtonProps) {
  const { googleAuth } = useAuth();
  const containerRef = useRef<HTMLDivElement>(null);
  const onErrorRef = useRef(onError);
  const workspaceNameRef = useRef(workspaceName);
  const [config, setConfig] = useState<GoogleAuthConfig | null>(null);
  const [ready, setReady] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [scriptError, setScriptError] = useState(false);

  useEffect(() => {
    onErrorRef.current = onError;
  }, [onError]);

  useEffect(() => {
    workspaceNameRef.current = workspaceName;
  }, [workspaceName]);

  useEffect(() => {
    let cancelled = false;
    apiFetch<GoogleAuthConfig>("/auth/google/config", { auth: false, skipRefresh: true })
      .then((data) => {
        if (!cancelled) setConfig(data);
      })
      .catch(() => {
        if (!cancelled) setConfig({ enabled: false, clientId: null });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!config?.enabled || !config.clientId) return;
    let cancelled = false;

    loadGoogleIdentity()
      .then(() => {
        if (cancelled) return;
        if (!containerRef.current || !window.google?.accounts?.id) {
          setScriptError(true);
          return;
        }
        const googleId = window.google.accounts.id;
        googleId.initialize({
          client_id: config.clientId!,
          ux_mode: "popup",
          callback: async (response) => {
            if (!response.credential) {
              onErrorRef.current("Google did not return a credential. Please try again.");
              return;
            }
            setSubmitting(true);
            try {
              await googleAuth({
                credential: response.credential,
                intent,
                workspaceName: workspaceNameRef.current?.trim() || undefined,
              });
            } catch (err) {
              onErrorRef.current(
                err instanceof ApiError ? err.message : "Unable to sign in with Google.",
              );
            } finally {
              setSubmitting(false);
            }
          },
        });

        containerRef.current.replaceChildren();
        const width = Math.max(
          200,
          Math.min(336, containerRef.current.clientWidth || 336),
        );
        googleId.renderButton(containerRef.current, {
          type: "standard",
          theme: "outline",
          size: "large",
          text: intent === "REGISTER" ? "signup_with" : "signin_with",
          shape: "rectangular",
          logo_alignment: "left",
          width,
        });
        if (!cancelled) setReady(true);
      })
      .catch(() => {
        if (!cancelled) setScriptError(true);
      });

    return () => {
      cancelled = true;
    };
  }, [config, intent, googleAuth]);

  if (!config?.enabled || !config.clientId) return null;

  if (scriptError) {
    return (
      <div className="mb-5 rounded-md border border-dashed border-surface-300 bg-surface-50 px-3 py-2 text-center text-[11.5px] text-surface-500">
        Google sign-in is unavailable right now. Use your email and password instead.
      </div>
    );
  }

  return (
    <div>
      <div className="relative">
        <div ref={containerRef} className="flex min-h-10 justify-center" />
        {!ready || submitting ? (
          <div className="pointer-events-none absolute inset-0 flex items-center justify-center rounded-md bg-white/80">
            <span className="text-[11.5px] font-medium text-surface-500">
              {submitting ? "Signing you in…" : "Loading Google…"}
            </span>
          </div>
        ) : null}
      </div>

      <div className="relative my-5">
        <div className="absolute inset-0 flex items-center" aria-hidden="true">
          <span className="w-full border-t border-surface-200" />
        </div>
        <div className="relative flex justify-center">
          <span className="bg-white px-3 text-[10px] font-semibold uppercase tracking-[0.14em] text-surface-400">
            or continue with email
          </span>
        </div>
      </div>
    </div>
  );
}
