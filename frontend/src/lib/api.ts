/**
 * Typed API client for the Monotron backend.
 *
 * - prefixes every path with NEXT_PUBLIC_API_BASE_URL
 * - attaches the stored access token
 * - transparently refreshes the access token once on a 401 and retries the request
 * - throws {@link ApiError} for non-2xx responses (status / message / fieldErrors)
 */

import type { ApiErrorBody, AuthTokens } from "./types";

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/+$/, "") ?? "https://api.monotron.in";

export const ACCESS_TOKEN_KEY = "monotron.access";
export const REFRESH_TOKEN_KEY = "monotron.refresh";

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;
  readonly path?: string;

  constructor(
    message: string,
    status: number,
    fieldErrors: Record<string, string> = {},
    path?: string,
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.path = path;
  }
}

/* ------------------------------ token storage ----------------------------- */

function safeStorage(): Storage | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

export function getAccessToken(): string | null {
  return safeStorage()?.getItem(ACCESS_TOKEN_KEY) ?? null;
}

export function getRefreshToken(): string | null {
  return safeStorage()?.getItem(REFRESH_TOKEN_KEY) ?? null;
}

export function storeTokens(tokens: Pick<AuthTokens, "accessToken" | "refreshToken">): void {
  const storage = safeStorage();
  if (!storage) return;
  try {
    storage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    storage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  } catch {
    /* storage unavailable — ignore */
  }
}

export function clearTokens(): void {
  const storage = safeStorage();
  if (!storage) return;
  try {
    storage.removeItem(ACCESS_TOKEN_KEY);
    storage.removeItem(REFRESH_TOKEN_KEY);
  } catch {
    /* ignore */
  }
}

/* --------------------------------- helpers -------------------------------- */

const PUBLIC_PATHS = [
  "/auth/register",
  "/auth/login",
  "/auth/google",
  "/auth/google/config",
  "/auth/refresh",
];

function isPublicPath(path: string): boolean {
  const clean = path.startsWith("/api") ? path.slice(4) : path;
  return PUBLIC_PATHS.some((p) => clean === p || clean.startsWith(`${p}/`));
}

function isWidgetPath(path: string): boolean {
  const clean = path.startsWith("/api") ? path.slice(4) : path;
  return clean === "/widget" || clean.startsWith("/widget/");
}

function buildUrl(path: string, params?: Record<string, unknown>): string {
  const clean = path.startsWith("/api") ? path.slice(4) : path;
  const url = `${API_BASE_URL}${clean.startsWith("/") ? clean : `/${clean}`}`;
  if (!params) return url;
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") return;
    search.append(key, String(value));
  });
  const qs = search.toString();
  return qs ? `${url}?${qs}` : url;
}

/** Build the absolute API URL for streaming or other low-level requests. */
export function apiUrl(path: string, params?: Record<string, unknown>): string {
  return buildUrl(path, params);
}

async function parseError(response: Response, path: string): Promise<ApiError> {
  let body: ApiErrorBody = {};
  try {
    const text = await response.text();
    if (text) {
      try {
        body = JSON.parse(text) as ApiErrorBody;
      } catch {
        body = { message: text };
      }
    }
  } catch {
    body = {};
  }
  const message =
    body.message?.trim() ||
    body.error?.trim() ||
    `Request failed with status ${response.status}`;
  return new ApiError(message, response.status, body.fieldErrors ?? {}, body.path ?? path);
}

function redirectToLogin(): void {
  if (typeof window === "undefined") return;
  if (window.location.pathname === "/login") return;
  window.location.assign("/login");
}

function redirectToBilling(): void {
  if (typeof window === "undefined") return;
  if (window.location.pathname === "/billing") return;
  window.location.assign("/billing");
}

/* ------------------------------- token refresh ----------------------------- */

let refreshInFlight: Promise<string | null> | null = null;

async function performRefresh(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;
  try {
    const response = await fetch(buildUrl("/auth/refresh"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!response.ok) return null;
    const data = (await response.json()) as Partial<AuthTokens>;
    if (!data.accessToken) return null;
    storeTokens({
      accessToken: data.accessToken,
      refreshToken: data.refreshToken ?? refreshToken,
    });
    return data.accessToken;
  } catch {
    return null;
  }
}

export function refreshAccessToken(): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = performRefresh().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

/* -------------------------------- apiFetch -------------------------------- */

export interface ApiFetchOptions extends Omit<RequestInit, "body"> {
  body?: BodyInit | null;
  /** JSON-serialisable payload. Serialised automatically when `body` is absent. */
  json?: unknown;
  /** Query string parameters. `undefined`/`null`/`""` values are dropped. */
  params?: Record<string, unknown>;
  /** Set to false to skip the Authorization header (public widget endpoints). */
  auth?: boolean;
  /** Set to true to opt out of the automatic 401 refresh + retry. */
  skipRefresh?: boolean;
}

function buildHeaders(init: ApiFetchOptions, path: string, hasBody: boolean): Headers {
  const headers = new Headers(init.headers ?? {});
  if (hasBody && !headers.has("Content-Type") && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (init.auth !== false && !isWidgetPath(path)) {
    const token = getAccessToken();
    if (token) headers.set("Authorization", `Bearer ${token}`);
  }
  return headers;
}

async function rawRequest(
  path: string,
  init: ApiFetchOptions,
  tokenOverride: string | null,
): Promise<Response> {
  const url = buildUrl(path, init.params);
  const useJson = init.json !== undefined && init.body === undefined;
  const body = useJson ? JSON.stringify(init.json) : init.body;
  const headers = buildHeaders(init, path, body !== undefined && body !== null);
  if (tokenOverride) headers.set("Authorization", `Bearer ${tokenOverride}`);

  const { json: _json, params: _params, auth: _auth, skipRefresh: _skip, ...rest } = init;

  return fetch(url, {
    ...rest,
    method: init.method ?? (body === undefined || body === null ? "GET" : "POST"),
    headers,
    body: body === undefined || body === null ? undefined : body,
  });
}

/**
 * Perform an authenticated (or public) API request.
 * On a 401 the client refreshes the access token exactly once and retries once.
 */
export async function apiFetch<T = unknown>(
  path: string,
  init: ApiFetchOptions = {},
): Promise<T> {
  let response = await rawRequest(path, init, null);

  if (
    response.status === 401 &&
    !init.skipRefresh &&
    !isPublicPath(path) &&
    !isWidgetPath(path)
  ) {
    const newToken = await refreshAccessToken();
    if (newToken) {
      response = await rawRequest(path, init, newToken);
    } else {
      clearTokens();
      redirectToLogin();
      throw await parseError(response, path);
    }
  }

  if (!response.ok) {
    const error = await parseError(response, path);
    if (response.status === 402) {
      redirectToBilling();
    }
    throw error;
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) {
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }

  return (await response.json()) as T;
}

/** GET helper returning a Blob (used for protected PDF downloads). */
export async function apiBlob(path: string, init: ApiFetchOptions = {}): Promise<Blob> {
  const response = await rawRequest(path, { ...init, method: "GET" }, null);
  if (!response.ok) {
    const error = await parseError(response, path);
    if (response.status === 402) {
      redirectToBilling();
    }
    throw error;
  }
  return response.blob();
}

/** Build a query string from a plain object, dropping empty values. */
export function qs(params: Record<string, unknown>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") return;
    search.append(key, String(value));
  });
  return search.toString();
}
