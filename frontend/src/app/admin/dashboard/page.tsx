"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth/auth-context";
import {
  exportOccupancyReport,
  exportSalesReport,
  getOccupancyReport,
  getSalesReport,
} from "@/lib/api/admin-reports";
import type {
  OccupancyReportResponse,
  ReportGranularity,
  SalesBucket,
  SalesReportResponse,
  ShowtimeOccupancy,
} from "@/lib/api/types";
import { resolvePreset } from "@/lib/admin/date-range";
import { formatCurrency, formatPercent } from "@/lib/admin/format";
import { formatShowDate, formatShowTime } from "@/lib/format";
import { DateRangeFilter, type DateRangeFilterValue } from "@/components/admin/date-range-filter";
import { StatTile } from "@/components/admin/stat-tile";
import { SalesChart } from "@/components/admin/sales-chart";
import { OccupancyChart } from "@/components/admin/occupancy-chart";
import { ExportButtons } from "@/components/admin/export-buttons";
import { ReportCardSkeleton } from "@/components/admin/report-card-skeleton";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

const GRANULARITY_OPTIONS: { value: ReportGranularity; label: string }[] = [
  { value: "DAY", label: "Day" },
  { value: "WEEK", label: "Week" },
  { value: "MONTH", label: "Month" },
];

