"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { TooltipContentProps } from "recharts";
import type { SalesBucket } from "@/lib/api/types";
import { formatCurrency } from "@/lib/admin/format";

interface SalesChartProps {
  buckets: SalesBucket[];
  currency: string;
}

interface SalesChartPoint {
  period: string;
  revenue: number;
  bookingCount: number;
}

/**
 * Single series (revenue), so one flat hue and no legend box — the card
 * title already says what's plotted (dataviz skill: "a single series needs
 * no legend box"). bookingCount rides along in the tooltip as supplementary
 * data rather than a second scale — this project never does dual-axis
 * charts.
 */
export function SalesChart({ buckets, currency }: SalesChartProps) {
  const data: SalesChartPoint[] = buckets.map((bucket) => ({
    period: bucket.periodStart,
    revenue: bucket.revenue,
    bookingCount: bucket.bookingCount,
  }));

  return (
    <div className="h-72 w-full" role="img" aria-label="按时间段的营收柱状图">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, left: 8, bottom: 8 }}>
          <CartesianGrid stroke="var(--border)" vertical={false} />
          <XAxis
            dataKey="period"
            tick={{ fill: "var(--muted-foreground)", fontSize: 12 }}
            axisLine={{ stroke: "var(--border)" }}
            tickLine={false}
            minTickGap={24}
          />
          <YAxis
            tick={{ fill: "var(--muted-foreground)", fontSize: 12 }}
            axisLine={false}
            tickLine={false}
            width={72}
            tickFormatter={(value: number) => formatCurrency(value, currency, { compact: true })}
          />
          <Tooltip content={renderSalesTooltip(currency)} cursor={{ fill: "var(--muted)" }} />
          <Bar
            dataKey="revenue"
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

/**
 * Returns a tooltip renderer closing over `currency` instead of accepting it
 * as a prop — recharts calls `content` with its own TooltipContentProps
 * shape (imported from recharts itself, rather than hand-rolled, since its
 * `label`/`payload` fields have more permissive types — e.g. `payload[].
 * payload` is `any` — than a naive guess would assign them). Currying
 * `currency` in via the enclosing SalesChart render keeps that imported
 * type untouched.
 */
function renderSalesTooltip(currency: string) {
  return function SalesTooltipContent({ active, label, payload }: TooltipContentProps) {
    if (!active || !payload?.length) return null;
    const point = payload[0].payload as SalesChartPoint;
    return (
      <div className="rounded-lg border border-border bg-popover p-3 text-sm shadow-md">
        <p className="font-medium text-popover-foreground">{label}</p>
        <p className="mt-1 font-mono font-semibold text-popover-foreground">
          {formatCurrency(point.revenue, currency)}
          <span className="ml-1.5 font-sans font-normal text-muted-foreground">营收</span>
        </p>
        <p className="text-muted-foreground">{point.bookingCount} 笔订单</p>
      </div>
    );
  };
}
