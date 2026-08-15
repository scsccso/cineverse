import { apiFetch } from "./client";
import type {
  CinemaResponse,
  CreateCinemaRequest,
  CreateHallRequest,
  HallResponse,
  HallSeatsResponse,
} from "./types";

/** GET /api/v1/cinemas is public. This MVP has exactly one cinema (see
 * CLAUDE.md Phase 3), but callers still ask the API rather than hardcoding
 * it, so a second cinema (if one's ever added) doesn't require a frontend
 * change here. */
export function listCinemas(): Promise<CinemaResponse[]> {
  return apiFetch<CinemaResponse[]>("/api/v1/cinemas");
}

/** GET /api/v1/cinemas/{id}/halls is public. */
export function listHalls(cinemaId: string): Promise<HallResponse[]> {
  return apiFetch<HallResponse[]>(`/api/v1/cinemas/${cinemaId}/halls`);
}

/** GET /api/v1/halls/{id}/seats is public — the full seat layout for one hall.
 * Used here just to tally the STANDARD/COUPLE split shown on the cinemas admin
 * page, not to render an actual seat grid (that's the booking flow's job, via
 * the showtime-scoped .../showtimes/{id}/seats endpoint instead). */
export function getHallSeats(hallId: string): Promise<HallSeatsResponse> {
  return apiFetch<HallSeatsResponse>(`/api/v1/halls/${hallId}/seats`);
}

/** POST /api/v1/cinemas — ADMIN only. No update/delete counterpart, see CLAUDE.md Phase 3. */
export function createCinema(token: string, request: CreateCinemaRequest): Promise<CinemaResponse> {
  return apiFetch<CinemaResponse>("/api/v1/cinemas", {
    method: "POST",
    body: request,
    headers: { Authorization: `Bearer ${token}` },
  });
}

/** POST /api/v1/cinemas/{cinemaId}/halls — ADMIN only. Seats are generated
 * automatically from totalRows/totalColumns (last row COUPLE, everything else
 * STANDARD); there's no separate "generate seats" call, and no way to choose
 * which row is COUPLE — see CreateHallRequest's doc comment. No update/delete
 * counterpart either, see CLAUDE.md Phase 3. */
export function createHall(token: string, cinemaId: string, request: CreateHallRequest): Promise<HallResponse> {
  return apiFetch<HallResponse>(`/api/v1/cinemas/${cinemaId}/halls`, {
    method: "POST",
    body: request,
    headers: { Authorization: `Bearer ${token}` },
  });
}
