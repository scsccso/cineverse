"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-context";
import { ApiError } from "@/lib/api/client";
import { getShowtimeSeats } from "@/lib/api/showtimes";
import { cancelBooking, createBooking } from "@/lib/api/bookings";
import type { BookingResponse, SeatStatusEntry, ShowtimeSeatsResponse } from "@/lib/api/types";
import { SeatMap } from "@/components/booking/seat-map";
import { BookingConfirmation } from "@/components/booking/booking-confirmation";
import { GlassCard } from "@/components/glass/glass-card";
import { Button } from "@/components/ui/button";
import { AnimatedFormBanner } from "@/components/motion/animated-form-banner";

const POLL_INTERVAL_MS = 4000;
/** How long a just-cleared "someone else took your seat" notice stays on screen. */
const NOTICE_TIMEOUT_MS = 6000;

interface SeatPickerProps {
  showtimeId: string;
  movieTitle: string;
  hallLabel: string;
  showDate: string;
  showTime: string;
  pricePerSeat: number;
  initialSeatData: ShowtimeSeatsResponse;
}

export function SeatPicker({
  showtimeId,
  movieTitle,
  hallLabel,
  showDate,
  showTime,
  pricePerSeat,
  initialSeatData,
}: SeatPickerProps) {
  const { status, callAuthorized } = useAuth();
  const router = useRouter();

  const [seatData, setSeatData] = useState(initialSeatData);
  const [selectedSeatIds, setSelectedSeatIds] = useState<Set<string>>(new Set());
  const [booking, setBooking] = useState<BookingResponse | null>(null);
  const [expired, setExpired] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);

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
        setNotice(`座位 ${removedLabels.join("、")} 刚被其他用户选走,已自动为你取消选中`);
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
      setErrorMessage("请先登录后再确认选座,即将跳转到登录页…");
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
        setErrorMessage("提交失败:部分座位刚被其他用户抢先锁定,座位图已刷新,请重新选择");
        await refreshSeats();
      } else {
        setErrorMessage("提交失败,请稍后重试");
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

  if (booking) {
    return (
      <BookingConfirmation
        booking={booking}
        movieTitle={movieTitle}
        onExpire={handleExpire}
        onCancel={handleCancel}
        isCancelling={isCancelling}
      />
    );
  }

  if (expired) {
    return (
      <GlassCard className="mx-auto max-w-lg p-8 text-center">
        <h2 className="font-display text-xl font-semibold">选座已过期</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          5 分钟持有时间已到,座位已释放给其他用户。请返回重新选座。
        </p>
        <Button className="mt-6 h-11 w-full" onClick={handleReturnToSelection}>
          返回重新选座
        </Button>
      </GlassCard>
    );
  }

  return (
    <div className="pb-32">
      <div className="mb-6 text-center">
        <p className="text-sm text-muted-foreground">{movieTitle}</p>
        <p className="mt-1 font-mono text-xs text-muted-foreground">
          {showDate} · {showTime} · {hallLabel}
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
      <div className="mx-auto flex max-w-5xl flex-col gap-3 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          {selectedSeats.length === 0 ? (
            <p className="text-sm text-muted-foreground">请选择座位(可多选)</p>
          ) : (
            <>
              <p className="truncate text-sm text-foreground">
                已选 {selectedSeats.length} 个座位:{" "}
                <span className="font-mono text-muted-foreground">
                  {selectedSeats.map(seatLabel).join("、")}
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
          {isSubmitting ? "提交中…" : "确认选座"}
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
