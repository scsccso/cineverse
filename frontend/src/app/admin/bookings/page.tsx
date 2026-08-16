"use client";

import { type FormEvent, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Search } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { searchBookings } from "@/lib/api/admin-bookings";
import type { AdminBookingSearchResult, BookingStatus, Page } from "@/lib/api/types";
import { formatShowDate, formatShowTime } from "@/lib/format";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { BookingStatusBadge } from "@/components/admin/booking-status-badge";

const SELECT_CLASSNAME =
  "h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50";

const STATUS_OPTIONS: { value: BookingStatus; label: string }[] = [
  { value: "PENDING", label: "Pending" },
  { value: "CONFIRMED", label: "Confirmed" },
  { value: "EXPIRED", label: "Expired" },
  { value: "CANCELLED", label: "Cancelled" },
];

interface AppliedSearch {
  userEmail: string;
  movieTitle: string;
  status: BookingStatus | "";
  page: number;
}

const EMPTY_SEARCH: AppliedSearch = { userEmail: "", movieTitle: "", status: "", page: 0 };

/**
 * Customer-support order lookup — before this page, an ADMIN could only
 * reach a specific booking by already knowing its UUID (GET /bookings/{id}
 * has always supported an ADMIN override, but nothing could resolve
 * "this customer's order" down to that UUID in the first place). All three
 * filters are optional and submitted together as one form, not applied
 * live-as-you-type — same reasoning as the TMDB movie search picker
 * (CLAUDE.md's "Admin 场次管理"/TMDB search decisions): an explicit
 * search action, not a request fired per keystroke.
 */
export default function AdminBookingsPage() {
  const { callAuthorized } = useAuth();

  const [userEmailInput, setUserEmailInput] = useState("");
  const [movieTitleInput, setMovieTitleInput] = useState("");
  const [statusInput, setStatusInput] = useState<BookingStatus | "">("");

  const [applied, setApplied] = useState<AppliedSearch>(EMPTY_SEARCH);
  const [result, setResult] = useState<(AppliedSearch & { data: Page<AdminBookingSearchResult> }) | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Same "loading derived from whether held data matches the current
  // request params" pattern as admin/showtimes, admin/dashboard,
  // admin/movies — result carries the params it was actually fetched for.
  const loading =
    !result ||
    result.userEmail !== applied.userEmail ||
    result.movieTitle !== applied.movieTitle ||
    result.status !== applied.status ||
    result.page !== applied.page;

  const requestIdRef = useRef(0);

  useEffect(() => {
    const thisRequestId = ++requestIdRef.current;
    callAuthorized((token) =>
      searchBookings(token, {
        userEmail: applied.userEmail || undefined,
        movieTitle: applied.movieTitle || undefined,
        status: applied.status || undefined,
        page: applied.page,
      }),
    )
      .then((data) => {
        if (requestIdRef.current !== thisRequestId) return;
        setResult({ ...applied, data });
        setError(null);
      })
      .catch(() => {
        if (requestIdRef.current !== thisRequestId) return;
        setError("Failed to search bookings");
      });
  }, [callAuthorized, applied]);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setApplied({
      userEmail: userEmailInput.trim(),
      movieTitle: movieTitleInput.trim(),
      status: statusInput,
      page: 0,
    });
  }

  return (
    <section className="mx-auto max-w-6xl px-6 py-10">
      <header className="mb-6">
        <h1 className="font-heading text-2xl font-semibold text-foreground">Admin · Bookings</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Look up a customer&apos;s order for support — by email, movie, or status. Leave everything
          blank to browse all bookings.
        </p>
      </header>

      <Card className="mb-6 shadow-sm">
        <CardHeader>
          <CardTitle>Search</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="grid gap-4 sm:grid-cols-3">
            <Field>
              <FieldLabel htmlFor="userEmail">Customer Email</FieldLabel>
              <Input
                id="userEmail"
                placeholder="fan@example.com"
                value={userEmailInput}
                onChange={(event) => setUserEmailInput(event.target.value)}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="movieTitle">Movie Title</FieldLabel>
              <Input
                id="movieTitle"
                placeholder="Interstellar"
                value={movieTitleInput}
                onChange={(event) => setMovieTitleInput(event.target.value)}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="status">Status</FieldLabel>
              <select
                id="status"
                className={SELECT_CLASSNAME}
                value={statusInput}
                onChange={(event) => setStatusInput(event.target.value as BookingStatus | "")}
              >
                <option value="">Any status</option>
                {STATUS_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </Field>
            <div className="sm:col-span-3">
              <Button type="submit" className="h-11">
                <Search className="size-4" aria-hidden />
                Search
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {error && (
        <div role="alert" className="mb-6 rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">
          {error}
        </div>
      )}

      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle>Results</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="py-8 text-center text-sm text-muted-foreground">Loading...</div>
          ) : !result || result.data.content.length === 0 ? (
            <div className="py-8 text-center text-sm text-muted-foreground">No bookings match this search</div>
          ) : (
            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full text-sm">
                <caption className="sr-only">Bookings</caption>
                <thead>
                  <tr className="border-b border-border bg-muted/50 text-left text-muted-foreground">
                    <th scope="col" className="px-3 py-2 font-medium">Status</th>
                    <th scope="col" className="px-3 py-2 font-medium">Movie / Showtime</th>
                    <th scope="col" className="px-3 py-2 font-medium">Seats</th>
                    <th scope="col" className="px-3 py-2 font-medium">Customer</th>
                    <th scope="col" className="px-3 py-2 font-medium">Created</th>
                  </tr>
                </thead>
                <tbody>
                  {result.data.content.map((row) => (
                    <tr key={row.booking.id} className="border-b border-border last:border-0">
                      <td className="px-3 py-2">
                        <BookingStatusBadge status={row.booking.status} redeemedAt={row.booking.redeemedAt} />
                      </td>
                      <td className="px-3 py-2">
                        <Link href={`/admin/bookings/${row.booking.id}?email=${encodeURIComponent(row.userEmail)}`} className="block hover:underline">
                          <span className="text-foreground">{row.booking.showtime.movieTitle}</span>
                          <p className="font-mono text-xs text-muted-foreground">
                            {formatShowDate(row.booking.showtime.startTime)} ·{" "}
                            {formatShowTime(row.booking.showtime.startTime)} · {row.booking.showtime.hallName}
                          </p>
                        </Link>
                      </td>
                      <td className="px-3 py-2 font-mono text-foreground">
                        {row.booking.seats.map((seat) => `${seat.rowLabel}${seat.columnNumber}`).join(", ")}
                      </td>
                      <td className="px-3 py-2 text-foreground">{row.userEmail}</td>
                      <td className="px-3 py-2 font-mono text-muted-foreground">
                        {formatShowDate(row.booking.createdAt)} {formatShowTime(row.booking.createdAt)}
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
                  onClick={() => setApplied((prev) => ({ ...prev, page: prev.page - 1 }))}
                >
                  Previous
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={result.data.last || loading}
                  onClick={() => setApplied((prev) => ({ ...prev, page: prev.page + 1 }))}
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
