package com.cineverse.backend.auth.controller;

import com.cineverse.backend.auth.dto.AuthResponse;
import com.cineverse.backend.auth.dto.LoginRequest;
import com.cineverse.backend.auth.dto.RegisterRequest;
import com.cineverse.backend.auth.exception.InvalidRefreshTokenException;
import com.cineverse.backend.auth.service.AuthResult;
import com.cineverse.backend.auth.service.AuthService;
import com.cineverse.backend.common.exception.ErrorResponse;
import com.cineverse.backend.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "注册 / 登录 / Token 刷新(轮换)/ 登出")
public class AuthController {

    static final String REFRESH_COOKIE_NAME = "refresh_token";
    // Path=/ (not /api/v1/auth): the cookie's Path is matched against the
    // REQUEST'S path regardless of which origin/port is being called, and
    // the frontend's Proxy needs to see this cookie on frontend routes like
    // /profile — not just on backend /api/v1/auth/* calls — to gate access.
    private static final String REFRESH_COOKIE_PATH = "/";

    private final AuthService authService;

    @Value("${app.security.cookie-secure}")
    private boolean cookieSecure;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "用户注册", description = "邮箱 + 密码注册,默认角色 CUSTOMER,密码用 BCrypt 加密存储")
    @ApiResponse(responseCode = "201", description = "注册成功")
    @ApiResponse(responseCode = "409", description = "邮箱已被注册",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录",
            description = "校验邮箱密码;access token 放响应体,refresh token 以 httpOnly cookie 下发")
    @ApiResponse(responseCode = "200", description = "登录成功")
    @ApiResponse(responseCode = "401", description = "邮箱或密码错误(不区分两者,防止枚举用户邮箱)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResult result = authService.login(request);
        setRefreshCookie(response, result.refreshToken(), result.refreshTokenTtl().toSeconds());
        return ResponseEntity.ok(toAuthResponse(result));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新 Token(Token Rotation)",
            description = "从 httpOnly cookie 读取旧 refresh token;校验通过后旧 token 立即标记 revoked,"
                    + "并下发新的 access + refresh token 对")
    @ApiResponse(responseCode = "200", description = "刷新成功,返回新 access token,并轮换 refresh token cookie")
    @ApiResponse(responseCode = "401", description = "refresh token 缺失、过期或已被撤销",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null) {
            throw new InvalidRefreshTokenException();
        }
        AuthResult result = authService.refresh(refreshToken);
        setRefreshCookie(response, result.refreshToken(), result.refreshTokenTtl().toSeconds());
        return ResponseEntity.ok(toAuthResponse(result));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "登出", description = "撤销 refresh token(DB 标记 revoked)并清除 cookie")
    @ApiResponse(responseCode = "204", description = "登出成功(即使没有有效 refresh token 也返回 204)")
    public void logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);
        clearRefreshCookie(response);
    }

    private void setRefreshCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, maxAgeSeconds).toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0).toString());
    }

    private ResponseCookie buildCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }

    private AuthResponse toAuthResponse(AuthResult result) {
        return new AuthResponse(result.accessToken(), "Bearer", result.accessTokenTtl().toSeconds(), result.user());
    }
}
