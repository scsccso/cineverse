"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-context";
import { ApiError } from "@/lib/api/client";
import { Skeleton } from "@/components/ui/skeleton";
import { AdminSidebar } from "@/components/admin/admin-sidebar";
import { TmdbAttribution } from "@/components/layout/tmdb-attribution";

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
 * app/, not nested. AdminSidebar below is a separate component, not the
 * customer Navbar branching on pathname (see CLAUDE.md 1.5.1).
 *
 * `md:flex` here plus AdminSidebar's own internal `hidden md:flex` (desktop
 * aside) / `md:hidden` (mobile top bar + drawer) splits are what make this a
 * side-by-side shell at md+ and a stacked one below it — no page under
 * app/admin/** needs to know or care, since each one's own content wrapper
 * (`mx-auto max-w-*`) just centers within whatever width this shell's
 * content column has. `min-w-0` on that column is load-bearing: without it,
 * a flex child sizes to its content's intrinsic width by default, which for
 * a wide table would push the sidebar off-screen instead of the table
 * itself scrolling inside its own `overflow-x-auto` wrapper.
 *
 * The content column is deliberately a plain block div, not `flex flex-col`
 * (it was the latter until the sidebar-nav visual verification pass caught
 * a real bug): making it a flex container of its own turns `{children}`
 * into a flex item one level down, and *that* item has no `min-w-0` of its
 * own — its automatic minimum size falls back to its content's min-content
 * width, which for a page whose content has a wide natural floor (e.g.
 * admin/bookings' 3-field search grid — three native inputs/selects that
 * won't shrink below ~190px each) is wider than the space actually
 * available once the sidebar's width is subtracted. That inflated flex
 * item then pushes the whole content column past the viewport instead of
 * shrinking to it, even though `min-w-0` on *this* div is correctly in
 * place — `min-w-0` only fixes this div's own sizing as a flex item of the
 * outer sidebar row, not its children's sizing as flex items of a second,
 * inner flex context this div would otherwise create. A plain block div
 * has no such pitfall: `{children}`'s top-level element resolves its width
 * via ordinary block layout (fill the containing block, no content-based
 * floor), and still stacks above `<TmdbAttribution>` with zero flex needed
 * for that — block boxes do that on their own.
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
      <div className="admin-light min-h-screen bg-background text-foreground md:flex">
        <AdminSidebar user={null} />
        <div className="min-w-0 flex-1">
          <div className="mx-auto w-full max-w-6xl space-y-4 px-6 py-10">
            <Skeleton className="h-8 w-64" />
            <Skeleton className="h-64 w-full rounded-xl" />
          </div>
          <TmdbAttribution />
        </div>
      </div>
    );
  }

  return (
    <div className="admin-light min-h-screen bg-background text-foreground md:flex">
      <AdminSidebar user={user} />
      <div className="min-w-0 flex-1">
        {children}
        <TmdbAttribution />
      </div>
    </div>
  );
}
