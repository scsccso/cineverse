package com.cineverse.backend.auth.service;

import com.cineverse.backend.user.dto.UserResponse;
import java.time.Duration;

/**
 * Internal result of a successful login/refresh. Carries the raw refresh
 * token so the controller can set it as an httpOnly cookie; it must never
 * be serialized into an API response body.
 */
public record AuthResult(
        String accessToken,
        Duration accessTokenTtl,
        String refreshToken,
        Duration refreshTokenTtl,
        UserResponse user) {
}
