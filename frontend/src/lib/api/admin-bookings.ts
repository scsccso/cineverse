import { apiFetch } from "./client";
import type { AdminBookingSearchResult, BookingStatus, Page } from "./types";

export interface AdminBookingSearchParams {
  userEmail?: string;
  movieTitle?: string;
  status?: BookingStatus;
  page?: number;
  size?: number;
}

/** GET /api/v1/admin/bookings — ADMIN only. All three filters are optional and
 * combinable; omitting all of them browses every booking, paginated. Viewing a
 * single result's full detail and cancelling it both reuse the existing
 * customer-facing GET/DELETE /api/v1/bookings/{id} (see lib/api/bookings.ts) —
 * both already support an ADMIN override server-side, so there's nothing
 * booking-search-specific to add for either. */
export function searchBookings(
  token: string,
  params: AdminBookingSearchParams = {},
): Promise<Page<AdminBookingSearchResult>> {
  const query = new URLSearchParams();
  if (params.userEmail) query.set("userEmail", params.userEmail);
  if (params.movieTitle) query.set("movieTitle", params.movieTitle);
  if (params.status) query.set("status", params.status);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 20));

  return apiFetch<Page<AdminBookingSearchResult>>(`/api/v1/admin/bookings?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}
