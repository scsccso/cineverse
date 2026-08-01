package com.cineverse.backend.common.exception;

import java.time.Instant;

/**
 * Uniform error envelope returned by every endpoint via
 * {@link GlobalExceptionHandler}.
 */
public record ErrorResponse(int code, String message, Instant timestamp) {
}
