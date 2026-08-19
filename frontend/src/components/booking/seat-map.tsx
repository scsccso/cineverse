"use client";

import { memo } from "react";
import { Check, Lock, User, type LucideIcon } from "lucide-react";
import { motion, useReducedMotion } from "framer-motion";
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
  // Computed once here rather than inside SeatButton — a hall can have 100+
  // seat buttons, and useReducedMotion() would otherwise mean that many
  // redundant matchMedia subscriptions for a value that's identical across
  // all of them (same density-driven reasoning as why seat buttons don't
  // each instantiate a full GlassCard — see CLAUDE.md 1.5).
  const reduceMotion = useReducedMotion();

  return (
    <div className="space-y-6">
      {/* Legend first: it's the key to the grid below, and a first-time user
          reads top-down — printed underneath, it only helps someone who
          already knew to go looking for it. Kept outside the scroll container
          so it never scrolls out of view sideways on a wide hall; the screen
          arc still sits directly above the seats, so the spatial cue is
          unaffected. */}
      <SeatLegend />

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
                      reduceMotion={reduceMotion}
                    />
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * Purely decorative "you're standing in front of the screen" cue — an arc +
 * soft glow standing in for the old flat gradient bar (see
 * docs/design-proposal-customer-editorial.md, section 2.1). Doesn't read
 * any seat data and carries no state; the accessible label is still the
 * plain-text "Screen · {hallName}" line below it, same as before. `aria-hidden`
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
        Screen · {hallName}
      </p>
    </div>
  );
}

/**
 * Wrapped in memo with a value-based comparator (not the default reference
 * check) because seat-picker.tsx's 4s poll (POLL_INTERVAL_MS) replaces the
 * entire `seats` array with a fresh fetch every tick, so `seat` object
 * identity churns on a timer regardless of whether anything about this seat
 * actually changed — a reference comparator would never bail out. Comparing
 * the fields that actually drive this button's appearance instead means
 * toggling one seat mid-poll-interval only re-renders that seat, not the
 * other ~150. `onToggle` is deliberately left out of the comparison even
 * though SeatMap creates a fresh closure for it on every render (line ~71)
 * — it only ever closes over this seat (stable per the fields already
 * compared) and seat-picker.tsx's toggleSeat, whose body only calls stable
 * setState functions, so an old closure behaves identically to a new one.
 */
const SeatButton = memo(function SeatButton({
  seat,
  selected,
  onToggle,
  reduceMotion,
}: {
  seat: SeatStatusEntry;
  selected: boolean;
  onToggle: () => void;
  reduceMotion: boolean | null;
}) {
  const disabled = seat.status !== "AVAILABLE";
  const isCouple = seat.seatType === "COUPLE";
  // Locked/Booked keep their own distinct pictogram rather than a
  // color-shifted person icon — CLAUDE.md 1.5 requires shape, not just
  // color, to separate states for colour-blind readers.
  const Icon = seat.status === "LOCKED" ? Lock : seat.status === "BOOKED" ? Check : User;
  const filled = seat.status === "AVAILABLE" && selected;

  return (
    <motion.button
      type="button"
      disabled={disabled}
      whileTap={disabled || reduceMotion ? undefined : { scale: 0.9 }}
      onClick={onToggle}
      aria-pressed={selected}
      aria-label={ariaLabel(seat, selected)}
      style={{ gridColumn: `${seat.columnNumber} / span ${seat.columnSpan}` }}
      className={cn(
        "flex h-11 items-center justify-center gap-1 rounded-lg transition-colors duration-150 motion-reduce:transition-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background",
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
      {/* Row/seat number lives in aria-label (below) and in the always-visible
          row-label column + SelectionSummaryBar text once chosen — not printed
          here, so nothing needs a second, hover-only place to read it. */}
      <Icon className={cn("size-3.5 shrink-0", filled && "fill-current")} />
      {isCouple && (
        <>
          {/* The "bridge" — inherits whatever text-color class is active
              above via currentColor, so it re-colors with the seat's state
              for free instead of needing its own conditional. */}
          <span aria-hidden className="h-0.5 w-2 shrink-0 rounded-full bg-current" />
          <Icon className={cn("size-3.5 shrink-0", filled && "fill-current")} />
        </>
      )}
    </motion.button>
  );
},
(prev, next) =>
  prev.seat.seatId === next.seat.seatId &&
  prev.seat.status === next.seat.status &&
  prev.selected === next.selected &&
  prev.reduceMotion === next.reduceMotion,
);

/**
 * The swatches mirror what the grid actually renders (CLAUDE.md 1.5's
 * "legend must depict the real seat" rule — previously violated for
 * Temporarily Locked vs. Booked, since fixed here for Available/Selected
 * too now that those show a filled-vs-outline person icon instead of a bare
 * seat number, see SeatButton). Couple Seat gets its own two-swatch row
 * instead of an `items` entry — its shape (two glyphs + a bridge) isn't a
 * single Icon.
 */
function SeatLegend() {
  const items: { label: string; swatch: string; Icon: LucideIcon; iconClass: string; filled?: boolean }[] = [
    {
      label: "Available",
      swatch: "border border-glass-border bg-glass-surface",
      Icon: User,
      iconClass: "text-foreground/80",
    },
    {
      label: "Selected",
      swatch: "border border-primary bg-primary/15",
      Icon: User,
      iconClass: "text-primary",
      filled: true,
    },
    {
      label: "Temporarily Locked",
      swatch: "border border-dashed border-muted-foreground/30 bg-muted/20",
      Icon: Lock,
      iconClass: "text-muted-foreground/70",
    },
    {
      label: "Booked",
      swatch: "border border-transparent bg-muted-foreground/25",
      Icon: Check,
      iconClass: "text-background/70",
    },
  ];

  return (
    <div className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-xs text-muted-foreground">
      {items.map((item) => (
        <div key={item.label} className="flex items-center gap-2">
          <span
            className={cn("flex size-5 items-center justify-center rounded", item.swatch)}
            aria-hidden
          >
            <item.Icon className={cn("size-3", item.iconClass, item.filled && "fill-current")} />
          </span>
          {item.label}
        </div>
      ))}
      <div className="flex items-center gap-2">
        <span
          className="flex h-5 w-8 items-center justify-center gap-0.5 rounded border border-glass-border bg-glass-surface text-foreground/80"
          aria-hidden
        >
          <User className="size-2.5 shrink-0" />
          <span className="h-0.5 w-1 shrink-0 rounded-full bg-current" />
          <User className="size-2.5 shrink-0" />
        </span>
        Couple Seat
      </div>
    </div>
  );
}

function ariaLabel(seat: SeatStatusEntry, selected: boolean): string {
  const typeLabel = seat.seatType === "COUPLE" ? "Couple Seat" : "Standard Seat";
  const statusLabel =
    seat.status === "BOOKED"
      ? "Booked"
      : seat.status === "LOCKED"
        ? "Temporarily locked, not selectable"
        : selected
          ? "Selected"
          : "Available";
  return `Row ${seat.rowLabel}, Seat ${seat.columnNumber}, ${typeLabel}, ${statusLabel}`;
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
