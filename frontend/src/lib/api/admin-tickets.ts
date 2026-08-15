import { apiFetch } from "./client";
import type { RedeemTicketRequest, TicketRedemptionResponse } from "./types";

/** POST /api/v1/tickets/redeem — ADMIN only. 400 means the code itself doesn't verify
 * (malformed/tampered/not a ticket code); 409 means the code is genuine but the booking
 * it points to can't be redeemed right now (already redeemed, or not CONFIRMED) — see
 * the two distinct ApiError.status branches in ticket-redemption-result.tsx. */
export function redeemTicket(token: string, ticketCode: string): Promise<TicketRedemptionResponse> {
  const request: RedeemTicketRequest = { ticketCode };
  return apiFetch<TicketRedemptionResponse>("/api/v1/tickets/redeem", {
    method: "POST",
    body: request,
    headers: { Authorization: `Bearer ${token}` },
  });
}
