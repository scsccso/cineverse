import { GlassSkeleton } from "@/components/glass/glass-skeleton";

export default function HomeLoading() {
  return (
    <div>
      <GlassSkeleton className="h-[70vh] min-h-[420px] w-full rounded-none border-x-0 border-t-0" />
      <div className="mx-auto max-w-6xl space-y-16 px-6 py-16">
        {[0, 1].map((section) => (
          <div key={section}>
            <GlassSkeleton className="h-7 w-40" />
            <div className="mt-6 grid grid-cols-2 gap-5 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
              {Array.from({ length: 10 }).map((_, i) => (
                <GlassSkeleton key={i} className="aspect-[2/3] w-full" />
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
