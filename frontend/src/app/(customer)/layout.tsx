import { Navbar } from "@/components/layout/navbar";
import { PageTransition } from "@/components/motion/page-transition";

/**
 * All customer-facing "chrome" (the dark Liquid Glass Navbar + route
 * transition) lives here, scoped to this route group only. /admin/** is a
 * sibling segment under app/ with its own layout (app/admin/layout.tsx) and
 * never nests inside this one, so none of this renders for admin routes —
 * not hidden via CSS, genuinely absent from that render tree. See CLAUDE.md
 * 1.5.1 for why admin needed its own shell instead of branching inside this
 * one.
 *
 * Deliberately NOT an ADMIN reverse-gate here (see CLAUDE.md "Admin
 * reverse-isolation" for the full reasoning): this layout wraps public routes
 * (/, /movies/[id], /login, /register, /showtimes/**) that render instantly
 * today with no auth check at all, and the backend already permits ADMIN to
 * use booking endpoints (`authenticated()`, not role-restricted — see
 * SecurityConfig). Gating the whole route group would add an auth-resolution
 * wait to every public page's hard-navigation/first-load, including the
 * homepage, to guard against a case the backend doesn't even treat as
 * off-limits. The actual problem — an ADMIN's personal data rendering on a
 * page meant for the logged-in customer — only exists on the routes that
 * show that data, so the redirect lives there instead: see the ADMIN check
 * in profile/page.tsx and bookings/page.tsx. bookings/[id]/confirmed/page.tsx
 * deliberately does NOT get this check — GET /bookings/{id} explicitly lets
 * ADMIN view any customer's booking (support use case; see BookingController
 * .getById), so redirecting ADMIN away from it would break that.
 */
export default function CustomerLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-full flex-col">
      <Navbar />
      <main className="flex-1">
        <PageTransition>{children}</PageTransition>
      </main>
    </div>
  );
}
