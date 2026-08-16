import { CalendarX2, CheckCircle2, Clock, CircleX } from "lucide-react";
import type { BookingStatus } from "@/lib/api/types";
import { Badge } from "@/components/ui/badge";

/** Same three-way encoding (icon + text + color) as the customer bookings
 * page's StatusTag — same meaning should look the same everywhere in the
 * app, just via Badge's admin-appropriate flat styling instead of that
 * page's GlassCard-specific classes. Redeemed still takes precedence over
 * Confirmed: a ticket that's already been used at the door is the more
 * useful thing for support staff to see at a glance. Shared between
 * admin/bookings' search results and admin/bookings/[id]'s detail view. */
export function BookingStatusBadge({ status, redeemedAt }: { status: BookingStatus; redeemedAt: string | null }) {
  if (status === "CONFIRMED" && redeemedAt) {
    return (
      <Badge variant="secondary" className="gap-1">
        <CheckCircle2 className="size-3" aria-hidden />
        Redeemed
      </Badge>
    );
  }
  switch (status) {
    case "CONFIRMED":
      return (
        <Badge variant="default" className="gap-1">
          <CheckCircle2 className="size-3" aria-hidden />
          Confirmed
        </Badge>
      );
    case "PENDING":
      return (
        <Badge variant="outline" className="gap-1">
          <Clock className="size-3" aria-hidden />
          Pending
        </Badge>
      );
    case "EXPIRED":
      return (
        <Badge variant="secondary" className="gap-1">
          <CalendarX2 className="size-3" aria-hidden />
          Expired
        </Badge>
      );
    case "CANCELLED":
      return (
        <Badge variant="secondary" className="gap-1">
          <CircleX className="size-3" aria-hidden />
          Cancelled
        </Badge>
      );
  }
}
