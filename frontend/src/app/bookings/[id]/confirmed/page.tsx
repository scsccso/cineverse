"use client";

import { useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { motion } from "framer-motion";
import { QRCodeSVG } from "qrcode.react";
import { useAuth } from "@/lib/auth/auth-context";
import { getBooking } from "@/lib/api/bookings";
import type { BookingResponse } from "@/lib/api/types";
import { GlassCard } from "@/components/glass/glass-card";
import { Button } from "@/components/ui/button";
import { formatShowDate, formatShowTime } from "@/lib/format";

const EASE_APPLE = [0.22, 1, 0.36, 1] as const;
const POLL_INTERVAL_MS = 1500;
/** ~15s of polling — Stripe's webhook normally arrives within a second or two of the redirect; see the "timeout" phase for what happens beyond that. */
const MAX_ATTEMPTS = 10;

type Phase = "waiting" | "confirmed" | "not-confirmed" | "timeout";

/**
 * Landed on from Stripe's success_url. The redirect itself proves nothing —
 * Stripe's webhook (server-to-server, see PaymentService) is the only thing
 * that actually flips the booking to CONFIRMED, and it can arrive slightly
 * after this page loads. So this polls GET /bookings/{id} for a few seconds
 * rather than trusting the redirect alone — same "poll, don't push"
 * philosophy as the seat picker (see CLAUDE.md Phase 5).
 */
export default function BookingConfirmedPage() {
  const { id } = useParams<{ id: string }>();
  const { status, callAuthorized } = useAuth();
  const [booking, setBooking] = useState<BookingResponse | null>(null);
  const [phase, setPhase] = useState<Phase>("waiting");
  const attemptsRef = useRef(0);

  useEffect(() => {
    if (status !== "authenticated") return;
    let cancelled = false;

    async function poll() {
      try {
        const result = await callAuthorized((token) => getBooking(token, id));
        if (cancelled) return;
        setBooking(result);
        if (result.status === "CONFIRMED") {
          setPhase("confirmed");
          return;
        }
        if (result.status === "EXPIRED" || result.status === "CANCELLED") {
          setPhase("not-confirmed");
          return;
        }
      } catch {
        // Transient network hiccup — retried below like any other attempt.
      }
      attemptsRef.current += 1;
      if (attemptsRef.current >= MAX_ATTEMPTS) {
        setPhase("timeout");
        return;
      }
      if (!cancelled) {
        setTimeout(poll, POLL_INTERVAL_MS);
      }
    }

    poll();
    return () => {
      cancelled = true;
    };
  }, [status, id, callAuthorized]);

  if (status === "loading" || phase === "waiting") {
    return (
      <section className="mx-auto flex min-h-[60vh] max-w-lg flex-col items-center justify-center px-6 text-center">
        <p className="text-sm text-muted-foreground">正在确认支付结果…</p>
      </section>
    );
  }

  if (status === "unauthenticated") {
    return (
      <section className="mx-auto max-w-lg px-6 py-16 text-center text-muted-foreground">
        请登录后查看订单状态。
      </section>
    );
  }

  if (phase === "timeout") {
    return (
      <section className="mx-auto max-w-lg px-6 py-16">
        <GlassCard className="p-8 text-center">
          <h1 className="font-display text-xl font-semibold">支付结果确认中</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            支付可能仍在处理,请稍后刷新此页面查看最新状态。
          </p>
          <Button className="mt-6 h-11 w-full" onClick={() => window.location.reload()}>
            刷新
          </Button>
        </GlassCard>
      </section>
    );
  }

  if (phase === "not-confirmed" && booking) {
    return (
      <section className="mx-auto max-w-lg px-6 py-16">
        <GlassCard className="p-8 text-center">
          <h1 className="font-display text-xl font-semibold">支付未完成</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            这笔订单没能在有效时间内完成支付,座位已释放。
          </p>
          <Button
            className="mt-6 h-11 w-full"
            render={<Link href={`/showtimes/${booking.showtime.id}/seats`}>返回重新选座</Link>}
          />
        </GlassCard>
      </section>
    );
  }

  if (!booking) {
    return null;
  }

  return (
    <section className="mx-auto max-w-lg px-6 py-16">
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: EASE_APPLE }}
      >
        <GlassCard className="p-8">
          <p className="text-sm text-primary">支付成功,订单已确认</p>
          <h1 className="mt-1 font-display text-2xl font-semibold tracking-tight">
            {booking.showtime.movieTitle}
          </h1>
          <p className="mt-1 font-mono text-xs text-muted-foreground">
            {formatShowDate(booking.showtime.startTime)} · {formatShowTime(booking.showtime.startTime)} ·{" "}
            {booking.showtime.hallName}
          </p>

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

          {booking.ticketCode && (
            <div className="mt-6 flex flex-col items-center border-t border-glass-border/60 pt-6">
              {booking.redeemedAt ? (
                <p className="mb-3 rounded-full bg-primary/15 px-3 py-1 text-xs font-medium text-primary">
                  已入场 · {new Date(booking.redeemedAt).toLocaleString("zh-CN")}
                </p>
              ) : (
                <p className="mb-3 text-xs text-muted-foreground">入场时向工作人员出示此电子票</p>
              )}
              {/* White backing regardless of theme — QR scanners need strong
                  contrast, which the dark Liquid Glass surface can't provide
                  on its own. Dimmed (not hidden) once redeemed: still useful
                  as a receipt, just visually de-emphasized. */}
              <div className={`rounded-2xl bg-white p-4 ${booking.redeemedAt ? "opacity-50" : ""}`}>
                <QRCodeSVG value={booking.ticketCode} size={180} marginSize={2} />
              </div>
            </div>
          )}

          <Button className="mt-6 h-11 w-full" render={<Link href="/profile">查看我的账号</Link>} />
        </GlassCard>
      </motion.div>
    </section>
  );
}
