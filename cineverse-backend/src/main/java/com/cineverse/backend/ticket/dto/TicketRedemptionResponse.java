package com.cineverse.backend.ticket.dto;

import com.cineverse.backend.cinema.entity.SeatType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Returned to the scanning staff on a successful check-in, so they can visually confirm showtime/seats against the ticket holder. */
public record TicketRedemptionResponse(
        UUID bookingId,
        @Schema(example = "Interstellar") String movieTitle,
        @Schema(example = "Hall 1") String hallName,
        Instant showtimeStartTime,
        List<SeatSummary> seats,
        Instant redeemedAt) {

    public record SeatSummary(
            @Schema(example = "A") String rowLabel,
            @Schema(example = "1") Integer columnNumber,
            SeatType seatType) {
    }
}
