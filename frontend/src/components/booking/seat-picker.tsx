"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { useAuth } from "@/lib/auth/auth-context";
import { ApiError } from "@/lib/api/client";
import { getShowtimeSeats } from "@/lib/api/showtimes";
import { cancelBooking, createBooking, createCheckoutSession, getBooking } from "@/lib/api/bookings";
import type { BookingResponse, SeatStatusEntry, ShowtimeSeatsResponse } from "@/lib/api/types";
import { SeatMap } from "@/components/booking/seat-map";
import { BookingConfirmation } from "@/components/booking/booking-confirmation";
import { GlassCard } from "@/components/glass/glass-card";
import { Button } from "@/components/ui/button";
import { AnimatedFormBanner } from "@/components/motion/animated-form-banner";
import { SubmitProgressBar } from "@/components/motion/submit-progress-bar";
import { EASE_APPLE } from "@/lib/motion";

const POLL_INTERVAL_MS = 4000;
/** How long a just-cleared "someone else took your seat" notice stays on screen. */
const NOTICE_TIMEOUT_MS = 6000;

interface SeatPickerProps {
  showtimeId: string;
  movieTitle: string;
  movieBackdropUrl: string;
  hallLabel: string;
  showDate: string;
  showTime: string;
  pricePerSeat: number;
  initialSeatData: ShowtimeSeatsResponse;
  /** Present when redirected back here from Stripe's cancel_url — see the resume effect below. */
  initialBookingId?: string;
}

