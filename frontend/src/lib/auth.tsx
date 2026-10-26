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

import {
  ApiError,
  apiFetch,
  clearTokens,
  getAccessToken,
  storeTokens,
} from "./api";
import type {
  AuthResponse,
  AuthSession,
  GoogleAuthRequest,
  LoginRequest,
  RegisterRequest,
  Workspace,
} from "./types";

export type AuthStatus = "loading" | "authenticated" | "unauthenticated";

export interface AuthContextValue {
  user: AuthSession["user"] | null;
  workspace: Workspace | null;
  workspaces: Workspace[];
  status: AuthStatus;
  login: (credentials: LoginRequest) => Promise<void>;
  register: (payload: RegisterRequest) => Promise<void>;
  googleAuth: (payload: GoogleAuthRequest) => Promise<void>;
  logout: () => Promise<void>;
  switchWorkspace: (workspaceId: string) => Promise<void>;
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthSession["user"] | null>(null);
  const [workspace, setWorkspace] = useState<Workspace | null>(null);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [status, setStatus] = useState<AuthStatus>("loading");
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const loadSession = useCallback(async (): Promise<boolean> => {
    if (!getAccessToken()) {
      if (mounted.current) {
        setUser(null);
        setWorkspace(null);
        setWorkspaces([]);
        setStatus("unauthenticated");
      }
      return false;
    }
    try {
      const data = await apiFetch<AuthSession>("/auth/me");
      if (!mounted.current) return true;
      setUser(data.user);
      setWorkspace(data.activeWorkspace);
      setWorkspaces(data.workspaces ?? []);
      setStatus("authenticated");
      return true;
    } catch (error) {
      if (!mounted.current) return false;
      if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        clearTokens();
      }
      setUser(null);
      setWorkspace(null);
      setWorkspaces([]);
      setStatus("unauthenticated");
      return false;
    }
  }, []);

  useEffect(() => {
    void loadSession();
  }, [loadSession]);

  const login = useCallback(async (credentials: LoginRequest) => {
    const data = await apiFetch<AuthResponse>("/auth/login", {
      method: "POST",
      json: credentials,
      auth: false,
      skipRefresh: true,
    });
    storeTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken });
    if (!mounted.current) return;
    setUser(data.user);
    setWorkspace(data.activeWorkspace);
    setWorkspaces(data.workspaces ?? []);
    setStatus("authenticated");
  }, []);

  const register = useCallback(async (payload: RegisterRequest) => {
    const data = await apiFetch<{
      user: AuthSession["user"];
      activeWorkspace: Workspace;
      workspaces: Workspace[];
      accessToken: string;
      refreshToken: string;
      expiresIn: number;
    }>("/auth/register", {
      method: "POST",
      json: payload,
      auth: false,
      skipRefresh: true,
    });
    storeTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken });
    if (!mounted.current) return;
    setUser(data.user);
    setWorkspace(data.activeWorkspace);
    setWorkspaces(data.workspaces ?? [data.activeWorkspace]);
    setStatus("authenticated");
    await loadSession();
  }, [loadSession]);

  const googleAuth = useCallback(async (payload: GoogleAuthRequest) => {
    const data = await apiFetch<AuthResponse>("/auth/google", {
      method: "POST",
      json: payload,
      auth: false,
      skipRefresh: true,
    });
    storeTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken });
    if (!mounted.current) return;
    setUser(data.user);
    setWorkspace(data.activeWorkspace);
    setWorkspaces(data.workspaces ?? [data.activeWorkspace]);
    setStatus("authenticated");
  }, []);

  const logout = useCallback(async () => {
    const refreshToken =
      typeof window !== "undefined" ? window.localStorage.getItem("monotron.refresh") : null;
    try {
      await apiFetch<void>("/auth/logout", {
        method: "POST",
        json: { refreshToken },
        skipRefresh: true,
      });
    } catch {
      /* logging out locally is enough */
    }
    clearTokens();
    if (!mounted.current) return;
    setUser(null);
    setWorkspace(null);
    setWorkspaces([]);
    setStatus("unauthenticated");
  }, []);

  const switchWorkspace = useCallback(async (workspaceId: string) => {
    const data = await apiFetch<AuthResponse>("/auth/switch-workspace", {
      method: "POST",
      json: { workspaceId },
    });
    if (data.accessToken && data.refreshToken) {
      storeTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken });
    }
    if (!mounted.current) return;
    setUser(data.user ?? user);
    setWorkspace(data.activeWorkspace);
    setWorkspaces(data.workspaces ?? []);
  }, [user]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      workspace,
      workspaces,
      status,
      login,
      register,
      googleAuth,
      logout,
      switchWorkspace,
      refresh: async () => {
        await loadSession();
      },
    }),
    [user, workspace, workspaces, status, login, register, googleAuth, logout, switchWorkspace, loadSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used inside <AuthProvider>");
  }
  return ctx;
}
