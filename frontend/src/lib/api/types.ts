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

/** Shared body shape for both POST (create) and PUT (full replace) — mirrors
 * the backend's MovieRequest record exactly, including that PUT is a full
 * replace: submitting genreIds always overwrites the movie's entire genre
 * set, never merges with what was there before. No image fields — an image
 * only ever reaches a movie via the separate multipart upload endpoints
 * (uploadMoviePoster/uploadMovieBackdrop) or the PATCH .../image-urls
 * hotlink endpoint (setMovieImageUrls, used by the TMDB-prefilled create
 * flow), never through create/update itself. */
export interface MovieRequest {
  title: string;
  description: string | null;
  tagline: string | null;
  durationMinutes: number;
  contentRating: string | null;
  userRating: number | null;
  trailerUrl: string | null;
  status: MovieStatus;
  genreIds: string[];
}

/** GET /api/v1/admin/movies/tmdb-search — deliberately minimal, just enough
 * to visually pick the right movie out of a list. No overview/runtime; see
 * TmdbMovieDetail for what selecting a result fetches. */
export interface TmdbSearchResult {
  tmdbId: number;
  title: string;
  /** Null if TMDB has no release date on file. */
  releaseYear: string | null;
  /** Null if TMDB has no poster for this title. */
  posterUrl: string | null;
}

/** GET /api/v1/admin/movies/tmdb-search/{tmdbId} — prefill data for the
 * create form. No contentRating/userRating/status/genreIds: TMDB's
 * taxonomy doesn't map cleanly onto this project's fixed genre list or
 * MPAA-style rating, so those stay admin-filled either way. */
export interface TmdbMovieDetail {
  tmdbId: number;
  title: string;
  description: string | null;
  durationMinutes: number | null;
  /** youtube.com watch URL for the preferred trailer, or null if TMDB has none. */
  trailerUrl: string | null;
  /** Hotlinked TMDB URL — never re-hosted. */
  posterUrl: string | null;
  /** Hotlinked TMDB URL — never re-hosted. */
  backdropUrl: string | null;
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
  backdropUrl: string;
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
  /** CONFIRMED bookings only — same counting rule as the admin occupancy report. */
  bookedSeats: number;
  /** Bookable seat units in this showtime's hall (one row per COUPLE seat, not two). */
  totalSeats: number;
}

/** POST /api/v1/showtimes body — mirrors the backend's CreateShowtimeRequest
 * exactly. No endTime field: the backend always derives it from
 * startTime + movie.durationMinutes, never accepts one directly. There is no
 * update/PATCH counterpart — showtimes are create-or-delete only, see
 * CLAUDE.md's Phase 4 "没有更新场次的 API" decision. */
export interface CreateShowtimeRequest {
  movieId: string;
  hallId: string;
  /** ISO-8601 instant (UTC), e.g. "2026-09-01T14:00:00Z" — see lib/validation/admin-showtimes.ts
   * for how a cinema-local wall-clock time picked in the form becomes this. */
  startTime: string;
  price: number;
}

/** GET /api/v1/cinemas — this MVP always has exactly one, but the admin showtime
 * form still asks the API rather than hardcoding it, so a second cinema (if one's
 * ever added) doesn't require a frontend change here. */
export interface CinemaResponse {
  id: string;
  name: string;
  address: string;
  createdAt: string;
  updatedAt: string;
}

/** GET /api/v1/cinemas/{id}/halls */
export interface HallResponse {
  id: string;
  cinemaId: string;
  name: string;
  totalRows: number;
  totalColumns: number;
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
  /** End of the 5-minute hold window — past this, a PENDING booking is lazily expired on next read.
   * Never extended for checkout (see CLAUDE.md Phase 6) — Stripe's own Checkout Session is proactively
   * expired instead when this booking is released. */
  expiresAt: string;
  createdAt: string;
  showtime: BookingShowtimeSummary;
  seats: BookingSeatResponse[];
  /** E-ticket code (a signed JWT) to render as a QR code — present only once status is CONFIRMED. */
  ticketCode: string | null;
  /** When this ticket was checked in via POST /api/v1/tickets/redeem — null until then. */
  redeemedAt: string | null;
}

/** POST /api/v1/bookings/{id}/checkout — checkoutUrl is Stripe's hosted payment page; redirect the whole page there. */
export interface CheckoutSessionResponse {
  checkoutUrl: string;
}

// ---- Ticket redemption (admin check-in UI) ----

export interface RedeemTicketRequest {
  ticketCode: string;
}

export interface TicketSeatSummary {
  rowLabel: string;
  columnNumber: number;
  seatType: SeatType;
}

/** POST /api/v1/tickets/redeem response — enough for the redeeming staff member to
 * visually confirm this is the right movie/showtime/seats before waving the holder in. */
export interface TicketRedemptionResponse {
  bookingId: string;
  movieTitle: string;
  hallName: string;
  showtimeStartTime: string;
  seats: TicketSeatSummary[];
  redeemedAt: string;
}

// ---- Admin reports (Phase 8) ----

export type ReportGranularity = "DAY" | "WEEK" | "MONTH";

export interface SalesBucket {
  /** Local (cinema-timezone) calendar date the bucket starts on — YYYY-MM-DD. Always present for every bucket in range, even when revenue is zero (backend gap-fills). */
  periodStart: string;
  revenue: number;
  bookingCount: number;
}

/** GET /api/v1/admin/reports/sales — ADMIN only. Only CONFIRMED bookings' SUCCEEDED payments count toward revenue; see pendingReconciliationAmount. */
export interface SalesReportResponse {
  from: string;
  to: string;
  granularity: ReportGranularity;
  movieId: string | null;
  hallId: string | null;
  currency: string;
  buckets: SalesBucket[];
  totalRevenue: number;
  /** ORPHANED_SUCCESS payments in range — money Stripe captured but not applied to any booking (seat may have been re-sold before the webhook arrived). Not included in totalRevenue; surfaced for manual reconciliation, not silently dropped. */
  pendingReconciliationAmount: number;
}

export interface ShowtimeOccupancy {
  showtimeId: string;
  movieTitle: string;
  hallId: string;
  hallName: string;
  startTime: string;
  totalSeats: number;
  /** Seats belonging to CONFIRMED bookings only — a PENDING hold doesn't count as occupied. */
  bookedSeats: number;
  /** 0..1 */
  occupancyRate: number;
}

/** GET /api/v1/admin/reports/occupancy — ADMIN only. */
export interface OccupancyReportResponse {
  from: string;
  to: string;
  hallId: string | null;
  movieId: string | null;
  showtimes: ShowtimeOccupancy[];
  totalSeats: number;
  totalBookedSeats: number;
  overallOccupancyRate: number;
}
