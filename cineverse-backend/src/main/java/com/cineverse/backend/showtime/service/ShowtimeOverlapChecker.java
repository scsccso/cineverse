package com.cineverse.backend.showtime.service;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure overlap math, kept separate from the DB/service layer specifically
 * so the boundary conditions are directly unit-testable without a database.
 *
 * <p>Two showtimes conflict if their cleanup-buffered intervals
 * {@code [start, end + buffer]} overlap. The buffer is added to each
 * interval's end only (not subtracted from its start) — padding both ends
 * the same way still correctly enforces the buffer in both directions,
 * whichever showtime turns out to be earlier.
 */
public final class ShowtimeOverlapChecker {

    private ShowtimeOverlapChecker() {
    }

    /**
     * Touching exactly at the buffer boundary (one showtime's
     * {@code end + buffer} exactly equal to the other's {@code start}) is
     * NOT a conflict — the business rule is {@code end + buffer <= start},
     * so equality is allowed. Hence strict {@code isBefore}, not
     * {@code !isAfter}.
     */
    public static boolean conflicts(
            Instant aStart, Instant aEnd, Instant bStart, Instant bEnd, Duration cleanupBuffer) {
        return aStart.isBefore(bEnd.plus(cleanupBuffer)) && bStart.isBefore(aEnd.plus(cleanupBuffer));
    }
}
