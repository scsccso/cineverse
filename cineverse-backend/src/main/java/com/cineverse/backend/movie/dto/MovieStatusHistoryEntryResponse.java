package com.cineverse.backend.movie.dto;

import com.cineverse.backend.movie.entity.MovieStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One row of GET /api/v1/admin/movies/{id}/status-history, newest first.
 * fromStatus is null for the very first row a movie ever gets (written by
 * MovieService.create — an initial state, not a "change"). changedByEmail
 * is null when the acting admin's account has since been deleted
 * (movie_status_history.changed_by is ON DELETE SET NULL — the row itself
 * survives, only the "who" is lost); the frontend shows "—" in that case. */
public record MovieStatusHistoryEntryResponse(
        UUID id,
        MovieStatus fromStatus,
        MovieStatus toStatus,
        Instant changedAt,
        @Schema(example = "admin@cineverse.local") String changedByEmail) {
}
