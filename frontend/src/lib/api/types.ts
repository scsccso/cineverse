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

export type SeatType = "STANDARD" | "COUPLE";

/** Live per-seat status from the seat picker's polling endpoint — absent booking means AVAILABLE. */
export type SeatStatus = "AVAILABLE" | "LOCKED" | "BOOKED";

export interface SeatStatusEntry {
  seatId: string;
  rowLabel: string;
  /** Starting column; for COUPLE seats this is the left of the pair. */
  columnNumber: number;
  /** How many physical grid columns this seat occupies (STANDARD=1, COUPLE=2). */
  columnSpan: number;
  seatType: SeatType;
  status: SeatStatus;
}

/** GET /api/v1/showtimes/{id}/seats — polled every few seconds by the seat picker. */
export interface ShowtimeSeatsResponse {
  showtimeId: string;
  hallId: string;
  hallName: string;
  totalRows: number;
  totalColumns: number;
  seats: SeatStatusEntry[];
}

export interface CreateBookingRequest {
  showtimeId: string;
  seatIds: string[];
}

export type BookingStatus = "PENDING" | "CONFIRMED" | "EXPIRED" | "CANCELLED";

export interface BookingSeatResponse {
  seatId: string;
  rowLabel: string;
  columnNumber: number;
  seatType: SeatType;
  /** Price at the moment of booking — later showtime price changes never affect it. */
  priceAtBooking: number;
}

export interface BookingShowtimeSummary {
  id: string;
  movieTitle: string;
  hallName: string;
  startTime: string;
}

export interface BookingResponse {
  id: string;
  status: BookingStatus;
  totalPrice: number;
  /** End of the hold window — past this, a PENDING booking is lazily expired on next read. Starts at 5
   * minutes; extended to 35 once checkout begins (POST /bookings/{id}/checkout), to outlast Stripe's
   * own 30-minute-minimum Checkout Session expiry. */
  expiresAt: string;
  createdAt: string;
  showtime: BookingShowtimeSummary;
  seats: BookingSeatResponse[];
}

/** POST /api/v1/bookings/{id}/checkout — checkoutUrl is Stripe's hosted payment page; redirect the whole page there. */
export interface CheckoutSessionResponse {
  checkoutUrl: string;
}
