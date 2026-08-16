"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { AlertTriangle, ArrowLeft, CheckCircle2, Clock, CircleX } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { searchPayments } from "@/lib/api/admin-payments";
import type { AdminPaymentResponse, Page, PaymentStatus } from "@/lib/api/types";
import { formatShowDate, formatShowTime } from "@/lib/format";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Field, FieldLabel } from "@/components/ui/field";

const SELECT_CLASSNAME =
  "h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50";

const STATUS_OPTIONS: { value: PaymentStatus | ""; label: string }[] = [
  { value: "", label: "All statuses" },
  { value: "ORPHANED_SUCCESS", label: "Orphaned Success (needs reconciliation)" },
  { value: "SUCCEEDED", label: "Succeeded" },
  { value: "FAILED", label: "Failed" },
  { value: "PENDING", label: "Pending" },
];

/**
 * Read-only drill-down behind the sales report's Pending Reconciliation
 * figure (dashboard/page.tsx) — shows which specific payments make up that
 * sum, not just the total. No refund/status-change action here or planned:
 * reconciling one of these is a manual, out-of-band process (CLAUDE.md
 * Phase 6), this page's whole job stops at "here's exactly which rows".
 *
 * Not in AdminSidebar's nav on purpose — this is an occasionally-checked
 * ops page, not a primary workflow like Movies/Bookings/Users, so it's
 * reachable from where the number that motivates it already lives (the
 * dashboard's StatTile) plus a direct URL, not a permanent nav slot.
 */
