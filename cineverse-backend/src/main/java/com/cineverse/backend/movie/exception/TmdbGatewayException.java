package com.cineverse.backend.movie.exception;

/** Covers both "TMDB_API_KEY not configured" and "the TMDB call itself
 * failed" (network error, non-2xx, timeout) — the frontend shows the same
 * "TMDB search unavailable, use manual entry" message either way, so one
 * exception type with a specific message is enough; no need for the caller
 * to distinguish the two causes. Mapped to 502 by GlobalExceptionHandler,
 * not the generic 500 handler — this is an upstream failure, not a bug in
 * this app's own code. */
public class TmdbGatewayException extends RuntimeException {

    public TmdbGatewayException(String message) {
        super(message);
    }

    public TmdbGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
