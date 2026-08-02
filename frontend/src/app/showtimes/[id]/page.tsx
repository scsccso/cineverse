import Link from "next/link";
import { notFound } from "next/navigation";
import { ApiError } from "@/lib/api/client";
import { getShowtime } from "@/lib/api/showtimes";
import type { ShowtimeResponse } from "@/lib/api/types";
import { GlassCard } from "@/components/glass/glass-card";
import { buttonVariants } from "@/components/ui/button";
import { formatShowDate, formatShowTime } from "@/lib/format";
import { cn } from "@/lib/utils";

async function findShowtime(id: string): Promise<ShowtimeResponse | null> {
  try {
    return await getShowtime(id);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

export default async function ShowtimeConfirmPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const showtime = await findShowtime(id);
  if (!showtime) {
    notFound();
  }

  return (
    <div className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-lg flex-col justify-center px-6 py-16">
      <GlassCard className="p-8">
        <p className="text-sm text-muted-foreground">场次确认</p>
        <h1 className="mt-2 font-display text-2xl font-semibold tracking-tight">
          {showtime.movie.title}
        </h1>
        <dl className="mt-6 space-y-3 text-sm">
          <Row label="日期" value={formatShowDate(showtime.startTime)} />
          <Row label="时间" value={formatShowTime(showtime.startTime)} />
          <Row
            label="影厅"
            value={`${showtime.hall.name} · ${showtime.hall.cinemaName}`}
          />
          <Row label="票价" value={`RM ${showtime.price.toFixed(2)}`} />
        </dl>
        <Link
          href={`/showtimes/${showtime.id}/seats`}
          className={cn(buttonVariants({ size: "lg" }), "mt-8 h-12 w-full")}
        >
          继续选座
        </Link>
      </GlassCard>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between border-b border-glass-border/60 pb-3">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-mono">{value}</dd>
    </div>
  );
}
