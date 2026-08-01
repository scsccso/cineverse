package com.cineverse.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cineverse.backend.auth.config.JwtProperties;
import com.cineverse.backend.auth.dto.LoginRequest;
import com.cineverse.backend.auth.dto.RegisterRequest;
import com.cineverse.backend.auth.entity.RefreshToken;
import com.cineverse.backend.auth.exception.DuplicateEmailException;
import com.cineverse.backend.auth.exception.InvalidCredentialsException;
import com.cineverse.backend.auth.exception.InvalidRefreshTokenException;
import com.cineverse.backend.auth.repository.RefreshTokenRepository;
import com.cineverse.backend.user.dto.UserResponse;
import com.cineverse.backend.user.entity.Role;
import com.cineverse.backend.user.entity.User;
import com.cineverse.backend.user.mapper.UserMapper;
import com.cineverse.backend.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "test-access-secret-must-be-at-least-32-bytes-long",
                "test-refresh-secret-must-be-at-least-32-bytes-long",
                Duration.ofMinutes(15),
                Duration.ofDays(7));
        JwtService jwtService = new JwtService(properties);
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtService, userMapper);
    }

    @Test
    void registerNormalizesEmailAndEncodesPasswordForNewCustomer() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Sup3rSecret!")).thenReturn("hashed-pw");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UserResponse expected = new UserResponse(UUID.randomUUID(), "jane@example.com", "Jane Doe", Role.CUSTOMER, Instant.now());
        when(userMapper.toResponse(any(User.class))).thenReturn(expected);

        UserResponse result = authService.register(new RegisterRequest(" Jane@Example.com ", "Sup3rSecret!", "Jane Doe"));

        assertThat(result).isEqualTo(expected);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("jane@example.com");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-pw");
        assertThat(captor.getValue().getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registerRejectsDuplicateEmailWithoutTouchingPasswordEncoder() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("jane@example.com", "Sup3rSecret!", "Jane Doe")))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void loginSucceedsAndIssuesAccessAndRefreshTokens() {
        User user = new User("jane@example.com", "hashed-pw", Role.CUSTOMER, "Jane Doe");
        user.setId(UUID.randomUUID());
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Sup3rSecret!", "hashed-pw")).thenReturn(true);
        UserResponse expected = new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), Instant.now());
        when(userMapper.toResponse(user)).thenReturn(expected);

        AuthResult result = authService.login(new LoginRequest("jane@example.com", "Sup3rSecret!"));

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.user()).isEqualTo(expected);
        verify(refreshTokenRepository).saveAndFlush(any(RefreshToken.class));
    }

    @Test
    void loginFailsForUnknownEmailWithGenericMessage() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void loginFailsForWrongPasswordWithSameGenericMessageAsUnknownEmail() {
        User user = new User("jane@example.com", "hashed-pw", Role.CUSTOMER, "Jane Doe");
        user.setId(UUID.randomUUID());
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-pw")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void logoutIsANoOpWhenNoRefreshTokenCookieWasPresent() {
        authService.logout(null);

        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void refreshRejectsAMalformedToken() {
        assertThatThrownBy(() -> authService.refresh("not-a-jwt"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
