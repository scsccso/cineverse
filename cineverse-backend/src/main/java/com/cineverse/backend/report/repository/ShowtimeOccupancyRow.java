package com.cineverse.backend.report.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Raw per-showtime aggregation result straight off the SQL row, before the
 * occupancy rate is computed — kept separate from
 * {@link com.cineverse.backend.report.dto.ShowtimeOccupancy} so
 * {@code OccupancyMath.rate(...)} stays the single place that turns
 * booked/total counts into a rate, instead of that division being duplicated
 * (or done differently) in SQL and in Java.
 */
public record ShowtimeOccupancyRow(
        UUID showtimeId,
        String movieTitle,
        UUID hallId,
        String hallName,
        Instant startTime,
        long totalSeats,
        long bookedSeats) {
}
