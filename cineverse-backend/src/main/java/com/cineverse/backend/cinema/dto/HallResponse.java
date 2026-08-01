package com.cineverse.backend.cinema.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record HallResponse(
        UUID id,
        UUID cinemaId,
        @Schema(example = "Hall 1") String name,
        @Schema(example = "6") Integer totalRows,
        @Schema(example = "10") Integer totalColumns,
        Instant createdAt,
        Instant updatedAt) {
}
