import { apiFetch } from "./client";
import type { CreateShowtimeRequest, ShowtimeResponse } from "./types";

/** GET /api/v1/showtimes is public (no Authorization needed) — the same
 * endpoint the customer booking flow's listShowtimesByMovie hits, just with
 * hallId/date added on top of movieId. Deliberately not paginated: this is a
 * shared public endpoint (changing it to Page<T> would break the customer
 * flow's parsing), and at this project's scale (one cinema, a handful of
 * halls) an unpaginated list filtered by date is simpler than standing up a
 * separate paginated admin-only endpoint for the same data — see CLAUDE.md's
 * "Admin 场次管理" decision record for the full tradeoff. */
export function listShowtimes(params?: {
  movieId?: string;
  hallId?: string;
  date?: string;
}): Promise<ShowtimeResponse[]> {
  const query = new URLSearchParams();
  if (params?.movieId) query.set("movieId", params.movieId);
  if (params?.hallId) query.set("hallId", params.hallId);
  if (params?.date) query.set("date", params.date);
  const queryString = query.toString();
  return apiFetch<ShowtimeResponse[]>(`/api/v1/showtimes${queryString ? `?${queryString}` : ""}`);
}

export function createShowtime(token: string, request: CreateShowtimeRequest): Promise<ShowtimeResponse> {
  return apiFetch<ShowtimeResponse>("/api/v1/showtimes", {
    method: "POST",
    body: request,
    headers: { Authorization: `Bearer ${token}` },
  });
}

/** No update function — showtimes are create-or-delete only, see CreateShowtimeRequest's doc comment. */
export function deleteShowtime(token: string, id: string): Promise<void> {
  return apiFetch<void>(`/api/v1/showtimes/${id}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
}
