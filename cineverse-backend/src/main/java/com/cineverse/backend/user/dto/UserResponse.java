package com.cineverse.backend.user.dto;

import com.cineverse.backend.user.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID id,
        @Schema(example = "moviefan@example.com") String email,
        @Schema(example = "Jane Doe") String fullName,
        @Schema(example = "CUSTOMER") Role role,
        Instant createdAt) {
}
