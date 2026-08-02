import { GlassSkeleton } from "@/components/glass/glass-skeleton";

export default function SeatSelectionLoading() {
  return (
    <div className="mx-auto max-w-5xl px-4 pb-32 pt-10 sm:px-6">
      <div className="mb-6 flex flex-col items-center gap-2">
        <GlassSkeleton className="h-4 w-40" />
        <GlassSkeleton className="h-3 w-56" />
      </div>
      <div className="flex flex-col items-center gap-2">
        {Array.from({ length: 6 }).map((_, i) => (
          <GlassSkeleton key={i} className="h-8 w-72 rounded-lg" />
        ))}
      </div>
    </div>
  );
}
