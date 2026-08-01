package com.cineverse.backend.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Access and refresh tokens are signed with different secrets (in addition
 * to the "type" claim JwtService checks) so a leaked refresh secret can't
 * be used to forge access tokens, and vice versa.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String accessSecret,
        String refreshSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl) {
}
