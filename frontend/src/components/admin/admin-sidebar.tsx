"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  ArrowLeft,
  Building2,
  CalendarClock,
  Clapperboard,
  Film,
  LayoutDashboard,
  Menu,
  PanelLeftClose,
  PanelLeftOpen,
  Receipt,
  TicketCheck,
  Users,
  X,
} from "lucide-react";
import { LogoutButton } from "@/components/auth/logout-button";
import { cn } from "@/lib/utils";
import type { UserResponse } from "@/lib/api/types";

const COLLAPSED_STORAGE_KEY = "cineverse-admin-sidebar-collapsed";

/**
 * A minimal external store, not a useState+useEffect pair — localStorage is
 * genuinely external state (unavailable during SSR, where this must render
 * something), which is exactly what useSyncExternalStore exists for. Reading
 * it via `useEffect` + `setState` instead (the first version of this file
 * did) trips `react-hooks/set-state-in-effect`, the same rule this project
 * has already hit and worked around elsewhere (dashboard/page.tsx, the
 * customer Navbar's route-change-closes-menu logic) — this isn't a
 * workaround for that lint rule, it's the tool the rule is steering toward.
 * Module-scope, not per-component-instance: AdminSidebar only ever mounts
 * once, but keeping the listener list here (rather than recreated per
 * render) is also just the correct shape for an external store.
 */
const collapsedStore = (() => {
  let listeners: Array<() => void> = [];

  function getSnapshot(): boolean {
    try {
      return localStorage.getItem(COLLAPSED_STORAGE_KEY) === "true";
    } catch {
      return false;
    }
  }

  // SSR always renders "expanded" — the accepted trade-off is one possible
  // frame of flash on reload if the stored preference is "collapsed", not a
  // no-flash cookie/proxy.ts-backed version, disproportionate for a
  // preference this low-stakes.
  function getServerSnapshot(): boolean {
    return false;
  }

  function subscribe(listener: () => void): () => void {
    listeners.push(listener);
    return () => {
      listeners = listeners.filter((l) => l !== listener);
    };
  }

  function setCollapsed(next: boolean): void {
    try {
      localStorage.setItem(COLLAPSED_STORAGE_KEY, String(next));
    } catch {
      // Nothing to persist to (private browsing etc.) — still notify so
      // this session's UI reflects the toggle even without storage.
    }
    listeners.forEach((listener) => listener());
  }

  return { getSnapshot, getServerSnapshot, subscribe, setCollapsed };
})();

// "Movies" was a dead link for a while (see CLAUDE.md — pointed at a page
// that had never been built) and was removed rather than built out on the
// spot. It's back now that /admin/movies is a real page, not a re-add of
// the original dead link.
const navItems = [
  { href: "/admin/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/admin/movies", label: "Movies", icon: Film },
  { href: "/admin/cinemas", label: "Cinemas", icon: Building2 },
  { href: "/admin/showtimes", label: "Showtimes", icon: CalendarClock },
  { href: "/admin/bookings", label: "Bookings", icon: Receipt },
  { href: "/admin/tickets/redeem", label: "Tickets", icon: TicketCheck },
  { href: "/admin/users", label: "Users", icon: Users },
];

/**
 * Admin's own top-level chrome — deliberately not the customer Navbar with a
 * route branch inside it (see CLAUDE.md 1.5.1/1.5.2). A left sidebar, not a
 * top bar: with 7 entries a horizontal nav was already crowded, and a
 * sidebar is the conventional shape for an admin backend rather than
 * something that reads as "the same site, admin flavor" the way a top bar
 * did. Two independent pieces of open/closed state, not one: `collapsed`
 * (icon-only rail vs full width, desktop only, persisted) and `mobileOpen`
 * (off-canvas drawer, mobile only, never persisted) — collapsing to a
 * narrower rail isn't a meaningful concept for a drawer that's either
 * fully open or fully closed.
 */
