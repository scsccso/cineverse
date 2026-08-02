export type Role = "CUSTOMER" | "ADMIN";

export interface UserResponse {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  createdAt: string | null;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserResponse;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface ErrorResponse {
  code: number;
  message: string;
  timestamp: string;
}

export type MovieStatus = "NOW_PLAYING" | "COMING_SOON" | "ENDED";

export interface GenreResponse {
  id: string;
  name: string;
}

export interface MovieResponse {
  id: string;
  title: string;
  description: string | null;
  tagline: string | null;
  durationMinutes: number;
  contentRating: string | null;
  userRating: number | null;
  /** Never null — backend falls back to a placeholder image. */
  posterUrl: string;
  /** Never null — backend falls back to a placeholder image. */
  backdropUrl: string;
  trailerUrl: string | null;
  status: MovieStatus;
  genres: GenreResponse[];
  createdAt: string;
  updatedAt: string;
}

/** Matches Spring Data's default (unwrapped) Page<T> JSON — see MovieController. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface ShowtimeMovieSummary {
  id: string;
  title: string;
  durationMinutes: number;
  posterUrl: string;
}

export interface ShowtimeHallSummary {
  id: string;
  name: string;
  cinemaId: string;
  cinemaName: string;
}

export interface ShowtimeResponse {
  id: string;
  movie: ShowtimeMovieSummary;
  hall: ShowtimeHallSummary;
  startTime: string;
  /** startTime + movie.durationMinutes — the 20-minute cleanup buffer is not included. */
  endTime: string;
  price: number;
  createdAt: string;
  updatedAt: string;
}
