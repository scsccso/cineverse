import Link from "next/link";
import { AlertTriangle, CalendarPlus } from "lucide-react";
import type { MovieStatus } from "@/lib/api/types";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

interface ScheduleShowtimesNudgeProps {
  movieId: string;
  status: MovieStatus;
  /** null covers both "still loading" and "the count fetch failed" — either
   * way the safe default is to show nothing rather than risk a false nudge,
   * see admin/movies/[id]/edit/page.tsx's upcomingShowtimeCount fetch. */
  upcomingShowtimeCount: number | null;
  onMarkEnded: () => void;
  isMarkingEnded: boolean;
}

/** Data-driven, not dismissible — recomputed from real data on every page
 * load, so it only shows up while the condition it describes is still true;
 * there's no separate "dismissed" flag to track, and an established movie
 * that already has showtimes never sees this again. Renders nothing once
 * the movie has at least one upcoming showtime, or once it's ENDED (both
 * are steady states, not something to keep nagging about).
 *
 * COMING_SOON and NOW_PLAYING get deliberately different treatments below
 * — they're not the same situation. The first is the expected state right
 * after creating a movie (calm, untinted card). The second is a real
 * mismatch between what the site tells customers and what's actually
 * bookable, so it reuses the same amber warning treatment already
 * established by StatTile's tone="warning" rather than inventing a new one. */
export function ScheduleShowtimesNudge({
  movieId,
  status,
  upcomingShowtimeCount,
  onMarkEnded,
  isMarkingEnded,
}: ScheduleShowtimesNudgeProps) {
  if (upcomingShowtimeCount === null || upcomingShowtimeCount > 0 || status === "ENDED") {
    return null;
  }

  const scheduleCta = (
    <Link href={`/admin/showtimes/new?movieId=${movieId}`} className="inline-flex">
      <Button type="button">
        <CalendarPlus className="size-4" aria-hidden />
        Schedule a Showtime
      </Button>
    </Link>
  );

  if (status === "NOW_PLAYING") {
    return (
      <Card className="shadow-sm ring-[color:var(--chart-amber-border)] bg-[color:var(--chart-amber-surface)]">
        <CardContent className="flex items-start gap-3">
          <AlertTriangle className="mt-0.5 size-5 shrink-0 text-[color:var(--chart-amber)]" aria-hidden />
          <div className="flex-1 space-y-3">
            <div>
              <p className="font-medium text-foreground">Marked as Now Playing, but nothing&apos;s scheduled</p>
              <p className="mt-1 text-sm text-muted-foreground">
                Customers can&apos;t book this movie until a showtime is added. Schedule one, or update the status if
                its run has ended.
              </p>
            </div>
            <div className="flex flex-wrap gap-3">
              {scheduleCta}
              <Button type="button" variant="outline" onClick={onMarkEnded} disabled={isMarkingEnded}>
                {isMarkingEnded ? "Marking as Ended…" : "Mark as Ended"}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="shadow-sm">
      <CardContent className="flex items-start gap-3">
        <CalendarPlus className="mt-0.5 size-5 shrink-0 text-muted-foreground" aria-hidden />
        <div className="flex-1 space-y-3">
          <div>
            <p className="font-medium text-foreground">No showtimes scheduled yet</p>
            <p className="mt-1 text-sm text-muted-foreground">
              This movie won&apos;t be bookable until at least one showtime is scheduled.
            </p>
          </div>
          {scheduleCta}
        </div>
      </CardContent>
    </Card>
  );
}
