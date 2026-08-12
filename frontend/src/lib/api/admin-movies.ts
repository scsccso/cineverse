import { apiFetch, ApiError } from "./client";
import type { ErrorResponse, GenreResponse, MovieRequest, MovieResponse, Page } from "./types";

// 8081, not 8080 — see lib/api/client.ts's API_BASE_URL comment and
// docs/DEVELOPMENT.md's port-conflict note.
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

/** GET /api/v1/movies is public (no Authorization needed) — same endpoint the
 * customer-facing movies.ts hits, but with page/size instead of a status
 * filter, since admin needs every status (including ENDED) in one paginated
 * list, not just what's currently playing. */
export function getAdminMovies(page: number = 0, size: number = 20): Promise<Page<MovieResponse>> {
  return apiFetch<Page<MovieResponse>>(`/api/v1/movies?page=${page}&size=${size}`);
}

/** GET /api/v1/genres is public and returns the fixed, non-manageable
 * 15-value genre list — the only source for genreIds, see MovieRequest. */
export function getGenres(): Promise<GenreResponse[]> {
  return apiFetch<GenreResponse[]>("/api/v1/genres");
}

export function createMovie(token: string, request: MovieRequest): Promise<MovieResponse> {
  return apiFetch<MovieResponse>("/api/v1/movies", {
    method: "POST",
    body: request,
    headers: { Authorization: `Bearer ${token}` },
  });
}

/** PUT is a full replace, not a patch — request must carry the complete,
 * already-merged field set (including genreIds), see MovieRequest. */
export function updateMovie(token: string, id: string, request: MovieRequest): Promise<MovieResponse> {
  return apiFetch<MovieResponse>(`/api/v1/movies/${id}`, {
    method: "PUT",
    body: request,
    headers: { Authorization: `Bearer ${token}` },
  });
}

/** 409 when the movie still has scheduled showtimes — message is returned
 * as-is by the caller, not re-worded here (see admin/movies/page.tsx). */
export function deleteMovie(token: string, id: string): Promise<void> {
  return apiFetch<void>(`/api/v1/movies/${id}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
}

/** Poster/backdrop upload is multipart, so it can't go through apiFetch
 * (which always JSON.stringifies the body and sets Content-Type:
 * application/json) — same reason admin-reports.ts's downloadExport bypasses
 * it for file responses. Requires the movie to already exist (404 otherwise)
 * — there's no one-step "create with images" endpoint. */
async function uploadMovieImage(token: string, path: string, file: File): Promise<MovieResponse> {
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    // No Content-Type header here on purpose — the browser sets
    // multipart/form-data with the correct boundary itself; setting it
    // manually breaks the boundary and the backend can't parse the body.
    headers: { Authorization: `Bearer ${token}` },
    credentials: "include",
    body: formData,
  });

  const text = await response.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!response.ok) {
    const errorBody = data as Partial<ErrorResponse> | undefined;
    throw new ApiError(response.status, errorBody?.code ?? response.status, errorBody?.message ?? "Upload failed");
  }

  return data as MovieResponse;
}

export function uploadMoviePoster(token: string, id: string, file: File): Promise<MovieResponse> {
  return uploadMovieImage(token, `/api/v1/movies/${id}/poster`, file);
}

export function uploadMovieBackdrop(token: string, id: string, file: File): Promise<MovieResponse> {
  return uploadMovieImage(token, `/api/v1/movies/${id}/backdrop`, file);
}
