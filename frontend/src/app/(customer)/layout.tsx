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
