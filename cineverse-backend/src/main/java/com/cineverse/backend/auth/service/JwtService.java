package com.cineverse.backend.auth.service;

import com.cineverse.backend.auth.config.JwtProperties;
import com.cineverse.backend.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies access/refresh JWTs. The two token kinds are signed
 * with different keys and carry a "type" claim, so an access token can
 * never be replayed where a refresh token is expected, or vice versa.
 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.accessKey = Keys.hmacShaKeyFor(properties.accessSecret().getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(properties.refreshSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(accessKey)
                .compact();
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.refreshTokenTtl())))
                .signWith(refreshKey)
                .compact();
    }

    /** @throws JwtException if the token is malformed, expired, or not an access token */
    public Claims parseAccessToken(String token) {
        return parse(token, accessKey, TYPE_ACCESS);
    }

    /** @throws JwtException if the token is malformed, expired, or not a refresh token */
    public Claims parseRefreshToken(String token) {
        return parse(token, refreshKey, TYPE_REFRESH);
    }

    public Duration getAccessTokenTtl() {
        return properties.accessTokenTtl();
    }

    public Duration getRefreshTokenTtl() {
        return properties.refreshTokenTtl();
    }

    private Claims parse(String token, SecretKey key, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Unexpected token type, expected \"" + expectedType + "\"");
        }
        return claims;
    }
}
