package com.cineverse.backend.booking.repository;

import java.util.UUID;

/** One row per showtime that has at least one CONFIRMED booking — see {@link BookingSeatRepository#countConfirmedByShowtimeIdIn}. */
public record ShowtimeBookedSeatCount(UUID showtimeId, long bookedSeats) {
}
