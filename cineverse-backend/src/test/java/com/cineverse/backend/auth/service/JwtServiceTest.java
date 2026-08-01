package com.cineverse.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cineverse.backend.auth.config.JwtProperties;
import com.cineverse.backend.user.entity.Role;
import com.cineverse.backend.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String ACCESS_SECRET = "test-access-secret-must-be-at-least-32-bytes-long";
    private static final String REFRESH_SECRET = "test-refresh-secret-must-be-at-least-32-bytes-long";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(ACCESS_SECRET, REFRESH_SECRET, Duration.ofMinutes(15), Duration.ofDays(7));
        jwtService = new JwtService(properties);
        user = new User("jane@example.com", "hashed", Role.CUSTOMER, "Jane Doe");
        user.setId(UUID.randomUUID());
    }

    @Test
    void accessTokenRoundTripCarriesExpectedClaims() {
        String token = jwtService.generateAccessToken(user);

        Claims claims = jwtService.parseAccessToken(token);

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("email", String.class)).isEqualTo("jane@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
        assertThat(claims.get("type", String.class)).isEqualTo("access");
    }

    @Test
    void refreshTokenRoundTripCarriesExpectedClaims() {
        String token = jwtService.generateRefreshToken(user);

        Claims claims = jwtService.parseRefreshToken(token);

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
    }

    @Test
    void accessTokenCannotBeParsedAsRefreshToken() {
        String accessToken = jwtService.generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.parseRefreshToken(accessToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refreshTokenCannotBeParsedAsAccessToken() {
        String refreshToken = jwtService.generateRefreshToken(user);

        assertThatThrownBy(() -> jwtService.parseAccessToken(refreshToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredAccessTokenIsRejected() throws Exception {
        JwtProperties shortLived = new JwtProperties(ACCESS_SECRET, REFRESH_SECRET, Duration.ofMillis(1), Duration.ofDays(7));
        JwtService shortLivedService = new JwtService(shortLived);
        String token = shortLivedService.generateAccessToken(user);

        Thread.sleep(20);

        assertThatThrownBy(() -> shortLivedService.parseAccessToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void accessAndRefreshSecretsAreIndependent() {
        String refreshToken = jwtService.generateRefreshToken(user);

        // An access-secret-signed parser must reject a refresh-secret-signed token outright.
        assertThatThrownBy(() -> jwtService.parseAccessToken(refreshToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void backToBackAccessTokensForSameUserAreNeverIdentical() {
        // iat/exp are second-granularity by the JWT spec and signing is
        // deterministic, so without a jti claim two tokens minted for the
        // same user within the same wall-clock second would be byte-for-byte
        // identical. Regression test for that: CI once hit exactly this.
        String first = jwtService.generateAccessToken(user);
        String second = jwtService.generateAccessToken(user);

        assertThat(second).isNotEqualTo(first);
    }
}
