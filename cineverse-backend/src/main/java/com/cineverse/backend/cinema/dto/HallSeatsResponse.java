package com.cineverse.backend.cinema.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Full seat-map payload for a hall — the base data Phase 5's seat picker
 * renders from. Grid dimensions travel alongside the seat list so the
 * frontend can size the grid container without a second request.
 */
public record HallSeatsResponse(
        UUID hallId,
        @Schema(example = "Hall 1") String hallName,
        @Schema(example = "6") Integer totalRows,
        @Schema(example = "10") Integer totalColumns,
        List<SeatResponse> seats) {
}
