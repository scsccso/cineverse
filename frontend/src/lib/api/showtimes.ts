import { apiFetch } from "./client";
import type { ShowtimeResponse } from "./types";

export function listShowtimesByMovie(movieId: string): Promise<ShowtimeResponse[]> {
  return apiFetch<ShowtimeResponse[]>(`/api/v1/showtimes?movieId=${movieId}`);
}

export function getShowtime(id: string): Promise<ShowtimeResponse> {
  return apiFetch<ShowtimeResponse>(`/api/v1/showtimes/${id}`);
}
