package com.cineverse.backend.auth.dto;

import com.cineverse.backend.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Refresh token is intentionally absent — it travels only via the httpOnly
 * {@code refresh_token} cookie set alongside this body, never in JSON.
 */
public record AuthResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "900", description = "Access token lifetime in seconds") long expiresIn,
        UserResponse user) {
}
