import { cn } from "@/lib/utils";

/** Glass-textured loading placeholder — used in loading.tsx route fallbacks instead of a spinner. */
export function GlassSkeleton({ className }: { className?: string }) {
  return (
    <div
      aria-hidden
      className={cn(
        "animate-pulse rounded-2xl border border-glass-border bg-glass-surface backdrop-blur-glass motion-reduce:animate-none",
        className,
      )}
    />
  );
}
