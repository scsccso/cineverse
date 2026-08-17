import { apiFetch, ApiError } from "./client";
import type {
  ErrorResponse,
  GenreResponse,
  MovieRequest,
  MovieResponse,
  MovieStatus,
  MovieStatusHistoryEntry,
  Page,
  TmdbMovieDetail,
  TmdbSearchResult,
} from "./types";

// 8081, not 8080 — see lib/api/client.ts's API_BASE_URL comment and
// docs/DEVELOPMENT.md's port-conflict note.
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

/** GET /api/v1/movies is public (no Authorization needed) — same endpoint the
 * customer-facing movies.ts hits, but with page/size instead of a status
 * filter, since admin needs every status (including ENDED) in one paginated
 * list, not just what's currently playing. title is an optional
 * case-insensitive "contains" search, added alongside page/size rather than
 * as a fourth positional param so existing callers that only pass page/size
 * don't need updating. */
export function getAdminMovies(page: number = 0, size: number = 20, title?: string): Promise<Page<MovieResponse>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (title) query.set("title", title);
  return apiFetch<Page<MovieResponse>>(`/api/v1/movies?${query.toString()}`);
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

/** PATCH /api/v1/movies/{id}/status — the only path that changes a movie's
 * status; PUT rejects a status difference outright (409, see updateMovie's
 * doc comment / MovieRequest). Requesting the movie's already-current
 * status is rejected too (409, "Movie is already X.") rather than treated
 * as a silent no-op — every successful call here writes a
 * movie_status_history row. */
export function updateMovieStatus(token: string, id: string, status: MovieStatus): Promise<MovieResponse> {
  return apiFetch<MovieResponse>(`/api/v1/movies/${id}/status`, {
    method: "PATCH",
    body: { status },
    headers: { Authorization: `Bearer ${token}` },
  });
}

/** GET /api/v1/admin/movies/{id}/status-history — ADMIN only (the response
 * includes the acting admin's email, so unlike GET /api/v1/movies/{id} this
 * can't sit on the public /api/v1/movies/** path), newest first. */
export function getMovieStatusHistory(token: string, id: string): Promise<MovieStatusHistoryEntry[]> {
  return apiFetch<MovieStatusHistoryEntry[]>(`/api/v1/admin/movies/${id}/status-history`, {
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

/** Server-side TMDB proxy — the API key never reaches the browser. Search
 * only, no pagination: TMDB's own relevance ranking on page 1 (up to 20
 * results) is what the admin scans visually; a title not showing up means
 * refining the query text, not paging through more results. */
export function searchTmdbMovies(token: string, query: string): Promise<TmdbSearchResult[]> {
  return apiFetch<TmdbSearchResult[]>(`/api/v1/admin/movies/tmdb-search?query=${encodeURIComponent(query)}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

/** One extra TMDB call, fired only once a specific search result is picked
 * — never prefetched for the whole results list, see CLAUDE.md's TMDB
 * call-budget note. */
export function getTmdbMovieDetail(token: string, tmdbId: number): Promise<TmdbMovieDetail> {
  return apiFetch<TmdbMovieDetail>(`/api/v1/admin/movies/tmdb-search/${tmdbId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

/** PATCH, not PUT — a partial update, matching updateUserRole's PATCH
 * elsewhere in this file's sibling admin-users.ts: only the fields present
 * in the body are set, the other is left untouched. Sets posterUrl/
 * backdropUrl directly to the given (hotlinked) URLs, bypassing
 * uploadMoviePoster/uploadMovieBackdrop entirely — used right after
 * createMovie succeeds in the TMDB-prefilled creation flow, never on the
 * edit page (which stays on the multipart upload path for swapping images,
 * see admin/movies/[id]/edit/page.tsx). */
export function setMovieImageUrls(
  token: string,
  id: string,
  urls: { posterUrl?: string; backdropUrl?: string },
): Promise<MovieResponse> {
  return apiFetch<MovieResponse>(`/api/v1/movies/${id}/image-urls`, {
    method: "PATCH",
    body: urls,
    headers: { Authorization: `Bearer ${token}` },
  });
}
