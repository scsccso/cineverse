package com.cineverse.backend.showtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** No endTime field — it's always computed from startTime + movie.durationMinutes, never admin-entered. */
public record CreateShowtimeRequest(
        @NotNull UUID movieId,

        @NotNull UUID hallId,

        @NotNull
        @Schema(example = "2026-08-10T14:00:00Z") Instant startTime,

        @NotNull @DecimalMin(value = "0.0")
        @Schema(example = "25.00") BigDecimal price) {
}
