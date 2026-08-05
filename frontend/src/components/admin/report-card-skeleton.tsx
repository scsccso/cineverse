import { Skeleton } from "@/components/ui/skeleton";

/**
 * Shown while a filter change is in flight — CLAUDE.md Phase 8 explicitly
 * asks for a skeleton transition on filter switches rather than a full-page
 * reload jump, so unlike the dataviz skill's general "hold the previous
 * render, no skeleton" guidance, this project deliberately blanks the
 * previous (now differently-scoped) numbers instead of leaving them on
 * screen mislabeled under the new filter for a moment.
 */
export function ReportCardSkeleton() {
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-20 rounded-xl" />
        ))}
      </div>
      <Skeleton className="h-72 w-full rounded-xl" />
    </div>
  );
}
