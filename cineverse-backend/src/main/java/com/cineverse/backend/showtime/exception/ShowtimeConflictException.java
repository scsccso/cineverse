package com.cineverse.backend.showtime.exception;

import java.time.Instant;

public class ShowtimeConflictException extends RuntimeException {

    public ShowtimeConflictException(String hallName, Instant conflictingStart, Instant conflictingEnd) {
        super(String.format(
                "Conflicts with an existing showtime in %s (%s - %s, including the 20-minute cleanup buffer)",
                hallName, conflictingStart, conflictingEnd));
    }
}
