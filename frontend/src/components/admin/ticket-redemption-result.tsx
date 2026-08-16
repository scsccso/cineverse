import { AlertTriangle, CircleCheck, CircleX } from "lucide-react";
import type { TicketRedemptionResponse } from "@/lib/api/types";
import { formatShowDate, formatShowTime } from "@/lib/format";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

export type TicketRedemptionOutcome =
  | { kind: "success"; result: TicketRedemptionResponse }
  | { kind: "invalid"; message: string }
  | { kind: "conflict"; message: string }
  | { kind: "network"; message: string };

const SEAT_TYPE_LABEL: Record<string, string> = {
  STANDARD: "Standard",
  COUPLE: "Couple",
};

/**
 * Three visually and textually distinct outcomes, not one blanket "redemption
 * failed" message. "Invalid code" (400 — the scanned/typed string itself
 * doesn't verify) and "cannot redeem" (409 — a genuine ticket, but the
 * backend won't let it in right now) are different situations for the staff
 * member to act on, so each gets its own icon/tone/heading — matching the
 * existing amber "warning" treatment (StatTile, ScheduleShowtimesNudge) for
 * the 409 case rather than reusing the same red as a hard 400 error.
 *
 * The 409 body text is the backend's own message shown as-is. It covers two
 * distinct backend exceptions (TicketAlreadyRedeemedException /
 * BookingNotConfirmedException) that differ only in message text, not status
 * code or an error code field — this project's established principle is not
 * to parse structured data out of error strings (see the showtime-conflict
 * precedent in CLAUDE.md), so this doesn't try to string-match its way to a
 * fourth, more specific heading. Both messages already read as complete,
 * specific sentences on their own.
 */
export function TicketRedemptionResult({ outcome }: { outcome: TicketRedemptionOutcome }) {
  if (outcome.kind === "success") {
    const { result } = outcome;
    return (
      <Card className="shadow-sm ring-primary/30 bg-primary/5">
        <CardContent className="flex items-start gap-3">
          <CircleCheck className="mt-0.5 size-5 shrink-0 text-primary" aria-hidden />
          <div className="flex-1 space-y-3">
            <div>
              <p className="font-medium text-foreground">Ticket Redeemed</p>
              <p className="mt-1 text-sm text-muted-foreground">
                Confirm this is the right showing before letting the holder in.
              </p>
            </div>
            <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-sm">
              <dt className="text-muted-foreground">Movie</dt>
              <dd className="text-foreground">{result.movieTitle}</dd>
              <dt className="text-muted-foreground">Showtime</dt>
              <dd className="text-foreground">
                {formatShowDate(result.showtimeStartTime)} · {formatShowTime(result.showtimeStartTime)}
              </dd>
              <dt className="text-muted-foreground">Hall</dt>
              <dd className="text-foreground">{result.hallName}</dd>
              <dt className="text-muted-foreground">Seats</dt>
              <dd className="flex flex-wrap gap-1.5">
                {result.seats.map((seat) => (
                  <Badge key={`${seat.rowLabel}${seat.columnNumber}`} variant="outline">
                    {seat.rowLabel}
                    {seat.columnNumber} · {SEAT_TYPE_LABEL[seat.seatType] ?? seat.seatType}
                  </Badge>
                ))}
              </dd>
              <dt className="text-muted-foreground">Redeemed at</dt>
              <dd className="font-mono text-foreground">
                {formatShowDate(result.redeemedAt)} {formatShowTime(result.redeemedAt)}
              </dd>
            </dl>
          </div>
        </CardContent>
      </Card>
    );
  }

  if (outcome.kind === "invalid") {
    return (
      <Card className="shadow-sm ring-destructive/30 bg-destructive/5">
        <CardContent className="flex items-start gap-3">
          <CircleX className="mt-0.5 size-5 shrink-0 text-destructive" aria-hidden />
          <div>
            <p className="font-medium text-foreground">Invalid Ticket Code</p>
            <p className="mt-1 text-sm text-muted-foreground">{outcome.message}</p>
          </div>
        </CardContent>
      </Card>
    );
  }

  if (outcome.kind === "conflict") {
    return (
      <Card className="shadow-sm ring-[color:var(--chart-amber-border)] bg-[color:var(--chart-amber-surface)]">
        <CardContent className="flex items-start gap-3">
          <AlertTriangle className="mt-0.5 size-5 shrink-0 text-[color:var(--chart-amber)]" aria-hidden />
          <div>
            <p className="font-medium text-foreground">Cannot Redeem This Ticket</p>
            <p className="mt-1 text-sm text-muted-foreground">{outcome.message}</p>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="shadow-sm ring-destructive/30 bg-destructive/5">
      <CardContent className="flex items-start gap-3">
        <CircleX className="mt-0.5 size-5 shrink-0 text-destructive" aria-hidden />
        <div>
          <p className="font-medium text-foreground">Redemption Failed</p>
          <p className="mt-1 text-sm text-muted-foreground">{outcome.message}</p>
        </div>
      </CardContent>
    </Card>
  );
}
