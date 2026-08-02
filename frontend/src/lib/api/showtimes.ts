import { apiFetch } from "./client";
import type { ShowtimeResponse, ShowtimeSeatsResponse } from "./types";

export function listShowtimesByMovie(movieId: string): Promise<ShowtimeResponse[]> {
  return apiFetch<ShowtimeResponse[]>(`/api/v1/showtimes?movieId=${movieId}`);
}

export function getShowtime(id: string): Promise<ShowtimeResponse> {
  return apiFetch<ShowtimeResponse>(`/api/v1/showtimes/${id}`);
}

/** Public — no auth required. The seat picker polls this on an interval instead of using a WebSocket. */
export function getShowtimeSeats(id: string): Promise<ShowtimeSeatsResponse> {
  return apiFetch<ShowtimeSeatsResponse>(`/api/v1/showtimes/${id}/seats`);
}
