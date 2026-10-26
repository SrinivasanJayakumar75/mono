"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState, type ReactNode, type RefObject } from "react";
import clsx from "clsx";

import {
  BookIcon,
  BuildingIcon,
  ChevronDownIcon,
  CloseIcon,
  CreditCardIcon,
  InboxIcon,
  LeadsIcon,
  LogoutIcon,
  MenuIcon,
  SettingsIcon,
  UsersIcon,
} from "./icons";
import { MonotronLogo } from "./logo";
import { Spinner, useToast } from "./ui";
import { useAuth } from "@/lib/auth";
import { useBilling } from "@/lib/billing";
import { initials } from "@/lib/format";

interface NavItem {
  href: string;
  label: string;
  icon: ReactNode;
  adminOnly?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { href: "/inbox", label: "Inbox", icon: <InboxIcon className="h-4 w-4" /> },
  { href: "/crm", label: "CRM", icon: <LeadsIcon className="h-4 w-4" /> },
  { href: "/knowledge", label: "Knowledge", icon: <BookIcon className="h-4 w-4" /> },
  { href: "/contacts", label: "Contacts", icon: <UsersIcon className="h-4 w-4" /> },
  { href: "/settings", label: "Settings", icon: <SettingsIcon className="h-4 w-4" /> },
  { href: "/billing", label: "Billing", icon: <CreditCardIcon className="h-4 w-4" /> },
];

const TITLES: Record<string, string> = {
  "/inbox": "Inbox",
  "/crm": "CRM",
  "/knowledge": "Knowledge base",
  "/contacts": "Contacts",
  "/settings": "Settings",
  "/billing": "Billing",
};

