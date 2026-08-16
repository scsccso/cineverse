"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Clapperboard, ArrowLeft, LayoutDashboard, Film, Building2, CalendarClock, Receipt, TicketCheck, Users } from "lucide-react";
import { LogoutButton } from "@/components/auth/logout-button";
import type { UserResponse } from "@/lib/api/types";

/**
 * Admin's own top-level chrome — deliberately not the customer Navbar with a
 * route branch inside it. Two different design languages (dark Liquid Glass
 * vs. admin's flat light surface, see CLAUDE.md 1.5.1) sharing one component
 * means every future Navbar change has to be re-checked against an admin
 * rendering path it was never designed for. A flat header (no dropdown, no
 * hover-glow) matches the "no sidebar, one page, don't over-build" scope of
 * this Phase.
 */
export function AdminHeader({ user }: { user: UserResponse | null }) {
  const pathname = usePathname();

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

  return (
    <header className="border-b border-border bg-card">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-6">
        <div className="flex items-center gap-8">
          <Link
            href="/admin/dashboard"
            className="flex items-center gap-2 text-lg font-semibold tracking-tight text-foreground"
          >
            <Clapperboard className="size-5 text-primary" aria-hidden />
            Cine<span className="text-primary">Verse</span>
            <span className="hidden text-sm font-normal text-muted-foreground sm:inline">Admin</span>
          </Link>

          <nav className="hidden items-center gap-1 md:flex" aria-label="Admin main navigation">
            {navItems.map((item) => {
              const isActive = pathname.startsWith(item.href);
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={isActive ? "page" : undefined}
                  className={`flex h-9 items-center gap-2 rounded-md px-3 text-sm font-medium transition-colors ${
                    isActive
                      ? "bg-secondary text-secondary-foreground"
                      : "text-muted-foreground hover:bg-secondary/50 hover:text-foreground"
                  }`}
                >
                  <Icon className="size-4" aria-hidden />
                  {item.label}
                </Link>
              );
            })}
          </nav>
        </div>

        <div className="flex items-center gap-3">
          <Link
            href="/"
            aria-label="Back to site"
            className="flex h-9 items-center gap-1.5 rounded-md px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-secondary/50 hover:text-foreground"
          >
            <ArrowLeft className="size-4" aria-hidden />
            <span className="hidden sm:inline">Back to site</span>
          </Link>
          {user && (
            <span className="hidden text-sm text-muted-foreground sm:inline">{user.fullName}</span>
          )}
          <LogoutButton />
        </div>
      </div>
    </header>
  );
}
