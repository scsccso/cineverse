package com.cineverse.backend.cinema.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record CinemaResponse(
        UUID id,
        @Schema(example = "CineVerse Downtown") String name,
        @Schema(example = "1 Cinema Plaza, Kuala Lumpur") String address,
        Instant createdAt,
        Instant updatedAt) {
}
