package com.cineverse.backend.showtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Carries basic movie/hall info inline so the showtime detail page doesn't
 * need extra round trips to piece together a display.
 */
public record ShowtimeResponse(
        UUID id,
        MovieSummary movie,
        HallSummary hall,
        Instant startTime,
        @Schema(description = "startTime + movie.durationMinutes — the 20-minute cleanup buffer is not included") Instant endTime,
        @Schema(example = "25.00") BigDecimal price,
        Instant createdAt,
        Instant updatedAt,
        @Schema(description = "CONFIRMED bookings only — same counting rule as the Phase 8 occupancy report")
                int bookedSeats,
        @Schema(description = "Bookable seat units in this showtime's hall (one row per COUPLE seat, not two)")
                int totalSeats) {

    public record MovieSummary(
            UUID id,
            @Schema(example = "Interstellar") String title,
            @Schema(example = "169") Integer durationMinutes,
            @Schema(description = "Never null — falls back to a placeholder image when not set") String posterUrl,
            @Schema(description = "Never null — falls back to a placeholder image when not set") String backdropUrl) {
    }

    public record HallSummary(
            UUID id,
            @Schema(example = "Hall 1") String name,
            UUID cinemaId,
            @Schema(example = "CineVerse Downtown") String cinemaName) {
    }
}
