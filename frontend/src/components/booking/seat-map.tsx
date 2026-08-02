"use client";

import { Check, Heart, Lock } from "lucide-react";
import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import type { SeatStatusEntry } from "@/lib/api/types";

interface SeatMapProps {
  hallName: string;
  totalColumns: number;
  seats: SeatStatusEntry[];
  selectedSeatIds: Set<string>;
  onToggleSeat: (seat: SeatStatusEntry) => void;
}

/**
 * Renders a full hall's seat grid grouped by row. Each row shares the same
 * `totalColumns` grid-template so seats line up vertically across rows even
 * though COUPLE seats span two columns — the browser's grid layout handles
 * that instead of hand-rolled column-width math.
 */
export function SeatMap({
  hallName,
  totalColumns,
  seats,
  selectedSeatIds,
  onToggleSeat,
}: SeatMapProps) {
  const rows = groupByRow(seats);

  return (
    <div className="space-y-6">
      {/* Horizontally scrollable so a hall wider than the viewport never
          squeezes seats — they stay tap-sized, the container scrolls instead. */}
      <div className="overflow-x-auto pb-2">
        <div className="mx-auto flex w-fit min-w-full flex-col items-center gap-6 px-2">
          <div className="w-full max-w-md">
            <div className="h-1.5 w-full rounded-full bg-gradient-to-r from-transparent via-primary/50 to-transparent" />
            <p className="mt-2 text-center text-xs tracking-[0.3em] text-muted-foreground">
              银幕 · {hallName}
            </p>
          </div>

          <div className="flex flex-col gap-2">
            {rows.map(([rowLabel, rowSeats]) => (
              <div key={rowLabel} className="flex items-center gap-3">
                <span className="w-5 shrink-0 text-center font-mono text-xs text-muted-foreground">
                  {rowLabel}
                </span>
                <div
                  className="grid gap-2"
                  style={{
                    gridTemplateColumns: `repeat(${totalColumns}, minmax(1.9rem, 2.25rem))`,
                  }}
                >
                  {rowSeats.map((seat) => (
                    <SeatButton
                      key={seat.seatId}
                      seat={seat}
                      selected={selectedSeatIds.has(seat.seatId)}
                      onToggle={() => onToggleSeat(seat)}
                    />
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <SeatLegend />
    </div>
  );
}

function SeatButton({
  seat,
  selected,
  onToggle,
}: {
  seat: SeatStatusEntry;
  selected: boolean;
  onToggle: () => void;
}) {
  const disabled = seat.status !== "AVAILABLE";

  return (
    <motion.button
      type="button"
      disabled={disabled}
      whileTap={disabled ? undefined : { scale: 0.9 }}
      onClick={onToggle}
      aria-pressed={selected}
      aria-label={ariaLabel(seat, selected)}
      style={{ gridColumn: `${seat.columnNumber} / span ${seat.columnSpan}` }}
      className={cn(
        "flex h-8 items-center justify-center gap-0.5 rounded-lg text-[0.65rem] font-mono font-medium transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background",
        !disabled &&
          !selected &&
          "cursor-pointer border border-glass-border bg-glass-surface text-foreground/80 backdrop-blur-glass hover:border-primary/50 hover:text-foreground",
        selected &&
          "cursor-pointer border border-primary bg-primary/15 text-primary",
        seat.status === "LOCKED" &&
          "cursor-not-allowed border border-dashed border-muted-foreground/30 bg-muted/20 text-muted-foreground/50",
        seat.status === "BOOKED" &&
          "cursor-not-allowed border border-transparent bg-muted-foreground/25 text-background/70",
      )}
    >
      {seat.status === "LOCKED" ? (
        <Lock className="size-3" />
      ) : seat.status === "BOOKED" ? (
        <Check className="size-3" />
      ) : (
        <>
          {seat.seatType === "COUPLE" && <Heart className="size-2.5 fill-current" />}
          {seat.columnNumber}
        </>
      )}
    </motion.button>
  );
}

function SeatLegend() {
  const items = [
    { label: "可选", swatch: "border border-glass-border bg-glass-surface" },
    { label: "已选", swatch: "border border-primary bg-primary/15" },
    {
      label: "使用中(暂时锁定)",
      swatch: "border border-dashed border-muted-foreground/30 bg-muted/20",
    },
    { label: "已售出", swatch: "border border-transparent bg-muted-foreground/25" },
  ];

  return (
    <div className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-xs text-muted-foreground">
      {items.map((item) => (
        <div key={item.label} className="flex items-center gap-2">
          <span className={cn("size-4 rounded", item.swatch)} aria-hidden />
          {item.label}
        </div>
      ))}
      <div className="flex items-center gap-1.5">
        <Heart className="size-3 fill-current text-muted-foreground" aria-hidden />
        情侣座
      </div>
    </div>
  );
}

function ariaLabel(seat: SeatStatusEntry, selected: boolean): string {
  const typeLabel = seat.seatType === "COUPLE" ? "情侣座" : "标准座";
  const statusLabel =
    seat.status === "BOOKED"
      ? "已售出"
      : seat.status === "LOCKED"
        ? "暂时锁定,不可选"
        : selected
          ? "已选中"
          : "可选";
  return `${seat.rowLabel}排${seat.columnNumber}号 ${typeLabel} ${statusLabel}`;
}

/** Preserves the backend's row-then-column ordering — no re-sort needed. */
function groupByRow(seats: SeatStatusEntry[]): [string, SeatStatusEntry[]][] {
  const map = new Map<string, SeatStatusEntry[]>();
  for (const seat of seats) {
    const bucket = map.get(seat.rowLabel);
    if (bucket) {
      bucket.push(seat);
    } else {
      map.set(seat.rowLabel, [seat]);
    }
  }
  return [...map.entries()];
}
