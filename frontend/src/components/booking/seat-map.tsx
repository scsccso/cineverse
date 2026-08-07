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
          squeezes seats below the 44px (h-11) touch-target minimum — the
          container scrolls instead of the seats shrinking. */}
      <div className="overflow-x-auto pb-2">
        <div className="mx-auto flex w-fit min-w-full flex-col items-center gap-6 px-2">
          <ScreenIndicator hallName={hallName} />

          <div className="flex flex-col gap-2.5">
            {rows.map(([rowLabel, rowSeats]) => (
              <div key={rowLabel} className="flex items-center gap-3">
                <span className="w-5 shrink-0 text-center font-mono text-xs text-muted-foreground">
                  {rowLabel}
                </span>
                <div
                  className="grid gap-2"
                  style={{
                    gridTemplateColumns: `repeat(${totalColumns}, minmax(2.75rem, 3rem))`,
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

/**
 * Purely decorative "you're standing in front of the screen" cue — an arc +
 * soft glow standing in for the old flat gradient bar (see
 * docs/design-proposal-customer-editorial.md, section 2.1). Doesn't read
 * any seat data and carries no state; the accessible label is still the
 * plain-text "银幕 · {hallName}" line below it, same as before. `aria-hidden`
 * on the SVG (and the all-caps "SCREEN" glyph next to it) keeps screen
 * readers from getting two competing announcements of the same fact.
 */
function ScreenIndicator({ hallName }: { hallName: string }) {
  return (
    <div className="w-full max-w-md">
      <svg viewBox="0 0 400 56" className="h-9 w-full" aria-hidden="true">
        <defs>
          <linearGradient id="screen-arc-glow" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="var(--primary)" stopOpacity="0" />
            <stop offset="50%" stopColor="var(--primary)" stopOpacity="0.9" />
            <stop offset="100%" stopColor="var(--primary)" stopOpacity="0" />
          </linearGradient>
        </defs>
        {/* Soft blurred duplicate sits behind the crisp line to read as a glow. */}
        <path
          d="M 10 34 Q 200 4 390 34"
          fill="none"
          stroke="url(#screen-arc-glow)"
          strokeWidth="10"
          strokeLinecap="round"
          className="blur-md"
          opacity="0.7"
        />
        <path
          d="M 10 34 Q 200 4 390 34"
          fill="none"
          stroke="url(#screen-arc-glow)"
          strokeWidth="1.5"
          strokeLinecap="round"
        />
      </svg>
      <p
        aria-hidden="true"
        className="-mt-1 text-center text-[10px] font-semibold tracking-[0.5em] text-muted-foreground/70"
      >
        SCREEN
      </p>
      <p className="mt-1 text-center text-xs tracking-[0.3em] text-muted-foreground">
        银幕 · {hallName}
      </p>
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
        "flex h-11 items-center justify-center gap-0.5 rounded-lg text-xs font-mono font-medium transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background",
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
        <Lock className="size-3.5" />
      ) : seat.status === "BOOKED" ? (
        <Check className="size-3.5" />
      ) : (
        <>
          {seat.seatType === "COUPLE" && <Heart className="size-3 fill-current" />}
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
