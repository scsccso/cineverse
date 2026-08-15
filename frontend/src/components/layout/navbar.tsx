"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Clapperboard, Menu, X } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { LogoutButton } from "@/components/auth/logout-button";
import { cn } from "@/lib/utils";

const movieSectionLinks = [
  { label: "Now Playing", href: "/#now-playing" },
  { label: "Coming Soon", href: "/#coming-soon" },
];

export function Navbar() {
  const { status, user } = useAuth();
  const pathname = usePathname();
  const [mobileOpen, setMobileOpen] = useState(false);

  // Route changes (tapping a link inside the panel) should close it — adjusted
  // during render rather than in a useEffect (react-hooks/set-state-in-effect
  // flags a setState synchronously inside an effect body as a
  // cascading-render anti-pattern; this is React's documented alternative for
  // "reset state when a prop changes": https://react.dev/learn/you-might-not-need-an-effect).
  const [prevPathname, setPrevPathname] = useState(pathname);
  if (pathname !== prevPathname) {
    setPrevPathname(pathname);
    setMobileOpen(false);
  }

  useEffect(() => {
    if (!mobileOpen) return;
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setMobileOpen(false);
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [mobileOpen]);

  return (
    <header className="sticky top-0 z-50 border-b border-border/60 bg-background/70 backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-6">
        <Link
          href="/"
          className="flex items-center gap-2 text-lg font-semibold tracking-tight"
        >
          <Clapperboard className="size-5 text-primary" aria-hidden />
          Cine<span className="text-primary">Verse</span>
        </Link>

        <nav className="hidden items-center gap-8 md:flex">
          {movieSectionLinks.map(({ label, href }) => (
            <Link
              key={label}
              href={href}
              className="text-sm text-muted-foreground transition-colors hover:text-foreground"
            >
              {label}
            </Link>
          ))}
          <span
            aria-disabled
            title="Coming soon"
            className="cursor-not-allowed text-sm text-muted-foreground/50"
          >
            Cinemas
          </span>
        </nav>

        {/* Below md, the links above are gone with no way to reach them —
            everything (movie section links + the auth block that otherwise
            renders here) moves into the disclosure panel below instead. */}
        <div className="hidden items-center gap-3 md:flex">
          <AuthSection status={status} user={user} />
        </div>

        <button
          type="button"
          className="flex h-11 w-11 items-center justify-center text-foreground md:hidden"
          aria-expanded={mobileOpen}
          aria-controls="mobile-nav-panel"
          aria-label={mobileOpen ? "Close menu" : "Open menu"}
          onClick={() => setMobileOpen((open) => !open)}
        >
          {mobileOpen ? <X className="size-5" /> : <Menu className="size-5" />}
        </button>
      </div>

      {mobileOpen && (
        <div
          id="mobile-nav-panel"
          className="border-t border-border/60 bg-background/95 px-6 py-4 backdrop-blur-xl md:hidden"
        >
          <nav className="flex flex-col">
            {movieSectionLinks.map(({ label, href }) => (
              <Link
                key={label}
                href={href}
                className="flex h-11 items-center text-sm text-muted-foreground transition-colors hover:text-foreground"
              >
                {label}
              </Link>
            ))}
            <span
              aria-disabled
              title="Coming soon"
              className="flex h-11 items-center text-sm text-muted-foreground/50"
            >
              Cinemas
            </span>
          </nav>

          <div className="mt-2 flex flex-col gap-3 border-t border-border/60 pt-4">
            <AuthSection status={status} user={user} stacked />
          </div>
        </div>
      )}
    </header>
  );
}

interface AuthSectionProps {
  status: ReturnType<typeof useAuth>["status"];
  user: ReturnType<typeof useAuth>["user"];
  /** Full-width stacked buttons for the mobile panel instead of the compact inline row used at md+. */
  stacked?: boolean;
}

function AuthSection({ status, user, stacked = false }: AuthSectionProps) {
  if (status === "loading") {
    return stacked ? (
      <>
        <Skeleton className="h-11 w-full rounded-lg" />
        <Skeleton className="h-11 w-full rounded-lg" />
      </>
    ) : (
      <>
        <Skeleton className="h-8 w-16 rounded-lg" />
        <Skeleton className="h-8 w-20 rounded-lg" />
      </>
    );
  }

  if (status === "unauthenticated") {
    return (
      <>
        <Link
          href="/login"
          className={cn(
            buttonVariants({ variant: "ghost", size: stacked ? "default" : "sm" }),
            stacked && "w-full",
          )}
        >
          Log in
        </Link>
        <Link
          href="/register"
          className={cn(
            buttonVariants({ variant: "default", size: stacked ? "default" : "sm" }),
            stacked && "w-full",
          )}
        >
          Sign up
        </Link>
      </>
    );
  }

  return (
    <>
      {user?.role === "ADMIN" && (
        <Link
          href="/admin/dashboard"
          className={cn(
            "flex text-sm text-muted-foreground hover:text-foreground",
            stacked ? "h-11 items-center" : "items-center",
          )}
        >
          Admin
        </Link>
      )}
      {/* Sits in the shared AuthSection, so it appears in the desktop row and
          the mobile disclosure panel alike — the e-ticket must not be
          reachable on one breakpoint only. */}
      <Link
        href="/bookings"
        className={cn(
          "flex text-sm text-muted-foreground hover:text-foreground",
          stacked ? "h-11 items-center" : "items-center",
        )}
      >
        My Bookings
      </Link>
      <Link
        href="/profile"
        className={cn(
          "flex text-sm text-muted-foreground hover:text-foreground",
          stacked ? "h-11 items-center" : "items-center",
        )}
      >
        {user?.fullName ?? "My Account"}
      </Link>
      <LogoutButton className={stacked ? "h-11 w-full" : undefined} />
    </>
  );
}
