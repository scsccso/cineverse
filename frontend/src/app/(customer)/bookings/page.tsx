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
  const { status: authStatus, user, callAuthorized } = useAuth();
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

    if (user?.role === "ADMIN") {
      // ADMIN reverse-gate — see the matching check in profile/page.tsx and
      // CLAUDE.md "Admin reverse-isolation" for why this lives per-page.
      // listBookings() below is scoped server-side to the caller's own
      // rows anyway (see BookingRepository.findAllByUserIdNewestFirst), so
      // an ADMIN landing here isn't a data-leak risk the way /profile was —
      // this redirect is purely to keep them in the admin shell.
      router.replace("/admin/dashboard");
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
        if (!ignore) setLoadError("Failed to load your bookings — please try again later");
      });

    return () => {
      ignore = true;
    };
  }, [authStatus, user, callAuthorized, router]);

  return (
    <section className="mx-auto max-w-2xl px-6 py-16">
      <h1 className="font-display text-2xl font-semibold tracking-tight">My Bookings</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        Your booking history and e-tickets. Tap a confirmed booking to view its entry QR code.
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
        <p className="font-medium">You don&apos;t have any bookings yet</p>
        <p className="mt-1 text-sm text-muted-foreground">
          Pick a movie, choose your seats, and complete payment — your e-ticket will show up here.
        </p>
      </div>
      <Link href="/#now-playing" className={cn(buttonVariants(), "h-11 px-6")}>
        Browse Now Playing
      </Link>
    </GlassCard>
  );
}

function BookingRow({ booking }: { booking: BookingResponse }) {
  const seatLabels = booking.seats
    .map((seat) => `${seat.rowLabel}${seat.columnNumber}`)
    .join(", ");

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
            Seats <span className="font-mono text-foreground">{seatLabels}</span>
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
 * depends on colour alone (CLAUDE.md 1.5). "Redeemed" takes precedence over
 * "Confirmed" because a redeemed ticket can't be used again, which is the more
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
      label: "Redeemed",
      tone: "border-muted-foreground/30 bg-muted/20 text-muted-foreground",
    };
  }
  switch (status) {
    case "CONFIRMED":
      return { Icon: CheckCircle2, label: "Confirmed", tone: "border-primary/50 bg-primary/15 text-primary" };
    case "PENDING":
      return { Icon: Clock, label: "Pending Payment", tone: "border-primary/40 bg-primary/10 text-primary/90" };
    case "EXPIRED":
      return {
        Icon: CalendarX2,
        label: "Expired",
        tone: "border-muted-foreground/30 bg-muted/20 text-muted-foreground",
      };
    case "CANCELLED":
      return {
        Icon: XCircle,
        label: "Cancelled",
        tone: "border-muted-foreground/30 bg-muted/20 text-muted-foreground",
      };
  }
}
