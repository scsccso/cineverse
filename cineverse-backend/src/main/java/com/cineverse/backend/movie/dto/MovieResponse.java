package com.cineverse.backend.movie.dto;

import com.cineverse.backend.movie.entity.MovieStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MovieResponse(
        UUID id,
        @Schema(example = "Interstellar") String title,
        String description,
        @Schema(example = "Mankind's next step will be our greatest.") String tagline,
        @Schema(example = "169") Integer durationMinutes,
        @Schema(example = "PG-13") String contentRating,
        @Schema(example = "8.7") BigDecimal userRating,
        @Schema(description = "Never null — falls back to a placeholder image when not set") String posterUrl,
        @Schema(description = "Never null — falls back to a placeholder image when not set") String backdropUrl,
        String trailerUrl,
        MovieStatus status,
        List<GenreResponse> genres,
        Instant createdAt,
        Instant updatedAt) {
}
