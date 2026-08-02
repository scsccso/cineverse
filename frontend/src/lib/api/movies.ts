import { apiFetch } from "./client";
import type { MovieResponse, MovieStatus, Page } from "./types";

export function listMovies(status?: MovieStatus): Promise<Page<MovieResponse>> {
  const query = status ? `?status=${status}` : "";
  return apiFetch<Page<MovieResponse>>(`/api/v1/movies${query}`);
}

export function getMovie(id: string): Promise<MovieResponse> {
  return apiFetch<MovieResponse>(`/api/v1/movies/${id}`);
}
