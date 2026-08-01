package com.cineverse.backend.auth.service;

import com.cineverse.backend.auth.dto.LoginRequest;
import com.cineverse.backend.auth.dto.RegisterRequest;
import com.cineverse.backend.auth.entity.RefreshToken;
import com.cineverse.backend.auth.exception.DuplicateEmailException;
import com.cineverse.backend.auth.exception.InvalidCredentialsException;
import com.cineverse.backend.auth.exception.InvalidRefreshTokenException;
import com.cineverse.backend.auth.repository.RefreshTokenRepository;
import com.cineverse.backend.user.entity.Role;
import com.cineverse.backend.user.entity.User;
import com.cineverse.backend.user.dto.UserResponse;
import com.cineverse.backend.user.mapper.UserMapper;
import com.cineverse.backend.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserMapper userMapper) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }
        User user = new User(email, passwordEncoder.encode(request.password()), Role.CUSTOMER, request.fullName());
        // saveAndFlush (not save): createdAt is populated by @CreationTimestamp
        // at flush time, and the response must reflect it, not null.
        return userMapper.toResponse(userRepository.saveAndFlush(user));
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(user);
    }

    /** Rotates the refresh token: the presented one is revoked and a brand new pair is issued. */
    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        Claims claims;
        try {
            claims = jwtService.parseRefreshToken(rawRefreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidRefreshTokenException();
        }

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        stored.setRevoked(true);
        refreshTokenRepository.saveAndFlush(stored);

        return issueTokens(stored.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.saveAndFlush(token);
                });
    }

    private AuthResult issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        Claims refreshClaims = jwtService.parseRefreshToken(refreshToken);
        Instant expiresAt = refreshClaims.getExpiration().toInstant();
        refreshTokenRepository.saveAndFlush(new RefreshToken(user, hash(refreshToken), expiresAt));

        return new AuthResult(
                accessToken,
                jwtService.getAccessTokenTtl(),
                refreshToken,
                jwtService.getRefreshTokenTtl(),
                userMapper.toResponse(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
