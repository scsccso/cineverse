package com.cineverse.backend.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response body for both /internal/demo-reset endpoints — a scheduler
 * calling this on a cron has no other visibility into what happened besides
 * this response (and whatever it logs), so it reports real counts rather
 * than a bare 204. showtimesDeleted/showtimesCreated are always 0 for
 * POST .../transactions, which never touches showtimes.
 */
public record DemoResetResult(
        @Schema(description = "Bookings removed (payments removed first, per the FK direction — see "
                + "DemoResetService's doc comment)") int bookingsCleared,
        @Schema(description = "Payments removed") int paymentsCleared,
        @Schema(description = "0 for POST .../transactions") int showtimesDeleted,
        @Schema(description = "0 for POST .../transactions") int showtimesCreated) {
}
