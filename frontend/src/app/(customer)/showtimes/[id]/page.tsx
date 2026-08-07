import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ApiError, resolveMediaUrl } from "@/lib/api/client";
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
    <div className="relative flex min-h-[calc(100vh-4rem)] items-center justify-center overflow-hidden">
      {/* Same backdrop pattern as movies/[id]/page.tsx — keeps the movie's
          visual identity present through the confirm step instead of
          dropping to a bare card on a black screen (design-proposal-
          customer-editorial.md, finding 3-a). Purely decorative, so it's
          not wired into anything the card itself renders. */}
      <Image
        src={resolveMediaUrl(showtime.movie.backdropUrl)}
        alt=""
        fill
        sizes="100vw"
        className="object-cover"
      />
      <div className="absolute inset-0 bg-gradient-to-t from-background via-background/85 to-background/45" />

      <div className="relative z-10 mx-auto w-full max-w-lg px-6 py-16">
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
