"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { GlassCard } from "@/components/glass/glass-card";
import { Button } from "@/components/ui/button";
import { AnimatedFormBanner } from "@/components/motion/animated-form-banner";
import type { BookingResponse } from "@/lib/api/types";

interface BookingConfirmationProps {
  booking: BookingResponse;
  movieTitle: string;
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

  return (
    <GlassCard className="mx-auto max-w-lg p-8">
      <p className="text-sm text-muted-foreground">选座成功,请在时间内完成支付</p>
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
          请在 5 分钟内完成支付,否则座位将自动释放
        </p>
      </div>

      <dl className="mt-6 space-y-2 border-t border-glass-border/60 pt-4 text-sm">
        {booking.seats.map((seat) => (
          <div key={seat.seatId} className="flex items-center justify-between">
            <dt className="text-muted-foreground">
              {seat.rowLabel}
              {seat.columnNumber} 号座 · {seat.seatType === "COUPLE" ? "情侣座" : "标准座"}
            </dt>
            <dd className="font-mono">RM {seat.priceAtBooking.toFixed(2)}</dd>
          </div>
        ))}
        <div className="flex items-center justify-between border-t border-glass-border/60 pt-2 font-medium">
          <dt>总计</dt>
          <dd className="font-mono text-primary">RM {booking.totalPrice.toFixed(2)}</dd>
        </div>
      </dl>

      <div className="mt-4">
        <AnimatedFormBanner message={checkoutError} variant="destructive" />
      </div>

      <Button
        className="mt-6 h-11 w-full"
        disabled={isCheckingOut || isCancelling}
        onClick={onCheckout}
      >
        {isCheckingOut ? "正在跳转到支付页面…" : "去支付"}
      </Button>
      <Button
        variant="outline"
        className="mt-3 h-11 w-full"
        disabled={isCancelling || isCheckingOut}
        onClick={onCancel}
      >
        {isCancelling ? "取消中…" : "取消选座"}
      </Button>
    </GlassCard>
  );
}

function formatCountdown(ms: number): string {
  const totalSeconds = Math.ceil(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}
