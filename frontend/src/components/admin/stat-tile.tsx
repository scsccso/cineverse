import Link from "next/link";
import { AlertTriangle, ArrowRight } from "lucide-react";
import { cn } from "@/lib/utils";

interface StatTileProps {
  label: string;
  value: string;
  hint?: string;
  /** "warning" pairs an icon with the color (never color alone) — used for figures that need a second look, e.g. pending reconciliation. */
  tone?: "default" | "warning";
  /** Optional drill-down target — e.g. the sales report's Pending
   * Reconciliation tile linking to /admin/payments, which shows the
   * specific rows behind this tile's single summed figure. Every existing
   * caller omits this and renders exactly as before (a plain, non-
   * interactive div — most StatTiles have no page-sized detail view behind
   * them and shouldn't look clickable). */
  href?: string;
}

export function StatTile({ label, value, hint, tone = "default", href }: StatTileProps) {
  const className = cn(
    "rounded-xl border border-border bg-card p-4 shadow-sm",
    tone === "warning" && "border-[color:var(--chart-amber-border)] bg-[color:var(--chart-amber-surface)]",
    href && "transition-colors hover:bg-secondary/50",
  );

  const content = (
    <>
      <div className="flex items-center gap-1.5">
        {tone === "warning" && (
          <AlertTriangle className="size-3.5 text-[color:var(--chart-amber)]" aria-hidden />
        )}
        <p className="text-sm text-muted-foreground">{label}</p>
      </div>
      <p className="mt-1 font-mono text-2xl font-semibold text-foreground">{value}</p>
      {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
      {href && (
        <p className="mt-2 flex items-center gap-1 text-xs font-medium text-foreground">
          View details
          <ArrowRight className="size-3" aria-hidden />
        </p>
      )}
    </>
  );

  if (href) {
    return (
      <Link href={href} className={className}>
        {content}
      </Link>
    );
  }

  return <div className={className}>{content}</div>;
}
