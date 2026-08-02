import { apiFetch } from "./client";
import type { BookingResponse, CreateBookingRequest } from "./types";

/** All three endpoints require a bearer access token — see useAuth().callAuthorized. */
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
