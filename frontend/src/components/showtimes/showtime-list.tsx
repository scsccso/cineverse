import Link from "next/link";
import { GlassCard } from "@/components/glass/glass-card";
import { formatShowDate, formatShowTime, showDateKey } from "@/lib/format";
import type { ShowtimeResponse } from "@/lib/api/types";

export function ShowtimeList({ showtimes }: { showtimes: ShowtimeResponse[] }) {
  if (showtimes.length === 0) {
    return (
      <p className="text-muted-foreground">暂无可选场次,请稍后再来看看。</p>
    );
  }

  const groups = groupByDate(showtimes);

  return (
    <div className="space-y-8">
      {groups.map(([dateKey, dayShowtimes]) => (
        <div key={dateKey}>
          <h3 className="font-mono text-sm text-muted-foreground">
            {formatShowDate(dayShowtimes[0].startTime)}
          </h3>
          <div className="mt-3 flex flex-wrap gap-3">
            {dayShowtimes.map((showtime) => (
              <Link key={showtime.id} href={`/showtimes/${showtime.id}`}>
                <GlassCard className="px-5 py-3">
                  <div className="font-mono text-lg font-medium">
                    {formatShowTime(showtime.startTime)}
                  </div>
                  <div className="mt-1 text-xs text-muted-foreground">
                    {showtime.hall.name}
                  </div>
                  <div className="mt-1 font-mono text-sm text-primary">
                    RM {showtime.price.toFixed(2)}
                  </div>
                </GlassCard>
              </Link>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

function groupByDate(
  showtimes: ShowtimeResponse[],
): [string, ShowtimeResponse[]][] {
  const map = new Map<string, ShowtimeResponse[]>();
  for (const showtime of showtimes) {
    const key = showDateKey(showtime.startTime);
    const bucket = map.get(key);
    if (bucket) {
      bucket.push(showtime);
    } else {
      map.set(key, [showtime]);
    }
  }
  return [...map.entries()].sort(([a], [b]) => a.localeCompare(b));
}
