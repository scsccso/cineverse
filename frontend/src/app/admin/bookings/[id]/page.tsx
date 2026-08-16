"use client";

import { useEffect, useState } from "react";
import { useParams, useSearchParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { cancelBooking, getBooking } from "@/lib/api/bookings";
import { ApiError } from "@/lib/api/client";
import type { BookingResponse } from "@/lib/api/types";
import { formatShowDate, formatShowTime } from "@/lib/format";
import { BookingStatusBadge } from "@/components/admin/booking-status-badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

/**
 * Support detail view for one booking — reuses the existing GET/DELETE
 * /api/v1/bookings/{id} exactly as the customer flow does (both already
 * support an ADMIN override server-side, see BookingService.requireAccess),
 * not a new admin-specific endpoint. No QR code render here: staff at this
 * page aren't scanning anything, they're confirming order details or
 * cancelling on a customer's behalf, so the only thing worth carrying over
 * from the customer confirmation page is whether the ticket has been
 * redeemed, not the code itself.
 *
 * <p>?email= arrives from the search results link (see admin/bookings/
 * page.tsx) purely as display context, since BookingResponse itself has no
 * owner-email field (see AdminBookingSearchResult's doc comment for why) and
 * this page deliberately doesn't add a second backend call just to recover
 * it — a direct deep-link without that param still works, it just can't
 * show whose booking this is until the search flow supplies it.
 */
export default function AdminBookingDetailPage() {
  const { id } = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const email = searchParams.get("email");
  const { callAuthorized } = useAuth();

  const [booking, setBooking] = useState<BookingResponse | null>(null);
  const [loadError, setLoadError] = useState(false);

  const [confirmOpen, setConfirmOpen] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    callAuthorized((token) => getBooking(token, id))
      .then((result) => {
        if (cancelled) return;
        setBooking(result);
      })
      .catch(() => {
        if (!cancelled) setLoadError(true);
      });
    return () => {
      cancelled = true;
    };
  }, [id, callAuthorized]);

  async function handleCancel() {
    setIsCancelling(true);
    setCancelError(null);
    try {
      await callAuthorized((token) => cancelBooking(token, id));
      setBooking((prev) => (prev ? { ...prev, status: "CANCELLED" } : prev));
      setConfirmOpen(false);
    } catch (error) {
      // Shown as-is — "Booking is X, cannot be cancelled" is already a
      // complete, specific sentence, same convention as the showtimes/
      // movies delete-409 handlers.
      setCancelError(error instanceof ApiError ? error.message : "Failed to cancel. Please try again later.");
    } finally {
      setIsCancelling(false);
    }
  }

  if (loadError) {
    return (
      <section className="mx-auto max-w-2xl px-6 py-10">
        <p className="py-8 text-center text-sm text-destructive">Failed to load this booking — it may not exist</p>
        <Link href="/admin/bookings" className="mx-auto block w-fit text-sm text-muted-foreground hover:text-foreground">
          Back to Bookings
        </Link>
      </section>
    );
  }

  return (
    <section className="mx-auto max-w-2xl px-6 py-10">
      <Link
        href="/admin/bookings"
        className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" aria-hidden />
        Back to Bookings
      </Link>

      {!booking ? (
        <p className="py-8 text-center text-sm text-muted-foreground">Loading...</p>
      ) : (
        <div className="space-y-6">
          <header>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="font-heading text-2xl font-semibold text-foreground">{booking.showtime.movieTitle}</h1>
              <BookingStatusBadge status={booking.status} redeemedAt={booking.redeemedAt} />
            </div>
            <p className="mt-1 text-sm text-muted-foreground">
              {email ? (
                <>
                  Customer: <span className="text-foreground">{email}</span>
                </>
              ) : (
                "Customer email unavailable — opened without coming from a search result"
              )}
            </p>
          </header>

          <Card className="shadow-sm">
            <CardHeader>
              <CardTitle>Showtime</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-sm">
                <dt className="text-muted-foreground">Date &amp; Time</dt>
                <dd className="text-foreground">
                  {formatShowDate(booking.showtime.startTime)} · {formatShowTime(booking.showtime.startTime)}
                </dd>
                <dt className="text-muted-foreground">Hall</dt>
                <dd className="text-foreground">{booking.showtime.hallName}</dd>
                <dt className="text-muted-foreground">Booked</dt>
                <dd className="text-foreground">
                  {formatShowDate(booking.createdAt)} {formatShowTime(booking.createdAt)}
                </dd>
                {booking.status === "CONFIRMED" && (
                  <>
                    <dt className="text-muted-foreground">Ticket</dt>
                    <dd className="text-foreground">
                      {booking.redeemedAt
                        ? `Redeemed ${formatShowDate(booking.redeemedAt)} ${formatShowTime(booking.redeemedAt)}`
                        : "Not yet redeemed"}
                    </dd>
                  </>
                )}
              </dl>
            </CardContent>
          </Card>

          <Card className="shadow-sm">
            <CardHeader>
              <CardTitle>Seats</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="space-y-2 text-sm">
                {booking.seats.map((seat) => (
                  <div key={seat.seatId} className="flex items-center justify-between">
                    <dt className="text-foreground">
                      Seat {seat.rowLabel}
                      {seat.columnNumber} · {seat.seatType === "COUPLE" ? "Couple" : "Standard"}
                    </dt>
                    <dd className="font-mono text-muted-foreground">RM {seat.priceAtBooking.toFixed(2)}</dd>
                  </div>
                ))}
                <div className="flex items-center justify-between border-t border-border pt-2 font-medium">
                  <dt className="text-foreground">Total</dt>
                  <dd className="font-mono text-foreground">RM {booking.totalPrice.toFixed(2)}</dd>
                </div>
              </dl>
            </CardContent>
          </Card>

          <Card className="shadow-sm">
            <CardHeader>
              <CardTitle>Cancel Booking</CardTitle>
            </CardHeader>
            <CardContent>
              {booking.status === "PENDING" ? (
                <>
                  <p className="mb-4 text-sm text-muted-foreground">
                    Releases the held seat(s) immediately. The customer will need to select seats again if they still
                    want to book.
                  </p>
                  <Button type="button" variant="destructive" onClick={() => setConfirmOpen(true)}>
                    Cancel Booking
                  </Button>
                </>
              ) : (
                <p className="text-sm text-muted-foreground">
                  Only a pending (unpaid) booking can be cancelled from here — this one is {booking.status.toLowerCase()}.
                </p>
              )}
            </CardContent>
          </Card>
        </div>
      )}

      {confirmOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-labelledby="confirm-cancel-title"
        >
          <Card className="w-full max-w-md shadow-lg">
            <CardHeader>
              <CardTitle id="confirm-cancel-title">Cancel Booking</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                Are you sure you want to cancel this booking? The held seat(s) will be released immediately. This
                action cannot be undone.
              </p>

              {cancelError && (
                <div role="alert" className="mt-4 rounded border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
                  {cancelError}
                </div>
              )}

              <div className="mt-6 flex justify-end gap-3">
                <Button type="button" variant="outline" onClick={() => setConfirmOpen(false)} disabled={isCancelling}>
                  Keep Booking
                </Button>
                <Button type="button" variant="destructive" onClick={handleCancel} disabled={isCancelling}>
                  {isCancelling ? "Cancelling…" : "Confirm Cancel"}
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </section>
  );
}
