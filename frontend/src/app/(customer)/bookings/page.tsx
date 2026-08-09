"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { CalendarX2, CheckCircle2, Clock, Ticket, XCircle } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { listBookings } from "@/lib/api/bookings";
import type { BookingResponse, BookingStatus } from "@/lib/api/types";
import { GlassCard } from "@/components/glass/glass-card";
import { GlassSkeleton } from "@/components/glass/glass-skeleton";
import { FadeIn } from "@/components/motion/fade-in";
import { buttonVariants } from "@/components/ui/button";
import { formatShowDate, formatShowTime } from "@/lib/format";
import { cn } from "@/lib/utils";

/**
 * Order history — the only way back to a confirmed e-ticket once the
 * post-payment redirect URL is gone. Every row links to the existing
 * /bookings/{id}/confirmed page, which already renders the QR code and
 * handles the confirmed / redeemed / not-confirmed states, so nothing about
 * the ticket itself is duplicated here.
 */
export default function BookingsPage() {
  const { status: authStatus, callAuthorized } = useAuth();
  const router = useRouter();
  const [bookings, setBookings] = useState<BookingResponse[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (authStatus === "loading") return;

    if (authStatus === "unauthenticated") {
      // Belt-and-suspenders behind proxy.ts's coarse cookie gate, same split
      // /profile uses: the definitive check is here, after the silent refresh
      // has actually resolved.
      router.replace("/login?from=/bookings");
      return;
    }

    let ignore = false;
    callAuthorized((token) => listBookings(token))
      .then((result) => {
        if (ignore) return;
        setBookings(result);
        setLoadError(null);
      })
      .catch(() => {
        if (!ignore) setLoadError("加载订单失败,请稍后重试");
      });

    return () => {
      ignore = true;
    };
  }, [authStatus, callAuthorized, router]);

  return (
    <section className="mx-auto max-w-2xl px-6 py-16">
      <h1 className="font-display text-2xl font-semibold tracking-tight">我的订单</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        购票记录与电子票。已确认的订单点进去就是入场二维码。
      </p>

      <div className="mt-8">
        {loadError ? (
          <p className="text-center text-sm text-muted-foreground">{loadError}</p>
        ) : bookings === null ? (
          <div className="flex flex-col gap-4">
            {Array.from({ length: 3 }).map((_, i) => (
              <GlassSkeleton key={i} className="h-32 w-full" />
            ))}
          </div>
        ) : bookings.length === 0 ? (
          <EmptyState />
        ) : (
          <FadeIn className="flex flex-col gap-4">
            {bookings.map((booking) => (
              <BookingRow key={booking.id} booking={booking} />
            ))}
          </FadeIn>
        )}
      </div>
    </section>
  );
}

function EmptyState() {
  return (
    <GlassCard className="flex flex-col items-center gap-4 p-10 text-center">
      <Ticket className="size-10 text-muted-foreground" aria-hidden />
      <div>
        <p className="font-medium">你还没有任何订单</p>
        <p className="mt-1 text-sm text-muted-foreground">
          选一场电影,选好座位并完成支付后,电子票会出现在这里。
        </p>
      </div>
      <Link href="/#now-playing" className={cn(buttonVariants(), "h-11 px-6")}>
        去看看正在热映
      </Link>
    </GlassCard>
  );
}

function BookingRow({ booking }: { booking: BookingResponse }) {
  const seatLabels = booking.seats
    .map((seat) => `${seat.rowLabel}${seat.columnNumber}`)
    .join("、");

  return (
    <Link href={`/bookings/${booking.id}/confirmed`} className="block">
      <GlassCard interactive className="p-5">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <h2 className="truncate font-display text-lg font-semibold tracking-tight">
              {booking.showtime.movieTitle}
            </h2>
            <p className="mt-1 font-mono text-xs text-muted-foreground">
              {formatShowDate(booking.showtime.startTime)} ·{" "}
              {formatShowTime(booking.showtime.startTime)} · {booking.showtime.hallName}
            </p>
          </div>
          <StatusTag status={booking.status} redeemed={Boolean(booking.redeemedAt)} />
        </div>

        <div className="mt-4 flex items-end justify-between gap-4 border-t border-glass-border/60 pt-3">
          <p className="min-w-0 text-sm text-muted-foreground">
            座位 <span className="font-mono text-foreground">{seatLabels}</span>
          </p>
          <p className="shrink-0 font-mono text-base font-semibold text-primary">
            RM {booking.totalPrice.toFixed(2)}
          </p>
        </div>
      </GlassCard>
    </Link>
  );
}

/**
 * Status is encoded three ways — icon shape, wording, and colour — so it never
 * depends on colour alone (CLAUDE.md 1.5). "已入场" takes precedence over
 * "已确认" because a redeemed ticket can't be used again, which is the more
 * useful thing to know at a glance.
 */
function StatusTag({ status, redeemed }: { status: BookingStatus; redeemed: boolean }) {
  const { Icon, label, tone } = statusPresentation(status, redeemed);
  return (
    <span
      className={cn(
        "flex shrink-0 items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium",
        tone,
      )}
    >
      <Icon className="size-3.5" aria-hidden />
      {label}
    </span>
  );
}

function statusPresentation(status: BookingStatus, redeemed: boolean) {
  if (status === "CONFIRMED" && redeemed) {
    return {
      Icon: CheckCircle2,
      label: "已入场",
      tone: "border-muted-foreground/30 bg-muted/20 text-muted-foreground",
    };
  }
  switch (status) {
    case "CONFIRMED":
      return { Icon: CheckCircle2, label: "已确认", tone: "border-primary/50 bg-primary/15 text-primary" };
    case "PENDING":
      return { Icon: Clock, label: "待支付", tone: "border-primary/40 bg-primary/10 text-primary/90" };
    case "EXPIRED":
      return {
        Icon: CalendarX2,
        label: "已过期",
        tone: "border-muted-foreground/30 bg-muted/20 text-muted-foreground",
      };
    case "CANCELLED":
      return {
        Icon: XCircle,
        label: "已取消",
        tone: "border-muted-foreground/30 bg-muted/20 text-muted-foreground",
      };
  }
}
