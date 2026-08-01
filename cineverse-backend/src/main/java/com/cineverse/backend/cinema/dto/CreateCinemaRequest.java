package com.cineverse.backend.cinema.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCinemaRequest(
        @NotBlank @Size(max = 255)
        @Schema(example = "CineVerse Downtown") String name,

        @Size(max = 500)
        @Schema(example = "1 Cinema Plaza, Kuala Lumpur") String address) {
}
