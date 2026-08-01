package com.cineverse.backend.movie.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record GenreResponse(
        UUID id,
        @Schema(example = "Sci-Fi") String name) {
}