export default function AdminDashboardPage() {
  const { callAuthorized } = useAuth();
  const [filter, setFilter] = useState<DateRangeFilterValue>(() => ({ preset: "7d", ...resolvePreset("7d") }));
  const [granularity, setGranularity] = useState<ReportGranularity>("DAY");

  const [sales, setSales] = useState<SalesReportResponse | null>(null);
  const [occupancy, setOccupancy] = useState<OccupancyReportResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Bumped by the "Retry" button to re-run the effect below without touching
  // filter/granularity — a plain event-handler setState, not one inside the
  // effect body, so it's outside react-hooks/set-state-in-effect's concern.
  const [retryToken, setRetryToken] = useState(0);

  // Every setState here runs inside a .then()/.catch() callback (i.e. after
  // the fetch actually settles), never synchronously in the effect body
  // itself — react-hooks/set-state-in-effect flags the latter as a
  // cascading-render anti-pattern (see seat-picker.tsx's booking-resume
  // effect for the same shape already in this codebase). "Loading" is
  // deliberately not a separate flag toggled here — see salesLoading/
  // occupancyLoading below, derived during render instead.
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      callAuthorized((token) => getSalesReport(token, { from: filter.from, to: filter.to, granularity })),
      callAuthorized((token) => getOccupancyReport(token, { from: filter.from, to: filter.to })),
    ])
      .then(([salesReport, occupancyReport]) => {
        if (cancelled) return;
        setSales(salesReport);
        setOccupancy(occupancyReport);
        setError(null);
      })
      .catch(() => {
        if (!cancelled) setError("Failed to load reports. Please try again later.");
      });
    return () => {
      cancelled = true;
    };
  }, [callAuthorized, filter.from, filter.to, granularity, retryToken]);

  // "Loading" = the data on hand doesn't match the current filter yet —
  // computed from the response's own echoed from/to/granularity rather than
  // a manually-toggled boolean, so it can never drift out of sync with what
  // asked for it (and sidesteps needing a synchronous setState in the effect
  // above just to flip a flag before the fetch starts).
  const salesLoading =
    !sales || sales.from !== filter.from || sales.to !== filter.to || sales.granularity !== granularity;
  const occupancyLoading = !occupancy || occupancy.from !== filter.from || occupancy.to !== filter.to;

  const totalBookings = sales?.buckets.reduce((sum, bucket) => sum + bucket.bookingCount, 0) ?? 0;

  return (
    <section className="mx-auto max-w-6xl px-6 py-10">
      <header className="mb-6">
        <h1 className="font-heading text-2xl font-semibold text-foreground">Admin · Reports</h1>
        <p className="mt-1 text-sm text-muted-foreground">Sales and occupancy reports (Phase 8)</p>
      </header>

      <div className="mb-8 rounded-xl border border-border bg-card p-4 shadow-sm">
        <DateRangeFilter value={filter} onChange={setFilter} />
      </div>

      {error && (
        <div
          role="alert"
          className="mb-6 flex flex-wrap items-center gap-3 rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive"
        >
          {error}
          <Button type="button" variant="outline" size="sm" onClick={() => setRetryToken((token) => token + 1)}>
            Retry
          </Button>
        </div>
      )}

      <div className="space-y-8">
        <Card className="shadow-sm">
          <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3">
            <CardTitle>Sales Report</CardTitle>
            <div className="flex flex-wrap items-center gap-3">
              <div className="flex gap-1" role="group" aria-label="Time granularity">
                {GRANULARITY_OPTIONS.map((option) => (
                  <Button
                    key={option.value}
                    type="button"
                    size="sm"
                    variant={granularity === option.value ? "default" : "outline"}
                    aria-pressed={granularity === option.value}
                    onClick={() => setGranularity(option.value)}
                  >
                    {option.label}
                  </Button>
                ))}
              </div>
              {sales && (
                <ExportButtons
                  onExport={(format) =>
                    callAuthorized((token) =>
                      exportSalesReport(token, { from: filter.from, to: filter.to, granularity, format }),
                    )
                  }
                />
              )}
            </div>
          </CardHeader>
          <CardContent>
            {salesLoading || !sales ? (
              <ReportCardSkeleton />
            ) : (
              <>
                <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
                  <StatTile
                    label="Total Revenue"
                    value={formatCurrency(sales.totalRevenue, sales.currency)}
                    hint={`${filter.from} to ${filter.to}, CONFIRMED bookings only`}
                  />
                  <StatTile label="Bookings" value={String(totalBookings)} />
                  <StatTile
                    label="Pending Reconciliation"
                    value={formatCurrency(sales.pendingReconciliationAmount, sales.currency)}
                    hint="Stripe reported a successful payment that isn't counted in revenue — needs manual reconciliation"
                    tone={sales.pendingReconciliationAmount > 0 ? "warning" : "default"}
                  />
                </div>
                <SalesChart buckets={sales.buckets} currency={sales.currency} />
                <SalesTable buckets={sales.buckets} currency={sales.currency} />
              </>
            )}
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3">
            <CardTitle>Occupancy Analysis</CardTitle>
            {occupancy && occupancy.showtimes.length > 0 && (
              <ExportButtons
                onExport={(format) =>
                  callAuthorized((token) =>
                    exportOccupancyReport(token, { from: filter.from, to: filter.to, format }),
                  )
                }
              />
            )}
          </CardHeader>
          <CardContent>
            {occupancyLoading || !occupancy ? (
              <ReportCardSkeleton />
            ) : occupancy.showtimes.length === 0 ? (
              <p className="py-8 text-center text-sm text-muted-foreground">No showtimes in the selected date range</p>
            ) : (
              <>
                <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
                  <StatTile label="Overall Occupancy" value={formatPercent(occupancy.overallOccupancyRate)} />
                  <StatTile label="Booked Seats" value={String(occupancy.totalBookedSeats)} />
                  <StatTile label="Total Seats" value={String(occupancy.totalSeats)} />
                </div>
                <OccupancyChart showtimes={occupancy.showtimes} />
                <OccupancyTable showtimes={occupancy.showtimes} />
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </section>
  );
}

/** Progressive disclosure so a 30-day daily table doesn't dominate the card by default — <details> is natively keyboard/screen-reader accessible, no custom disclosure widget needed. Exists so every charted value is also reachable as plain text (dataviz skill: "a table view exists"). */
function SalesTable({ buckets, currency }: { buckets: SalesBucket[]; currency: string }) {
  return (
    <details className="mt-4 group/details">
      <summary className="flex min-h-11 cursor-pointer items-center text-sm text-muted-foreground hover:text-foreground">
        View data table ({buckets.length} rows)
      </summary>
      <div className="overflow-x-auto rounded-lg border border-border">
        <table className="w-full text-sm">
          <caption className="sr-only">Revenue breakdown by period</caption>
          <thead>
            <tr className="border-b border-border bg-muted/50 text-left text-muted-foreground">
              <th scope="col" className="px-3 py-2 font-medium">Period</th>
              <th scope="col" className="px-3 py-2 font-medium">Revenue</th>
              <th scope="col" className="px-3 py-2 font-medium">Bookings</th>
            </tr>
          </thead>
          <tbody>
            {buckets.map((bucket) => (
              <tr key={bucket.periodStart} className="border-b border-border last:border-0">
                <td className="px-3 py-2 font-mono text-foreground">{bucket.periodStart}</td>
                <td className="px-3 py-2 font-mono text-foreground">{formatCurrency(bucket.revenue, currency)}</td>
                <td className="px-3 py-2 font-mono text-foreground">{bucket.bookingCount}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}

function OccupancyTable({ showtimes }: { showtimes: ShowtimeOccupancy[] }) {
  return (
    <details className="mt-4 group/details">
      <summary className="flex min-h-11 cursor-pointer items-center text-sm text-muted-foreground hover:text-foreground">
        View data table ({showtimes.length} showtimes)
      </summary>
      <div className="overflow-x-auto rounded-lg border border-border">
        <table className="w-full text-sm">
          <caption className="sr-only">Occupancy breakdown by showtime</caption>
          <thead>
            <tr className="border-b border-border bg-muted/50 text-left text-muted-foreground">
              <th scope="col" className="px-3 py-2 font-medium">Showtime</th>
              <th scope="col" className="px-3 py-2 font-medium">Movie</th>
              <th scope="col" className="px-3 py-2 font-medium">Hall</th>
              <th scope="col" className="px-3 py-2 font-medium">Booked/Total Seats</th>
              <th scope="col" className="px-3 py-2 font-medium">Occupancy</th>
            </tr>
          </thead>
          <tbody>
            {showtimes.map((showtime) => (
              <tr key={showtime.showtimeId} className="border-b border-border last:border-0">
                <td className="px-3 py-2 font-mono text-foreground">
                  {formatShowDate(showtime.startTime)} {formatShowTime(showtime.startTime)}
                </td>
                <td className="px-3 py-2 text-foreground">{showtime.movieTitle}</td>
                <td className="px-3 py-2 text-foreground">{showtime.hallName}</td>
                <td className="px-3 py-2 font-mono text-foreground">
                  {showtime.bookedSeats} / {showtime.totalSeats}
                </td>
                <td className="px-3 py-2 font-mono text-foreground">{formatPercent(showtime.occupancyRate)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}
