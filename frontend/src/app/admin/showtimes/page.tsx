"use client";

import { useEffect, useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { Plus } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { deleteShowtime, listShowtimes } from "@/lib/api/admin-showtimes";
import { ApiError, resolveMediaUrl } from "@/lib/api/client";
import type { ShowtimeResponse } from "@/lib/api/types";
import { formatShowTime, showDateKey } from "@/lib/format";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";

export default function AdminShowtimesPage() {
  const { callAuthorized } = useAuth();
  const [date, setDate] = useState<string>(() => showDateKey(new Date().toISOString()));
  const [error, setError] = useState<string | null>(null);

  // Same "loading is derived from whether the data on hand matches the
  // current request params" pattern as admin/dashboard, admin/users,
  // admin/movies — result carries the date it was actually fetched for, so a
  // date change shows a loading state immediately instead of the previous
  // date's rows lingering on screen with a stale-looking table.
  const [result, setResult] = useState<{ date: string; showtimes: ShowtimeResponse[] } | null>(null);
  const loading = !result || result.date !== date;
  const showtimes = result?.date === date ? result.showtimes : null;

  const [confirmDialog, setConfirmDialog] = useState<{
    isOpen: boolean;
    title: string;
    description: string;
    action: () => Promise<void>;
  }>({ isOpen: false, title: "", description: "", action: async () => {} });
  const [actionError, setActionError] = useState<string | null>(null);

  const requestIdRef = useRef(0);

  useEffect(() => {
    const thisRequestId = ++requestIdRef.current;
    listShowtimes({ date })
      .then((data) => {
        if (requestIdRef.current !== thisRequestId) return;
        setResult({ date, showtimes: data });
        setError(null);
      })
      .catch(() => {
        if (requestIdRef.current !== thisRequestId) return;
        setResult(null);
        setError("Failed to load showtimes");
      });
  }, [date]);

  const handleDelete = (showtime: ShowtimeResponse) => {
    setConfirmDialog({
      isOpen: true,
      title: "Delete Showtime",
      description: `Are you sure you want to delete "${showtime.movie.title}" at ${formatShowTime(showtime.startTime)}? This action cannot be undone.`,
      action: async () => {
        try {
          await callAuthorized((token) => deleteShowtime(token, showtime.id));
          setResult((prev) => (prev ? { ...prev, showtimes: prev.showtimes.filter((s) => s.id !== showtime.id) } : prev));
          closeDialog();
        } catch (err: unknown) {
          // Shown as-is, unlike the create form's 409 — "still has bookings"
          // is already a complete, clear sentence, nothing to translate.
          // Same reasoning as admin/movies/page.tsx's delete handler.
          setActionError(err instanceof ApiError ? err.message : "Failed to delete. Please try again later.");
        }
      },
    });
  };

  const closeDialog = () => {
    setConfirmDialog((prev) => ({ ...prev, isOpen: false }));
    setActionError(null);
  };

  return (
    <section className="mx-auto max-w-6xl px-6 py-10">
      <header className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl font-semibold text-foreground">Admin · Showtimes</h1>
          <p className="mt-1 text-sm text-muted-foreground">Browse the schedule by date, add or delete showtimes</p>
        </div>
        <Link href="/admin/showtimes/new" className="inline-flex">
          <Button type="button" className="h-11">
            <Plus className="size-4" aria-hidden />
            Add Showtime
          </Button>
        </Link>
      </header>

      <div className="mb-6 max-w-56">
        <Field>
          <FieldLabel htmlFor="date-filter">Date</FieldLabel>
          <Input id="date-filter" type="date" value={date} onChange={(event) => setDate(event.target.value)} />
        </Field>
      </div>

      {error && (
        <div role="alert" className="mb-6 rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">
          {error}
        </div>
      )}

      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle>Showtimes</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="py-8 text-center text-sm text-muted-foreground">Loading...</div>
          ) : !showtimes || showtimes.length === 0 ? (
            <div className="py-8 text-center text-sm text-muted-foreground">No showtimes on this day</div>
          ) : (
            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full text-sm">
                <caption className="sr-only">Showtimes</caption>
                <thead>
                  <tr className="border-b border-border bg-muted/50 text-left text-muted-foreground">
                    <th scope="col" className="px-3 py-2 font-medium">Movie</th>
                    <th scope="col" className="px-3 py-2 font-medium">Hall</th>
                    <th scope="col" className="px-3 py-2 font-medium">Time</th>
                    <th scope="col" className="px-3 py-2 font-medium">Price</th>
                    <th scope="col" className="px-3 py-2 font-medium">Seats</th>
                    <th scope="col" className="px-3 py-2 font-medium text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {showtimes.map((showtime) => (
                    <tr key={showtime.id} className="border-b border-border last:border-0">
                      <td className="px-3 py-2">
                        <div className="flex items-center gap-3">
                          <div className="relative aspect-[2/3] w-9 shrink-0 overflow-hidden rounded border border-border bg-muted">
                            <Image
                              src={resolveMediaUrl(showtime.movie.posterUrl)}
                              alt=""
                              fill
                              sizes="36px"
                              className="object-cover"
                            />
                          </div>
                          <span className="text-foreground">{showtime.movie.title}</span>
                        </div>
                      </td>
                      <td className="px-3 py-2 text-foreground">{showtime.hall.name}</td>
                      <td className="px-3 py-2 font-mono text-foreground">{formatShowTime(showtime.startTime)}</td>
                      <td className="px-3 py-2 font-mono text-foreground">RM {showtime.price.toFixed(2)}</td>
                      <td className="px-3 py-2">
                        <Badge variant={showtime.bookedSeats >= showtime.totalSeats ? "default" : "outline"}>
                          <span className="font-mono">
                            {showtime.bookedSeats} / {showtime.totalSeats}
                          </span>
                        </Badge>
                      </td>
                      <td className="px-3 py-2 text-right">
                        <Button type="button" variant="destructive" size="sm" onClick={() => handleDelete(showtime)}>
                          Delete
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {confirmDialog.isOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-labelledby="confirm-dialog-title"
        >
          <Card className="w-full max-w-md shadow-lg">
            <CardHeader>
              <CardTitle id="confirm-dialog-title">{confirmDialog.title}</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">{confirmDialog.description}</p>

              {actionError && (
                <div role="alert" className="mt-4 rounded border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
                  {actionError}
                </div>
              )}

              <div className="mt-6 flex justify-end gap-3">
                <Button type="button" variant="outline" onClick={closeDialog}>
                  Cancel
                </Button>
                <Button type="button" variant="default" onClick={confirmDialog.action}>
                  Confirm
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </section>
  );
}
