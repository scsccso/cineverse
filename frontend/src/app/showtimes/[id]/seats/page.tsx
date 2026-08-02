import Link from "next/link";
import { GlassCard } from "@/components/glass/glass-card";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

// Placeholder only: real seat locking/selection is Phase 5. This page exists
// so the movie -> showtime -> seats path is clickable end to end today.
export default async function SeatSelectionPlaceholderPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  return (
    <div className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-lg flex-col items-center justify-center px-6 py-16 text-center">
      <GlassCard className="p-8">
        <h1 className="font-display text-2xl font-semibold tracking-tight">
          选座功能 Phase 5 开发中
        </h1>
        <p className="mt-3 text-muted-foreground">
          座位选择与锁定正在开发中,敬请期待。
        </p>
        <Link
          href={`/showtimes/${id}`}
          className={cn(buttonVariants({ variant: "outline" }), "mt-8")}
        >
          返回场次详情
        </Link>
      </GlassCard>
    </div>
  );
}