export function AdminSidebar({ user }: { user: UserResponse | null }) {
  const pathname = usePathname();
  const collapsed = useSyncExternalStore(
    collapsedStore.subscribe,
    collapsedStore.getSnapshot,
    collapsedStore.getServerSnapshot,
  );
  const [mobileOpen, setMobileOpen] = useState(false);

  function toggleCollapsed() {
    collapsedStore.setCollapsed(!collapsed);
  }

  // Route changes close the mobile drawer — compared during render, not in
  // a useEffect (react-hooks/set-state-in-effect flags a synchronous
  // setState inside an effect body as a cascading-render anti-pattern; this
  // is the same technique the customer Navbar's mobile panel already uses).
  const [prevPathname, setPrevPathname] = useState(pathname);
  if (pathname !== prevPathname) {
    setPrevPathname(pathname);
    setMobileOpen(false);
  }

  // Escape closes the drawer — same as the customer Navbar's mobile panel.
  useEffect(() => {
    if (!mobileOpen) return;
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setMobileOpen(false);
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [mobileOpen]);

  return (
    <>
      {/* Desktop sidebar */}
      <aside
        className={cn(
          "hidden shrink-0 flex-col border-r border-border bg-card transition-[width] motion-reduce:transition-none md:flex",
          collapsed ? "w-16" : "w-64",
        )}
      >
        <div className="flex h-16 shrink-0 items-center justify-between gap-2 border-b border-border px-3">
          <Link
            href="/admin/dashboard"
            aria-label="CineVerse Admin"
            className="flex min-w-0 items-center gap-2 text-lg font-semibold tracking-tight text-foreground"
          >
            <Clapperboard className="size-5 shrink-0 text-primary" aria-hidden />
            {!collapsed && (
              <span className="truncate">
                Cine<span className="text-primary">Verse</span>
              </span>
            )}
          </Link>
          {!collapsed && (
            <button
              type="button"
              onClick={toggleCollapsed}
              aria-expanded={!collapsed}
              aria-controls="admin-sidebar-nav"
              aria-label="Collapse sidebar"
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-secondary/50 hover:text-foreground"
            >
              <PanelLeftClose className="size-4" aria-hidden />
            </button>
          )}
        </div>

        {collapsed && (
          <div className="flex justify-center border-b border-border px-3 py-2">
            <button
              type="button"
              onClick={toggleCollapsed}
              aria-expanded={!collapsed}
              aria-controls="admin-sidebar-nav"
              aria-label="Expand sidebar"
              className="flex h-11 w-11 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-secondary/50 hover:text-foreground"
            >
              <PanelLeftOpen className="size-4" aria-hidden />
            </button>
          </div>
        )}

        <SidebarNav id="admin-sidebar-nav" pathname={pathname} collapsed={collapsed} />

        <div className="flex shrink-0 flex-col gap-1 border-t border-border p-3">
          {user && !collapsed && (
            <p className="truncate px-3 py-1 text-sm text-muted-foreground">{user.fullName}</p>
          )}
          <SidebarSecondaryLink href="/" icon={ArrowLeft} label="Back to site" collapsed={collapsed} />
          {collapsed ? (
            <div className="flex justify-center">
              <LogoutButton iconOnly />
            </div>
          ) : (
            <LogoutButton className="w-full" />
          )}
        </div>
      </aside>

      {/* Mobile top bar */}
      <header className="flex h-16 shrink-0 items-center justify-between border-b border-border bg-card px-4 md:hidden">
        <Link
          href="/admin/dashboard"
          className="flex items-center gap-2 text-lg font-semibold tracking-tight text-foreground"
        >
          <Clapperboard className="size-5 text-primary" aria-hidden />
          Cine<span className="text-primary">Verse</span>
          <span className="text-sm font-normal text-muted-foreground">Admin</span>
        </Link>
        <button
          type="button"
          className="flex h-11 w-11 items-center justify-center text-foreground"
          aria-expanded={mobileOpen}
          aria-controls="admin-mobile-nav-panel"
          aria-label={mobileOpen ? "Close menu" : "Open menu"}
          onClick={() => setMobileOpen((open) => !open)}
        >
          {mobileOpen ? <X className="size-5" aria-hidden /> : <Menu className="size-5" aria-hidden />}
        </button>
      </header>

      {/* Mobile drawer — always mounted (not conditionally rendered) so the
          slide/fade can actually transition; inert removes it from the
          a11y tree and from tab order/pointer interaction while closed,
          rather than stacking manual aria-hidden + pointer-events + tabIndex
          management for the same effect. */}
      <div
        className={cn("fixed inset-0 z-50 md:hidden", !mobileOpen && "pointer-events-none")}
        inert={!mobileOpen}
      >
        <div
          className={cn(
            "fixed inset-0 bg-background/80 backdrop-blur-sm transition-opacity motion-reduce:transition-none",
            mobileOpen ? "opacity-100" : "opacity-0",
          )}
          onClick={() => setMobileOpen(false)}
          aria-hidden
        />
        <div
          id="admin-mobile-nav-panel"
          className={cn(
            "fixed inset-y-0 left-0 flex w-64 flex-col border-r border-border bg-card transition-transform motion-reduce:transition-none",
            mobileOpen ? "translate-x-0" : "-translate-x-full",
          )}
        >
          <div className="flex h-16 shrink-0 items-center justify-between border-b border-border px-4">
            <span className="text-lg font-semibold tracking-tight text-foreground">
              Cine<span className="text-primary">Verse</span>
            </span>
            <button
              type="button"
              onClick={() => setMobileOpen(false)}
              aria-label="Close menu"
              className="flex h-11 w-11 items-center justify-center text-foreground"
            >
              <X className="size-5" aria-hidden />
            </button>
          </div>

          <SidebarNav pathname={pathname} collapsed={false} />

          <div className="flex shrink-0 flex-col gap-1 border-t border-border p-3">
            {user && <p className="truncate px-3 py-1 text-sm text-muted-foreground">{user.fullName}</p>}
            <SidebarSecondaryLink href="/" icon={ArrowLeft} label="Back to site" collapsed={false} />
            <LogoutButton className="w-full" />
          </div>
        </div>
      </div>
    </>
  );
}

function SidebarNav({
  id,
  pathname,
  collapsed,
}: {
  id?: string;
  pathname: string;
  collapsed: boolean;
}) {
  return (
    <nav id={id} aria-label="Admin main navigation" className="flex flex-1 flex-col gap-1 overflow-y-auto p-3">
      {navItems.map((item) => {
        const isActive = pathname.startsWith(item.href);
        const Icon = item.icon;
        return (
          <Link
            key={item.href}
            href={item.href}
            aria-current={isActive ? "page" : undefined}
            aria-label={collapsed ? item.label : undefined}
            title={collapsed ? item.label : undefined}
            className={cn(
              "flex h-11 items-center gap-3 rounded-md px-3 text-sm font-medium transition-colors",
              isActive
                ? "bg-secondary text-secondary-foreground"
                : "text-muted-foreground hover:bg-secondary/50 hover:text-foreground",
              collapsed && "justify-center px-0",
            )}
          >
            <Icon className="size-4 shrink-0" aria-hidden />
            {!collapsed && <span className="truncate">{item.label}</span>}
          </Link>
        );
      })}
    </nav>
  );
}

function SidebarSecondaryLink({
  href,
  icon: Icon,
  label,
  collapsed,
}: {
  href: string;
  icon: typeof ArrowLeft;
  label: string;
  collapsed: boolean;
}) {
  return (
    <Link
      href={href}
      aria-label={collapsed ? label : undefined}
      title={collapsed ? label : undefined}
      className={cn(
        "flex h-11 items-center gap-2 rounded-md px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-secondary/50 hover:text-foreground",
        collapsed && "justify-center px-0",
      )}
    >
      <Icon className="size-4 shrink-0" aria-hidden />
      {!collapsed && <span className="truncate">{label}</span>}
    </Link>
  );
}