export default function AdminPaymentsPage() {
  const { callAuthorized } = useAuth();
  const searchParams = useSearchParams();

  // Defaults to the one status this page exists for, whether arriving from
  // the dashboard's ?status=ORPHANED_SUCCESS link or a bare /admin/payments
  // bookmark — read once via a lazy initializer, not synced from an effect
  // (same react-hooks/set-state-in-effect avoidance as every other
  // ?param-seeded page in this codebase, e.g. admin/movies/new's
  // preselected movie).
  const [status, setStatus] = useState<PaymentStatus | "">(
    () => (searchParams.get("status") as PaymentStatus | null) ?? "ORPHANED_SUCCESS",
  );
  const [page, setPage] = useState(0);

  const [result, setResult] = useState<{ status: PaymentStatus | ""; page: number; data: Page<AdminPaymentResponse> } | null>(
    null,
  );
  const [error, setError] = useState<string | null>(null);
  const loading = !result || result.status !== status || result.page !== page;

  const requestIdRef = useRef(0);

  useEffect(() => {
    const thisRequestId = ++requestIdRef.current;
    callAuthorized((token) => searchPayments(token, { status: status || undefined, page }))
      .then((data) => {
        if (requestIdRef.current !== thisRequestId) return;
        setResult({ status, page, data });
        setError(null);
      })
      .catch(() => {
        if (requestIdRef.current !== thisRequestId) return;
        setError("Failed to load payments");
      });
  }, [callAuthorized, status, page]);

  return (
    <section className="mx-auto max-w-6xl px-6 py-10">
      <Link
        href="/admin/dashboard"
        className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" aria-hidden />
        Back to Reports
      </Link>

      <header className="mb-6">
        <h1 className="font-heading text-2xl font-semibold text-foreground">Admin · Payments</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Read-only. Reconciling an Orphaned Success payment (refund or manual booking fix) is done outside this
          system — this page only helps you find which ones need it.
        </p>
      </header>

      <div className="mb-6 max-w-sm">
        <Field>
          <FieldLabel htmlFor="status">Status</FieldLabel>
          <select
            id="status"
            className={SELECT_CLASSNAME}
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as PaymentStatus | "");
              setPage(0);
            }}
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </Field>
      </div>

      {error && (
        <div role="alert" className="mb-6 rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">
          {error}
        </div>
      )}

      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle>Payments</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="py-8 text-center text-sm text-muted-foreground">Loading...</div>
          ) : result.data.content.length === 0 ? (
            <div className="py-8 text-center text-sm text-muted-foreground">No payments match this filter</div>
          ) : (
            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full text-sm">
                <caption className="sr-only">Payments</caption>
                <thead>
                  <tr className="border-b border-border bg-muted/50 text-left text-muted-foreground">
                    <th scope="col" className="px-3 py-2 font-medium">Status</th>
                    <th scope="col" className="px-3 py-2 font-medium">Amount</th>
                    <th scope="col" className="px-3 py-2 font-medium">Customer</th>
                    <th scope="col" className="px-3 py-2 font-medium">Movie / Showtime</th>
                    <th scope="col" className="px-3 py-2 font-medium">Booking</th>
                    <th scope="col" className="px-3 py-2 font-medium">Stripe IDs</th>
                    <th scope="col" className="px-3 py-2 font-medium">Updated</th>
                  </tr>
                </thead>
                <tbody>
                  {result.data.content.map((payment) => (
                    <tr key={payment.id} className="border-b border-border last:border-0">
                      <td className="px-3 py-2">
                        <PaymentStatusBadge status={payment.status} />
                      </td>
                      <td className="px-3 py-2 font-mono text-foreground">
                        {payment.amount.toFixed(2)} {payment.currency.toUpperCase()}
                      </td>
                      <td className="px-3 py-2 text-foreground">{payment.booking.userEmail}</td>
                      <td className="px-3 py-2">
                        <span className="text-foreground">{payment.booking.movieTitle}</span>
                        <p className="font-mono text-xs text-muted-foreground">
                          {formatShowDate(payment.booking.showtimeStartTime)} ·{" "}
                          {formatShowTime(payment.booking.showtimeStartTime)} · {payment.booking.hallName}
                        </p>
                      </td>
                      <td className="px-3 py-2">
                        <Badge variant="outline">{payment.booking.status}</Badge>
                      </td>
                      <td className="px-3 py-2 font-mono text-xs text-muted-foreground">
                        <p>{payment.stripeSessionId}</p>
                        {payment.stripePaymentIntentId && <p>{payment.stripePaymentIntentId}</p>}
                      </td>
                      <td className="px-3 py-2 font-mono text-muted-foreground">
                        {formatShowDate(payment.updatedAt)} {formatShowTime(payment.updatedAt)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {result && result.data.totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between">
              <p className="text-sm text-muted-foreground">
                {result.data.totalElements} total, page {result.data.number + 1} of {result.data.totalPages}
              </p>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={result.data.first || loading}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  Previous
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={result.data.last || loading}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </section>
  );
}

/** Same icon+text+color encoding standard as every other status badge in
 * this app (1.5 节). ORPHANED_SUCCESS reuses the amber warning tone already
 * established by StatTile/ScheduleShowtimesNudge rather than inventing a
 * new one — it's the same "needs a second look" category of figure. */
function PaymentStatusBadge({ status }: { status: PaymentStatus }) {
  switch (status) {
    case "ORPHANED_SUCCESS":
      return (
        <Badge
          variant="outline"
          className="gap-1 border-[color:var(--chart-amber-border)] bg-[color:var(--chart-amber-surface)] text-[color:var(--chart-amber)]"
        >
          <AlertTriangle className="size-3" aria-hidden />
          Orphaned Success
        </Badge>
      );
    case "SUCCEEDED":
      return (
        <Badge variant="default" className="gap-1">
          <CheckCircle2 className="size-3" aria-hidden />
          Succeeded
        </Badge>
      );
    case "FAILED":
      return (
        <Badge variant="secondary" className="gap-1">
          <CircleX className="size-3" aria-hidden />
          Failed
        </Badge>
      );
    case "PENDING":
      return (
        <Badge variant="outline" className="gap-1">
          <Clock className="size-3" aria-hidden />
          Pending
        </Badge>
      );
  }
}
