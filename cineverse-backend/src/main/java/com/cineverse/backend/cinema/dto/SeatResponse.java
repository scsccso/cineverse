package com.cineverse.backend.cinema.dto;

import com.cineverse.backend.cinema.entity.SeatType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Base seat-map rendering unit for Phase 5. {@code columnSpan} is derived
 * (STANDARD=1, COUPLE=2) so the frontend never has to hardcode which seat
 * types are physically wider — it just reads the number.
 */
public record SeatResponse(
        UUID id,
        @Schema(example = "A") String rowLabel,
        @Schema(example = "1", description = "Starting column; for COUPLE seats this is the left of the pair") Integer columnNumber,
        @Schema(example = "1", description = "How many physical grid columns this seat occupies (STANDARD=1, COUPLE=2)") Integer columnSpan,
        SeatType seatType) {
}
