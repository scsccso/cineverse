"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-context";
import { ApiError } from "@/lib/api/client";
import { Skeleton } from "@/components/ui/skeleton";
import { AdminHeader } from "@/components/admin/admin-header";

/**
 * The definitive ADMIN-role gate — proxy.ts only rules out "no session at
 * all" (see its comment); it has no role to check. This runs on every
 * /admin/* route (Next.js layouts wrap all nested pages) and either renders
 * children once fetchCurrentUser() confirms role === "ADMIN", or redirects
 * before anything protected renders — never briefly flashes admin content
 * to a non-admin. Mirrors app/profile/page.tsx's
 * "authStatus loading -> wait; unauthenticated -> redirect; else verify"
 * shape, generalized into a layout since every /admin page needs the same
 * check, not just one.
 *
 * This is also the only place the customer route group's chrome
 * (app/(customer)/layout.tsx: dark Navbar + PageTransition) and this one
 * could collide — they don't, because route groups are siblings under
 * app/, not nested. AdminHeader below is a separate component, not the
 * customer Navbar branching on pathname (see CLAUDE.md 1.5.1).
 */
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { status: authStatus, fetchCurrentUser, user } = useAuth();
  const router = useRouter();
  const [authorized, setAuthorized] = useState(false);

  useEffect(() => {
    if (authStatus === "loading") return;

    if (authStatus === "unauthenticated") {
      router.replace("/login?from=/admin/dashboard");
      return;
    }

    let ignore = false;

    fetchCurrentUser()
      .then((fetchedUser) => {
        if (ignore) return;
        if (fetchedUser.role !== "ADMIN") {
          // Not a hidden-link pseudo-guard — this runs even if someone
          // types the URL directly, and nothing protected has rendered yet.
          router.replace("/");
          return;
        }
        setAuthorized(true);
      })
      .catch((error) => {
        if (ignore) return;
        if (error instanceof ApiError && error.status === 401) {
          router.replace("/login?from=/admin/dashboard");
        }
        // Any other error (network, 5xx): stay on the verifying skeleton
        // rather than redirecting away from a possibly-valid admin session.
      });

    return () => {
      ignore = true;
    };
  }, [authStatus, fetchCurrentUser, router]);

  if (!authorized) {
    return (
      <div className="admin-light min-h-screen bg-background text-foreground">
        <AdminHeader user={null} />
        <div className="mx-auto max-w-6xl space-y-4 px-6 py-10">
          <Skeleton className="h-8 w-64" />
          <Skeleton className="h-64 w-full rounded-xl" />
        </div>
      </div>
    );
  }

  return (
    <div className="admin-light min-h-screen bg-background text-foreground">
      <AdminHeader user={user} />
      {children}
    </div>
  );
}
