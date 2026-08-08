import Link from "next/link";
import { Clapperboard, ArrowLeft } from "lucide-react";
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
  return (
    <header className="border-b border-border bg-card">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-6">
        <div className="flex items-center gap-6">
          <Link
            href="/admin/dashboard"
            className="flex items-center gap-2 text-lg font-semibold tracking-tight text-foreground"
          >
            <Clapperboard className="size-5 text-primary" aria-hidden />
            Cine<span className="text-primary">Verse</span>
            <span className="hidden text-sm font-normal text-muted-foreground sm:inline">管理后台</span>
          </Link>
          <Link
            href="/"
            aria-label="返回前台"
            className="flex h-11 items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground"
          >
            <ArrowLeft className="size-4" aria-hidden />
            <span className="hidden sm:inline">返回前台</span>
          </Link>
        </div>

        <div className="flex items-center gap-3">
          {user && (
            <span className="hidden text-sm text-muted-foreground sm:inline">{user.fullName}</span>
          )}
          <LogoutButton />
        </div>
      </div>
    </header>
  );
}
