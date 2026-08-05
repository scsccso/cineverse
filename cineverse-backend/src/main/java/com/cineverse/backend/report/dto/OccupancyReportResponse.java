package com.cineverse.backend.report.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/admin/reports/occupancy. {@code showtimes} is the per-showtime
 * breakdown (already carries hall + movie, so filtering by hallId/movieId and
 * "by time period" via from/to are the three axes CLAUDE.md Phase 8 asks
 * for — there's no separate cross-tab grouping endpoint); the three totals
 * are that list rolled up so the frontend doesn't have to re-sum it.
 */
public record OccupancyReportResponse(
        LocalDate from,
        LocalDate to,
        UUID hallId,
        UUID movieId,
        List<ShowtimeOccupancy> showtimes,
        long totalSeats,
        long totalBookedSeats,
        double overallOccupancyRate) {
}
