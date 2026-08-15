"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import Image from "next/image";
import { resolveMediaUrl } from "@/lib/api/client";
import { GlassCard } from "@/components/glass/glass-card";
import { Button } from "@/components/ui/button";
import { AnimatedFormBanner } from "@/components/motion/animated-form-banner";
import { SubmitProgressBar } from "@/components/motion/submit-progress-bar";
import type { BookingResponse } from "@/lib/api/types";

interface BookingConfirmationProps {
  booking: BookingResponse;
  movieTitle: string;
  movieBackdropUrl: string;
  onExpire: () => void;
  onCancel: () => void;
  isCancelling: boolean;
  onCheckout: () => void;
  isCheckingOut: boolean;
  checkoutError: string | null;
}

/**
 * The post-submit "please pay within 5 minutes" screen. The countdown is
 * purely a client-side display — it never calls DELETE on its own when it
 * hits zero. The backend already lazily expires a PENDING booking on the
 * next read that touches it (see BookingService.loadWithLazyExpiry), and
 * that next read happens naturally when the user goes back to the seat
 * picker (which re-fetches /seats). Calling DELETE here instead would race
 * that lazy expiry and could 409 ("Booking is EXPIRED, cannot be
 * cancelled") if the backend flips it first.
 */
export function BookingConfirmation({
  booking,
  movieTitle,
  movieBackdropUrl,
  onExpire,
  onCancel,
  isCancelling,
  onCheckout,
  isCheckingOut,
  checkoutError,
}: BookingConfirmationProps) {
  const expiresAtMs = useMemo(() => new Date(booking.expiresAt).getTime(), [booking.expiresAt]);
  const [initialRemainingMs] = useState(() => Math.max(1, expiresAtMs - Date.now()));
  const [now, setNow] = useState(() => Date.now());

  const onExpireRef = useRef(onExpire);
  useEffect(() => {
    onExpireRef.current = onExpire;
  }, [onExpire]);

  useEffect(() => {
    const interval = setInterval(() => {
      const remaining = expiresAtMs - Date.now();
      setNow(Date.now());
      if (remaining <= 0) {
        clearInterval(interval);
        onExpireRef.current();
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [expiresAtMs]);

  const remainingMs = Math.max(0, expiresAtMs - now);
  const percentRemaining = Math.min(100, Math.max(0, (remainingMs / initialRemainingMs) * 100));

  /**
   * The hold deadline as a wall-clock time. Deliberately NOT formatted with
   * lib/format's cinema timezone the way showtimes are: a showtime is an event
   * at the cinema, but this is a deadline the customer checks against the clock
   * on their own phone — especially once they're on Stripe's page, where our
   * countdown is no longer on screen. So it follows the device's timezone.
   *
   * No hydration concern despite the client/server split: this component only
   * mounts after `booking` state exists, which is set by a user action or the
   * resume effect — never during the server render.
   */
  const deadlineLabel = useMemo(
    () =>
      new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit", hour12: false })
        .format(new Date(expiresAtMs)),
    [expiresAtMs],
  );

  return (
    <div className="relative flex min-h-[calc(100vh-4rem)] items-center justify-center overflow-hidden">
      {/* Same backdrop treatment as the showtime-confirm page (design-
          proposal-customer-editorial.md, finding 5-a) — contained to this
          component's own column width (the max-w-5xl wrapper in
          seats/page.tsx, shared with the seat grid state) rather than
          full-bleed to the viewport edge: breaking out of that wrapper's
          padding would need negative-margin values kept in lockstep with
          that page's own px-4/sm:px-6, which is one hop of indirection
          more than this decorative treatment is worth. Purely decorative;
          doesn't touch the countdown/booking logic. */}
      <Image
        src={resolveMediaUrl(movieBackdropUrl)}
        alt=""
        fill
        sizes="100vw"
        className="object-cover"
      />
      <div className="absolute inset-0 bg-gradient-to-t from-background via-background/85 to-background/45" />

      <GlassCard className="relative z-10 max-w-lg p-8">
        {/* "Pay Now" fires a real network call (POST /bookings/{id}/checkout
            creates the Stripe session) before the redirect, so there is a
            latency window where only the button label had changed. Sits
            inside GlassCard's own inner `relative` wrapper, which insets it
            by the card padding — the same treatment the login/register bars
            get inside their Card, so the two read as one pattern. */}
        <SubmitProgressBar active={isCheckingOut} />
        <p className="text-sm text-muted-foreground">Seats reserved — complete payment before time runs out</p>
        <h2 className="mt-1 font-display text-2xl font-semibold tracking-tight">{movieTitle}</h2>

        <div className="mt-6">
          <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
            <div
              className="h-full rounded-full bg-primary transition-[width] duration-1000 ease-linear"
              style={{ width: `${percentRemaining}%` }}
            />
          </div>
          <p className="mt-3 text-center font-mono text-4xl font-semibold tabular-nums text-primary">
            {formatCountdown(remainingMs)}
          </p>
          <p className="text-center text-xs text-muted-foreground">
            Complete payment by <span className="font-mono text-foreground">{deadlineLabel}</span>, or your seats will be released automatically
          </p>
        </div>

        <dl className="mt-6 space-y-2 border-t border-glass-border/60 pt-4 text-sm">
          {booking.seats.map((seat) => (
            <div key={seat.seatId} className="flex items-center justify-between">
              <dt className="text-muted-foreground">
                Seat {seat.rowLabel}
                {seat.columnNumber} · {seat.seatType === "COUPLE" ? "Couple Seat" : "Standard Seat"}
              </dt>
              <dd className="font-mono">RM {seat.priceAtBooking.toFixed(2)}</dd>
            </div>
          ))}
          <div className="flex items-center justify-between border-t border-glass-border/60 pt-2 font-medium">
            <dt>Total</dt>
            <dd className="font-mono text-primary">RM {booking.totalPrice.toFixed(2)}</dd>
          </div>
        </dl>

        <div className="mt-4">
          <AnimatedFormBanner message={checkoutError} variant="destructive" />
        </div>

        {/* The one thing a first-timer can't discover on their own: the hold
            keeps counting down on Stripe's page, where this timer isn't
            visible. Stripe's own session minimum is 30 minutes while the seat
            hold is 5 (CLAUDE.md Phase 6), so someone typing card details
            slowly can pay for a seat that has already been released — the
            ORPHANED_SUCCESS case. Stating the absolute time here means they
            leave with something they can check against their phone clock. */}
        <p className="mt-6 text-center text-xs text-muted-foreground">
          The payment page is hosted by Stripe — the countdown keeps running while you&apos;re there. Complete payment by{" "}
          <span className="font-mono text-foreground">{deadlineLabel}</span>
        </p>
        <Button
          className="mt-2 h-11 w-full"
          disabled={isCheckingOut || isCancelling}
          onClick={onCheckout}
        >
          {isCheckingOut ? "Redirecting to payment…" : "Pay Now"}
        </Button>
        <Button
          variant="outline"
          className="mt-3 h-11 w-full"
          disabled={isCancelling || isCheckingOut}
          onClick={onCancel}
        >
          {isCancelling ? "Cancelling…" : "Cancel Selection"}
        </Button>
      </GlassCard>
    </div>
  );
}

function formatCountdown(ms: number): string {
  const totalSeconds = Math.ceil(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}
