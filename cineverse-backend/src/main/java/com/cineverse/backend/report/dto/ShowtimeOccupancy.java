package com.cineverse.backend.report.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One showtime's occupancy row. {@code bookedSeats} only counts seats
 * belonging to CONFIRMED bookings — a PENDING booking is a transient 5-minute
 * hold, not a real sale, so counting it here would make the report flicker
 * with in-progress checkouts (same "only CONFIRMED counts" rule as the sales
 * report, see CLAUDE.md Phase 8).
 */
public record ShowtimeOccupancy(
        UUID showtimeId,
        String movieTitle,
        UUID hallId,
        String hallName,
        Instant startTime,
        long totalSeats,
        long bookedSeats,
        double occupancyRate) {
}
