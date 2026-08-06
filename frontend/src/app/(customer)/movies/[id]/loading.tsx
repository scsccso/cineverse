import { GlassSkeleton } from "@/components/glass/glass-skeleton";

export default function MovieDetailLoading() {
  return (
    <div>
      <GlassSkeleton className="h-[50vh] min-h-[320px] w-full rounded-none border-x-0 border-t-0" />
      <div className="mx-auto -mt-24 max-w-5xl px-6 pb-20">
        <div className="flex flex-col gap-6 rounded-3xl border border-glass-border bg-glass-surface p-6 backdrop-blur-glass sm:flex-row sm:p-8">
          <GlassSkeleton className="aspect-[2/3] w-40 shrink-0 sm:w-48" />
          <div className="flex-1 space-y-4">
            <GlassSkeleton className="h-9 w-2/3" />
            <GlassSkeleton className="h-4 w-1/2" />
            <GlassSkeleton className="h-20 w-full" />
          </div>
        </div>
        <div className="mt-12 space-y-6">
          <GlassSkeleton className="h-7 w-32" />
          <div className="flex flex-wrap gap-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <GlassSkeleton key={i} className="h-20 w-28" />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
