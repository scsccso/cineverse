import { AlertTriangle } from "lucide-react";
import { cn } from "@/lib/utils";

interface StatTileProps {
  label: string;
  value: string;
  hint?: string;
  /** "warning" pairs an icon with the color (never color alone) — used for figures that need a second look, e.g. pending reconciliation. */
  tone?: "default" | "warning";
}

export function StatTile({ label, value, hint, tone = "default" }: StatTileProps) {
  return (
    <div
      className={cn(
        "rounded-xl border border-border bg-card p-4 shadow-sm",
        tone === "warning" &&
          "border-[color:var(--chart-amber-border)] bg-[color:var(--chart-amber-surface)]",
      )}
    >
      <div className="flex items-center gap-1.5">
        {tone === "warning" && (
          <AlertTriangle className="size-3.5 text-[color:var(--chart-amber)]" aria-hidden />
        )}
        <p className="text-sm text-muted-foreground">{label}</p>
      </div>
      <p className="mt-1 font-mono text-2xl font-semibold text-foreground">{value}</p>
      {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}
