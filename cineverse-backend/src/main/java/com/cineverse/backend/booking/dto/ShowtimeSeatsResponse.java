package com.cineverse.backend.booking.dto;

import com.cineverse.backend.cinema.entity.SeatType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * The seat picker's polling payload: static layout (rowLabel/columnNumber/
 * columnSpan/seatType, unchanged since Phase 3) plus this showtime's live
 * status per seat. No WebSocket — the frontend re-fetches this on an
 * interval (see CLAUDE.md Phase 5 open-question #3).
 */
public record ShowtimeSeatsResponse(
        UUID showtimeId,
        UUID hallId,
        @Schema(example = "Hall 1") String hallName,
        Integer totalRows,
        Integer totalColumns,
        List<SeatStatusEntry> seats) {

    public record SeatStatusEntry(
            UUID seatId,
            @Schema(example = "A") String rowLabel,
            @Schema(example = "1", description = "Starting column; for COUPLE seats this is the left of the pair") Integer columnNumber,
            @Schema(example = "1", description = "STANDARD=1, COUPLE=2") Integer columnSpan,
            SeatType seatType,
            SeatStatus status) {
    }
}
