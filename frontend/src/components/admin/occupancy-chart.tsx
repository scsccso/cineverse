"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { ShowtimeOccupancy } from "@/lib/api/types";
import { formatShowDate, formatShowTime } from "@/lib/format";
import { formatPercent } from "@/lib/admin/format";

interface OccupancyChartProps {
  showtimes: ShowtimeOccupancy[];
}

interface OccupancyChartPoint {
  id: string;
  label: string;
  movieTitle: string;
  hallName: string;
  occupancyRate: number;
  bookedSeats: number;
  totalSeats: number;
}

interface OccupancyTooltipProps {
  active?: boolean;
  payload?: { payload: OccupancyChartPoint }[];
}

/**
 * Each bar is a different showtime (a category), not a different series —
 * one hue for all of them is correct here (color-formula.md: "each bar
 * takes the same slot-1 hue" for nominal categories of one metric).
 */
export function OccupancyChart({ showtimes }: OccupancyChartProps) {
  const data: OccupancyChartPoint[] = showtimes.map((showtime) => ({
    id: showtime.showtimeId,
    label: `${formatShowDate(showtime.startTime)} ${formatShowTime(showtime.startTime)}`,
    movieTitle: showtime.movieTitle,
    hallName: showtime.hallName,
    occupancyRate: showtime.occupancyRate,
    bookedSeats: showtime.bookedSeats,
    totalSeats: showtime.totalSeats,
  }));

  return (
    <div className="h-72 w-full" role="img" aria-label="Bar chart of occupancy rate by showtime">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, left: 8, bottom: 8 }}>
          <CartesianGrid stroke="var(--border)" vertical={false} />
          <XAxis
            dataKey="label"
            tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
            axisLine={{ stroke: "var(--border)" }}
            tickLine={false}
            interval="preserveStartEnd"
          />
          <YAxis
            domain={[0, 1]}
            tickFormatter={(value: number) => formatPercent(value)}
            tick={{ fill: "var(--muted-foreground)", fontSize: 12 }}
            axisLine={false}
            tickLine={false}
            width={56}
          />
          <Tooltip content={<OccupancyTooltipContent />} cursor={{ fill: "var(--muted)" }} />
          <Bar
            dataKey="occupancyRate"
            fill="var(--chart-amber)"
            radius={[4, 4, 0, 0]}
            maxBarSize={24}
            isAnimationActive={false}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

function OccupancyTooltipContent({ active, payload }: OccupancyTooltipProps) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  return (
    <div className="rounded-lg border border-border bg-popover p-3 text-sm shadow-md">
      <p className="font-medium text-popover-foreground">{point.movieTitle}</p>
      <p className="text-muted-foreground">
        {point.hallName} · {point.label}
      </p>
      <p className="mt-1 font-mono font-semibold text-popover-foreground">
        {formatPercent(point.occupancyRate)}
        <span className="ml-1.5 font-sans font-normal text-muted-foreground">Occupancy</span>
      </p>
      <p className="text-muted-foreground">
        {point.bookedSeats} / {point.totalSeats} seats
      </p>
    </div>
  );
}
