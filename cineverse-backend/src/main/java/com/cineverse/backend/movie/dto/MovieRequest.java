package com.cineverse.backend.movie.dto;

import com.cineverse.backend.movie.entity.MovieStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/** Shared body shape for both create (POST) and full update (PUT). */
public record MovieRequest(
        @NotBlank @Size(max = 255)
        @Schema(example = "Interstellar") String title,

        String description,

        @Size(max = 500)
        @Schema(example = "Mankind's next step will be our greatest.") String tagline,

        @NotNull @Positive
        @Schema(example = "169") Integer durationMinutes,

        @Size(max = 20)
        @Schema(example = "PG-13") String contentRating,

        @DecimalMin(value = "0.0") @DecimalMax(value = "10.0")
        @Schema(example = "8.7") BigDecimal userRating,

        @Size(max = 500) String trailerUrl,

        @NotNull MovieStatus status,

        Set<UUID> genreIds) {
}
