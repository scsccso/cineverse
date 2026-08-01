package com.cineverse.backend.cinema.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Seats are generated automatically from these dimensions (last row =
 * COUPLE, everything else STANDARD — see SeatLayoutGenerator). There is no
 * separate "seats" field: this endpoint owns layout generation, not the
 * caller.
 */
public record CreateHallRequest(
        @NotBlank @Size(max = 255)
        @Schema(example = "Hall 4") String name,

        @NotNull @Positive @Max(40)
        @Schema(example = "8") Integer totalRows,

        @NotNull @Positive @Max(50)
        @Schema(example = "12") Integer totalColumns) {
}
