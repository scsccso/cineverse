import { apiFetch } from "./client";
import type { BookingResponse, CheckoutSessionResponse, CreateBookingRequest } from "./types";

/** All endpoints below require a bearer access token — see useAuth().callAuthorized. */
export function createBooking(
  accessToken: string,
  payload: CreateBookingRequest,
): Promise<BookingResponse> {
  return apiFetch<BookingResponse>("/api/v1/bookings", {
    method: "POST",
    body: payload,
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}

export function cancelBooking(accessToken: string, id: string): Promise<void> {
  return apiFetch<void>(`/api/v1/bookings/${id}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}

/** The caller's own orders, newest first. Strictly self-scoped server-side — an ADMIN token gets its own orders too, not everyone's. */
export function listBookings(accessToken: string): Promise<BookingResponse[]> {
  return apiFetch<BookingResponse[]>("/api/v1/bookings", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}

export function getBooking(accessToken: string, id: string): Promise<BookingResponse> {
  return apiFetch<BookingResponse>(`/api/v1/bookings/${id}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}

/** Creates a Stripe Checkout Session and returns its hosted page URL — redirect the whole page there (no Stripe.js needed). Does not touch the booking's 5-minute hold (see CLAUDE.md Phase 6). */
export function createCheckoutSession(
  accessToken: string,
  bookingId: string,
): Promise<CheckoutSessionResponse> {
  return apiFetch<CheckoutSessionResponse>(`/api/v1/bookings/${bookingId}/checkout`, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}