function useCloseOnOutside(
  ref: RefObject<HTMLElement | null>,
  onClose: () => void,
  active: boolean,
) {
  useEffect(() => {
    if (!active) return;
    function onPointerDown(event: MouseEvent | TouchEvent) {
      const node = ref.current;
      if (!node) return;
      if (!node.contains(event.target as Node)) onClose();
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
    }
    document.addEventListener("mousedown", onPointerDown);
    document.addEventListener("touchstart", onPointerDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onPointerDown);
      document.removeEventListener("touchstart", onPointerDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [ref, onClose, active]);
}

function SidebarContent({
  onNavigate,
  pathname,
}: {
  onNavigate?: () => void;
  pathname: string;
}) {
  const { workspace } = useAuth();
  const { billing } = useBilling();

  return (
    <div className="flex h-full flex-col">
      <div className="flex h-14 shrink-0 items-center border-b border-surface-200 px-4">
        <MonotronLogo
          href="/inbox"
          markClassName="h-7 w-7 rounded-md"
          wordmarkClassName="text-[14px]"
        />
      </div>

      <nav className="flex-1 space-y-0.5 overflow-y-auto px-2.5 py-4" aria-label="Primary">
        <p className="px-2.5 pb-2 text-[10px] font-medium uppercase tracking-[0.14em] text-surface-400">
          Workspace
        </p>
        {NAV_ITEMS.map((item) => {
          const active =
            pathname === item.href || pathname.startsWith(`${item.href}/`);
          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={onNavigate}
              aria-current={active ? "page" : undefined}
              className={clsx(
                "group flex items-center gap-2.5 rounded-md px-2.5 py-1.5 text-[13px] font-medium transition-colors",
                active
                  ? "bg-white text-surface-900 shadow-[0_1px_2px_rgb(26_24_21_/_0.06)] ring-1 ring-surface-200"
                  : "text-surface-600 hover:bg-surface-200/60 hover:text-surface-900",
              )}
            >
              <span
                className={clsx(
                  "flex h-4 w-4 items-center justify-center transition-colors",
                  active ? "text-surface-900" : "text-surface-400 group-hover:text-surface-600",
                )}
              >
                {item.icon}
              </span>
              {item.label}
            </Link>
          );
        })}
      </nav>

      <div className="shrink-0 border-t border-surface-200 p-2.5">
        <div className="rounded-md border border-surface-200 bg-white px-2.5 py-2">
          <p className="flex items-center gap-1.5 text-[10px] font-medium uppercase tracking-[0.12em] text-surface-400">
            <BuildingIcon className="h-3 w-3" />
            Workspace
          </p>
          <p className="mt-1 truncate text-[13px] font-medium text-surface-800">
            {workspace?.name ?? "—"}
          </p>
          {billing?.status === "TRIALING" ? (
            <Link
              href="/billing"
              onClick={onNavigate}
              className="mt-2 flex items-center justify-between gap-2 rounded bg-amber-50 px-2 py-1 text-[10.5px] font-medium text-amber-700 ring-1 ring-inset ring-amber-200 transition-colors hover:bg-amber-100"
            >
              <span>Trial</span>
              <span>
                {billing.daysLeft === null
                  ? "Ending soon"
                  : `${billing.daysLeft} ${billing.daysLeft === 1 ? "day" : "days"} left`}
              </span>
            </Link>
          ) : null}
        </div>
      </div>
    </div>
  );
}

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { user, workspace, workspaces, status, logout, switchWorkspace } = useAuth();
  const { toast } = useToast();

  const [mobileOpen, setMobileOpen] = useState(false);
  const [workspaceOpen, setWorkspaceOpen] = useState(false);
  const [userOpen, setUserOpen] = useState(false);
  const [switching, setSwitching] = useState(false);

  const workspaceRef = useRef<HTMLDivElement>(null);
  const userRef = useRef<HTMLDivElement>(null);

  useCloseOnOutside(workspaceRef, () => setWorkspaceOpen(false), workspaceOpen);
  useCloseOnOutside(userRef, () => setUserOpen(false), userOpen);

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  const title =
    Object.entries(TITLES).find(([path]) => pathname.startsWith(path))?.[1] ?? "Monotron";
  const isFullBleed =
    pathname.startsWith("/inbox") ||
    pathname.startsWith("/crm") ||
    pathname.startsWith("/contacts") ||
    pathname.startsWith("/knowledge");

  async function onSwitchWorkspace(workspaceId: string) {
    if (workspaceId === workspace?.id) {
      setWorkspaceOpen(false);
      return;
    }
    setSwitching(true);
    try {
      await switchWorkspace(workspaceId);
      // Full navigation guarantees every page re-fetches against the new tenant.
      window.location.assign("/inbox");
    } catch {
      toast("Could not switch workspace", { tone: "error" });
    } finally {
      setSwitching(false);
      setWorkspaceOpen(false);
    }
  }

  async function onLogout() {
    setUserOpen(false);
    await logout();
    window.location.assign("/login");
  }

  const sidebar = (
    <SidebarContent pathname={pathname} onNavigate={() => setMobileOpen(false)} />
  );

  return (
    <div className="min-h-screen bg-surface-50">
      {/* desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 border-r border-surface-200 bg-surface-100/70 lg:block">
        {sidebar}
      </aside>

      {/* mobile drawer */}
      {mobileOpen ? (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div
            className="absolute inset-0 bg-surface-950/30 animate-fade-in"
            onClick={() => setMobileOpen(false)}
            aria-hidden="true"
          />
          <div className="absolute inset-y-0 left-0 w-64 border-r border-surface-200 bg-surface-100 shadow-lift animate-fade-in">
            <button
              type="button"
              onClick={() => setMobileOpen(false)}
              aria-label="Close navigation"
              className="absolute right-3 top-3.5 rounded-md p-2 text-surface-500 hover:bg-surface-200 hover:text-surface-900"
            >
              <CloseIcon className="h-4 w-4" />
            </button>
            {sidebar}
          </div>
        </div>
      ) : null}

      <div className="lg:pl-60">
        {/* topbar */}
        <header className="sticky top-0 z-20 border-b border-surface-200 bg-surface-50/85 backdrop-blur-md">
          <div className="flex h-14 items-center gap-3 px-4 sm:px-6">
            <button
              type="button"
              onClick={() => setMobileOpen(true)}
              aria-label="Open navigation"
              className="rounded-md p-2 text-surface-600 hover:bg-surface-200/70 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 lg:hidden"
            >
              <MenuIcon className="h-5 w-5" />
            </button>

            <h1 className="truncate text-[13.5px] font-medium tracking-[-0.005em] text-surface-800">
              {title}
            </h1>

            <div className="ml-auto flex items-center gap-2">
              {/* workspace switcher */}
              <div className="relative" ref={workspaceRef}>
                <button
                  type="button"
                  onClick={() => setWorkspaceOpen((open) => !open)}
                  aria-haspopup="listbox"
                  aria-expanded={workspaceOpen}
                  disabled={status !== "authenticated"}
                  className="flex max-w-[10rem] items-center gap-2 rounded-md border border-surface-200 bg-white px-2.5 py-1.5 text-[12.5px] font-medium text-surface-700 transition-colors hover:border-surface-300 hover:bg-surface-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 disabled:opacity-60 sm:max-w-none"
                >
                  <BuildingIcon className="h-4 w-4 shrink-0 text-surface-400" />
                  <span className="truncate">{workspace?.name ?? "Workspace"}</span>
                  <ChevronDownIcon className="h-3.5 w-3.5 shrink-0 text-surface-400" />
                </button>

                {workspaceOpen ? (
                  <div
                    role="listbox"
                    aria-label="Workspaces"
                    className="absolute right-0 z-30 mt-2 w-64 overflow-hidden rounded-lg border border-surface-200 bg-white shadow-lift animate-fade-up"
                  >
                    <p className="border-b border-surface-100 px-3 py-2 text-[10px] font-medium uppercase tracking-[0.12em] text-surface-400">
                      Switch workspace
                    </p>
                    <ul className="max-h-64 overflow-y-auto py-1">
                      {workspaces.map((item) => (
                        <li key={item.id}>
                          <button
                            type="button"
                            role="option"
                            aria-selected={item.id === workspace?.id}
                            onClick={() => void onSwitchWorkspace(item.id)}
                            className={clsx(
                              "flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-[13px] transition-colors hover:bg-surface-50",
                              item.id === workspace?.id
                                ? "bg-surface-100 font-medium text-surface-900"
                                : "text-surface-700",
                            )}
                          >
                            <span className="truncate">{item.name}</span>
                            <span className="shrink-0 text-[9.5px] uppercase tracking-[0.1em] text-surface-400">
                              {item.role}
                            </span>
                          </button>
                        </li>
                      ))}
                      {workspaces.length === 0 ? (
                        <li className="px-3 py-3 text-sm text-surface-500">
                          No workspaces available.
                        </li>
                      ) : null}
                    </ul>
                    {switching ? (
                      <div className="flex items-center gap-2 border-t border-surface-100 px-3 py-2 text-xs text-surface-500">
                        <Spinner className="h-3.5 w-3.5" /> Switching workspace…
                      </div>
                    ) : null}
                  </div>
                ) : null}
              </div>

              {/* user menu */}
              <div className="relative" ref={userRef}>
                <button
                  type="button"
                  onClick={() => setUserOpen((open) => !open)}
                  aria-haspopup="menu"
                  aria-expanded={userOpen}
                  className="flex items-center gap-2 rounded-md px-1.5 py-1.5 text-[12.5px] font-medium text-surface-700 transition-colors hover:bg-surface-200/60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
                >
                  <span className="flex h-7 w-7 items-center justify-center rounded-md bg-brand-900 text-[10px] font-semibold text-surface-50">
                    {initials(user?.name)}
                  </span>
                  <span className="hidden max-w-[9rem] truncate text-surface-700 md:inline">
                    {user?.name ?? "Account"}
                  </span>
                  <ChevronDownIcon className="hidden h-3.5 w-3.5 text-surface-400 md:inline" />
                </button>

                {userOpen ? (
                  <div
                    role="menu"
                    className="absolute right-0 z-30 mt-2 w-60 overflow-hidden rounded-lg border border-surface-200 bg-white shadow-lift animate-fade-up"
                  >
                    <div className="border-b border-surface-100 px-3 py-3">
                      <p className="truncate text-[13px] font-semibold text-surface-900">
                        {user?.name ?? "Account"}
                      </p>
                      <p className="truncate text-xs text-surface-500">{user?.email ?? "—"}</p>
                      <p className="mt-1.5 inline-flex rounded bg-surface-100 px-1.5 py-0.5 text-[9.5px] font-medium uppercase tracking-[0.1em] text-surface-500">
                        {user?.role ?? "—"}
                      </p>
                    </div>
                    <button
                      type="button"
                      role="menuitem"
                      onClick={() => void onLogout()}
                      className="flex w-full items-center gap-2 px-3 py-2.5 text-left text-sm text-surface-700 transition-colors hover:bg-surface-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
                    >
                      <LogoutIcon className="h-4 w-4 text-surface-400" />
                      Sign out
                    </button>
                  </div>
                ) : null}
              </div>
            </div>
          </div>
        </header>

        <main
          className={clsx(
            isFullBleed
              ? "flex h-[calc(100dvh-3.5rem)] min-h-0 flex-col overflow-hidden"
              : "px-4 py-6 sm:px-6 lg:px-8 lg:py-8",
          )}
        >
          {children}
        </main>
      </div>
    </div>
  );
}
