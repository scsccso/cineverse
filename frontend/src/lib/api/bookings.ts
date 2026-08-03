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

export function getBooking(accessToken: string, id: string): Promise<BookingResponse> {
  return apiFetch<BookingResponse>(`/api/v1/bookings/${id}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}

/** Extends the booking's hold to 35 minutes server-side and returns the Stripe-hosted checkout page URL — redirect the whole page there (no Stripe.js needed). */
export function createCheckoutSession(
  accessToken: string,
  bookingId: string,
): Promise<CheckoutSessionResponse> {
  return apiFetch<CheckoutSessionResponse>(`/api/v1/bookings/${bookingId}/checkout`, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}
