package com.cineverse.backend.showtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class ShowtimeOverlapCheckerTest {

    private static final Duration BUFFER = Duration.ofMinutes(20);

    // Reference showtime: 10:00 - 12:00.
    private static final Instant A_START = Instant.parse("2026-08-10T10:00:00Z");
    private static final Instant A_END = Instant.parse("2026-08-10T12:00:00Z");

    @Test
    void fullyContainedIntervalConflicts() {
        // B entirely inside A.
        Instant bStart = A_START.plus(30, ChronoUnit.MINUTES);
        Instant bEnd = A_END.minus(30, ChronoUnit.MINUTES);

        assertThat(ShowtimeOverlapChecker.conflicts(A_START, A_END, bStart, bEnd, BUFFER)).isTrue();
    }

    @Test
    void partiallyOverlappingTailConflicts() {
        // B starts before A ends, extends past it.
        Instant bStart = A_END.minus(30, ChronoUnit.MINUTES);
        Instant bEnd = A_END.plus(2, ChronoUnit.HOURS);

        assertThat(ShowtimeOverlapChecker.conflicts(A_START, A_END, bStart, bEnd, BUFFER)).isTrue();
    }

    @Test
    void partiallyOverlappingHeadConflicts() {
        // B starts before A, ends inside A.
        Instant bStart = A_START.minus(2, ChronoUnit.HOURS);
        Instant bEnd = A_START.plus(30, ChronoUnit.MINUTES);

        assertThat(ShowtimeOverlapChecker.conflicts(A_START, A_END, bStart, bEnd, BUFFER)).isTrue();
    }

    @Test
    void identicalIntervalsConflict() {
        assertThat(ShowtimeOverlapChecker.conflicts(A_START, A_END, A_START, A_END, BUFFER)).isTrue();
    }

    @Test
    void exactlyAtTheTwentyMinuteBufferBoundaryDoesNotConflict() {
        // B starts exactly 20 minutes after A ends — the business rule's
        // "<=" allows this exactly.
        Instant bStart = A_END.plus(BUFFER);
        Instant bEnd = bStart.plus(90, ChronoUnit.MINUTES);

        assertThat(ShowtimeOverlapChecker.conflicts(A_START, A_END, bStart, bEnd, BUFFER)).isFalse();
    }

    @Test
    void oneMinuteInsideTheBufferBoundaryConflicts() {
        // Only 19 minutes of gap instead of the required 20.
        Instant bStart = A_END.plus(BUFFER).minus(1, ChronoUnit.MINUTES);
        Instant bEnd = bStart.plus(90, ChronoUnit.MINUTES);

        assertThat(ShowtimeOverlapChecker.conflicts(A_START, A_END, bStart, bEnd, BUFFER)).isTrue();
    }

    @Test
    void oneMinuteOutsideTheBufferBoundaryDoesNotConflict() {
        // 21 minutes of gap — comfortably clear.
        Instant bStart = A_END.plus(BUFFER).plus(1, ChronoUnit.MINUTES);
        Instant bEnd = bStart.plus(90, ChronoUnit.MINUTES);

        assertThat(ShowtimeOverlapChecker.conflicts(A_START, A_END, bStart, bEnd, BUFFER)).isFalse();
    }

    @Test
    void bufferAppliesSymmetricallyWhenTheOtherShowtimeIsEarlier() {
        // A is the "second" showtime this time: B ends exactly 20 minutes
        // before A starts — allowed.
        Instant bEnd = A_START.minus(BUFFER);
        Instant bStart = bEnd.minus(90, ChronoUnit.MINUTES);

        assertThat(ShowtimeOverlapChecker.conflicts(A_START, A_END, bStart, bEnd, BUFFER)).isFalse();

        // One minute short of the buffer — now it conflicts.
        Instant bEndTooClose = A_START.minus(BUFFER).plus(1, ChronoUnit.MINUTES);
        assertThat(ShowtimeOverlapChecker.conflicts(A_START, A_END, bStart, bEndTooClose, BUFFER)).isTrue();
    }

    @Test
    void clearlySeparatedIntervalsDoNotConflict() {
        Instant bStart = A_END.plus(3, ChronoUnit.HOURS);
        Instant bEnd = bStart.plus(90, ChronoUnit.MINUTES);

        assertThat(ShowtimeOverlapChecker.conflicts(A_START, A_END, bStart, bEnd, BUFFER)).isFalse();
    }
}