export function SeatPicker({
  showtimeId,
  movieTitle,
  movieBackdropUrl,
  hallLabel,
  showDate,
  showTime,
  pricePerSeat,
  initialSeatData,
  initialBookingId,
}: SeatPickerProps) {
  const { status, callAuthorized } = useAuth();
  const router = useRouter();
  const reduceMotion = useReducedMotion();

  const [seatData, setSeatData] = useState(initialSeatData);
  const [selectedSeatIds, setSelectedSeatIds] = useState<Set<string>>(new Set());
  const [booking, setBooking] = useState<BookingResponse | null>(null);
  const [expired, setExpired] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  const [isCheckingOut, setIsCheckingOut] = useState(false);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);

  // Stripe's cancel_url sends the user back here with ?bookingId=... — the
  // booking itself is still PENDING (Stripe doesn't notify us on cancel; it
  // just relies on Phase 5's lazy expiry, see CLAUDE.md Phase 6). Without
  // this, the user would land on an ordinary seat grid with their own seats
  // showing as LOCKED and no way back to the pay screen for this booking
  // until it expires. router.replace strips the query param afterward so a
  // manual refresh of this page doesn't re-trigger the resume attempt.
  useEffect(() => {
    if (!initialBookingId || status !== "authenticated") return;
    let cancelled = false;
    callAuthorized((token) => getBooking(token, initialBookingId))
      .then((result) => {
        if (!cancelled && result.status === "PENDING") {
          setBooking(result);
        }
      })
      .catch(() => {
        // Not ours, gone, or already resolved — fall back to the seat grid.
      })
      .finally(() => {
        if (!cancelled) {
          router.replace(`/showtimes/${showtimeId}/seats`);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [initialBookingId, status, callAuthorized, router, showtimeId]);

  const seatById = useMemo(() => {
    const map = new Map<string, SeatStatusEntry>();
    for (const seat of seatData.seats) map.set(seat.seatId, seat);
    return map;
  }, [seatData]);

  // Booking is null and not expired => the seat grid is what's on screen.
  const isSelecting = booking === null && !expired;

  const refreshSeats = useCallback(async (): Promise<ShowtimeSeatsResponse> => {
    const fresh = await getShowtimeSeats(showtimeId);
    setSeatData(fresh);
    return fresh;
  }, [showtimeId]);

  // Poll only while the grid is actually visible — no point refetching seat
  // status while the user is looking at the countdown/confirmation screen.
  useEffect(() => {
    if (!isSelecting) return;
    const interval = setInterval(() => {
      refreshSeats().catch(() => {
        // Transient network hiccup — the next tick retries, don't interrupt the user.
      });
    }, POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [isSelecting, refreshSeats]);

  // Whenever seat data changes (poll tick or a manual refresh), drop any
  // locally-selected seat someone else has since taken and say which ones —
  // otherwise the user is left staring at a selection that can never submit.
  // This adjusts state during render (comparing against the seatById map from
  // the last render) rather than in a useEffect, per React's guidance for
  // "adjusting state when a prop changes" — it bails out after one extra
  // render once lastSeatById catches up, so it can't loop.
  const [lastSeatById, setLastSeatById] = useState(seatById);
  if (seatById !== lastSeatById) {
    setLastSeatById(seatById);
    setSelectedSeatIds((current) => {
      if (current.size === 0) return current;
      const stillAvailable = new Set<string>();
      const removedLabels: string[] = [];
      for (const seatId of current) {
        const seat = seatById.get(seatId);
        if (seat && seat.status === "AVAILABLE") {
          stillAvailable.add(seatId);
        } else if (seat) {
          removedLabels.push(seatLabel(seat));
        }
      }
      if (removedLabels.length > 0) {
        setNotice(`Seats ${removedLabels.join(", ")} were just taken by another user and have been deselected for you`);
      }
      return stillAvailable.size === current.size ? current : stillAvailable;
    });
  }

  useEffect(() => {
    if (!notice) return;
    const timeout = setTimeout(() => setNotice(null), NOTICE_TIMEOUT_MS);
    return () => clearTimeout(timeout);
  }, [notice]);

  function toggleSeat(seat: SeatStatusEntry) {
    if (seat.status !== "AVAILABLE") return;
    setErrorMessage(null);
    setSelectedSeatIds((current) => {
      const next = new Set(current);
      if (next.has(seat.seatId)) {
        next.delete(seat.seatId);
      } else {
        next.add(seat.seatId);
      }
      return next;
    });
  }

  const selectedSeats = [...selectedSeatIds]
    .map((id) => seatById.get(id))
    .filter((seat): seat is SeatStatusEntry => Boolean(seat));
  const totalPrice = selectedSeats.length * pricePerSeat;

  async function handleConfirm() {
    if (selectedSeats.length === 0) return;

    if (status !== "authenticated") {
      setErrorMessage("Please log in to confirm your seats — redirecting to the login page…");
      setTimeout(() => {
        router.push(`/login?from=${encodeURIComponent(`/showtimes/${showtimeId}/seats`)}`);
      }, 900);
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);
    try {
      const created = await callAuthorized((token) =>
        createBooking(token, { showtimeId, seatIds: [...selectedSeatIds] }),
      );
      setBooking(created);
      setSelectedSeatIds(new Set());
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        // Don't parse the 409's message for which seats — refetching and
        // diffing against the current selection (handled by the seatById
        // effect above) is more robust than depending on the exact wording
        // of a server error string, and it catches every taken seat, not
        // just the first one the backend happened to report.
        setErrorMessage("Submission failed: some seats were just locked by another user. The seat map has been refreshed — please choose again");
        await refreshSeats();
      } else {
        setErrorMessage("Submission failed. Please try again later.");
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleCancel() {
    if (!booking) return;
    setIsCancelling(true);
    try {
      await callAuthorized((token) => cancelBooking(token, booking.id));
    } catch {
      // Even if this fails (e.g. it already lazily expired server-side),
      // fall through and reset the local view — the seat refresh below
      // reconciles with whatever the real state turns out to be.
    } finally {
      setIsCancelling(false);
      setBooking(null);
      setExpired(false);
      refreshSeats().catch(() => {});
    }
  }

  function handleExpire() {
    setBooking(null);
    setExpired(true);
  }

  function handleReturnToSelection() {
    setExpired(false);
    refreshSeats().catch(() => {});
  }

  async function handleCheckout() {
    if (!booking) return;
    setIsCheckingOut(true);
    setCheckoutError(null);
    try {
      const session = await callAuthorized((token) => createCheckoutSession(token, booking.id));
      // Plain full-page redirect to Stripe's hosted URL — no Stripe.js
      // needed since the session was created server-side (see
      // BookingController.checkout / PaymentService).
      window.location.href = session.checkoutUrl;
    } catch {
      setCheckoutError("Failed to start checkout. Please try again later.");
      setIsCheckingOut(false);
    }
  }

  // These three screens share one route and swap on local state, so
  // PageTransition — which keys off pathname — never sees them. Until now
  // they replaced each other in a single frame: the last hard cut left in
  // the customer flow, landing exactly where the user most needs to see the
  // system keep up (submit succeeded, or the hold ran out).
  //
  // Opacity only, deliberately no y-offset. The seat grid's
  // SelectionSummaryBar is `position: fixed`, and a transformed ancestor
  // becomes the containing block for its fixed descendants — animating y
  // here would re-anchor that bar to this wrapper mid-transition instead of
  // the viewport. A crossfade is also the truer read for an in-place swap
  // than a directional slide, which implies navigation that isn't happening.
  const screen = booking ? "confirmation" : expired ? "expired" : "selecting";

  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div
        key={screen}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: reduceMotion ? 0 : 0.18, ease: EASE_APPLE }}
      >
        {booking ? (
          <BookingConfirmation
            booking={booking}
            movieTitle={movieTitle}
            movieBackdropUrl={movieBackdropUrl}
            onExpire={handleExpire}
            onCancel={handleCancel}
            isCancelling={isCancelling}
            onCheckout={handleCheckout}
            isCheckingOut={isCheckingOut}
            checkoutError={checkoutError}
          />
        ) : expired ? (
          <GlassCard className="mx-auto max-w-lg p-8 text-center">
            <h2 className="font-display text-xl font-semibold">Selection Expired</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              Your 5-minute hold has expired and the seats have been released to other users. Please go back and choose again.
            </p>
            <Button className="mt-6 h-11 w-full" onClick={handleReturnToSelection}>
              Choose Seats Again
            </Button>
          </GlassCard>
        ) : (
          <div className="pb-32">
            {/* Per-seat price belongs here now that the showtime-confirm page
                is bypassed (see showtime-list.tsx): it was the one figure that
                page showed which this header didn't, and without it the price
                stays invisible until the first seat is picked. */}
            <div className="mb-6 text-center">
              <p className="text-sm text-muted-foreground">{movieTitle}</p>
              <p className="mt-1 font-mono text-xs text-muted-foreground">
                {showDate} · {showTime} · {hallLabel} · RM {pricePerSeat.toFixed(2)}/seat
              </p>
            </div>

            <div className="mx-auto mb-4 max-w-lg space-y-2">
              <AnimatedFormBanner message={errorMessage} variant="destructive" />
              <AnimatedFormBanner message={notice} variant="destructive" />
            </div>

            <SeatMap
              hallName={hallLabel}
              totalColumns={seatData.totalColumns}
              seats={seatData.seats}
              selectedSeatIds={selectedSeatIds}
              onToggleSeat={toggleSeat}
            />

            <SelectionSummaryBar
              selectedSeats={selectedSeats}
              totalPrice={totalPrice}
              onConfirm={handleConfirm}
              disabled={status === "loading"}
              isSubmitting={isSubmitting}
            />
          </div>
        )}
      </motion.div>
    </AnimatePresence>
  );
}

function SelectionSummaryBar({
  selectedSeats,
  totalPrice,
  onConfirm,
  disabled,
  isSubmitting,
}: {
  selectedSeats: SeatStatusEntry[];
  totalPrice: number;
  onConfirm: () => void;
  disabled: boolean;
  isSubmitting: boolean;
}) {
  return (
    <div className="fixed inset-x-0 bottom-0 z-40 border-t border-glass-border bg-background/85 backdrop-blur-xl">
      {/* Same submit feedback the login/register forms get. Confirming seats
          runs the backend's two-layer concurrency check (DB pre-check, then
          the atomic Redis lock per seat — see CLAUDE.md Phase 5), so a 409
          losing the race is a normal, designed-for branch and the request is
          not always instant; a button label swapping to "Submitting…" was the
          weakest feedback in the app on its riskiest click. No `relative`
          needed on the parent: it is already `fixed`, which establishes the
          containing block this `absolute` bar positions against. */}
      <SubmitProgressBar active={isSubmitting} />
      <div className="mx-auto flex max-w-5xl flex-col gap-3 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          {selectedSeats.length === 0 ? (
            <p className="text-sm text-muted-foreground">Select your seats (multiple allowed)</p>
          ) : (
            <>
              <p className="truncate text-sm text-foreground">
                {selectedSeats.length} seat{selectedSeats.length === 1 ? "" : "s"} selected:{" "}
                <span className="font-mono text-muted-foreground">
                  {selectedSeats.map(seatLabel).join(", ")}
                </span>
              </p>
              <p className="mt-0.5 font-mono text-lg font-semibold text-primary">
                RM {totalPrice.toFixed(2)}
              </p>
            </>
          )}
        </div>
        <Button
          size="lg"
          className="h-11 shrink-0 px-8"
          disabled={selectedSeats.length === 0 || isSubmitting || disabled}
          onClick={onConfirm}
        >
          {isSubmitting ? "Submitting…" : "Confirm Seats"}
        </Button>
      </div>
    </div>
  );
}

function seatLabel(seat: SeatStatusEntry): string {
  if (seat.columnSpan > 1) {
    return `${seat.rowLabel}${seat.columnNumber}-${seat.rowLabel}${seat.columnNumber + seat.columnSpan - 1}`;
  }
  return `${seat.rowLabel}${seat.columnNumber}`;
}
