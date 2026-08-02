package com.cineverse.backend.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cineverse.backend.booking.dto.SeatStatus;
import com.cineverse.backend.booking.entity.BookingStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BookingStateMachineTest {

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

    @Test
    void pendingBookingBeforeItsExpiryIsNotLazilyExpired() {
        Instant expiresAt = NOW.plusSeconds(1);
        assertThat(BookingStateMachine.shouldLazilyExpire(BookingStatus.PENDING, expiresAt, NOW)).isFalse();
    }

    @Test
    void pendingBookingExactlyAtItsExpiryIsLazilyExpired() {
        // Boundary: now == expiresAt counts as expired ("!isAfter", not strict isBefore).
        assertThat(BookingStateMachine.shouldLazilyExpire(BookingStatus.PENDING, NOW, NOW)).isTrue();
    }

    @Test
    void pendingBookingPastItsExpiryIsLazilyExpired() {
        Instant expiresAt = NOW.minusSeconds(1);
        assertThat(BookingStateMachine.shouldLazilyExpire(BookingStatus.PENDING, expiresAt, NOW)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CONFIRMED", "EXPIRED", "CANCELLED"})
    void nonPendingStatusesAreNeverLazilyExpiredRegardlessOfHowLongPastExpiry(BookingStatus status) {
        Instant longPastExpiry = NOW.minus(Duration.ofDays(1));
        assertThat(BookingStateMachine.shouldLazilyExpire(status, longPastExpiry, NOW)).isFalse();
    }

    @Test
    void noActiveBookingResolvesToAvailable() {
        assertThat(BookingStateMachine.resolveSeatStatus(null)).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void pendingBookingResolvesToLocked() {
        assertThat(BookingStateMachine.resolveSeatStatus(BookingStatus.PENDING)).isEqualTo(SeatStatus.LOCKED);
    }

    @Test
    void confirmedBookingResolvesToBooked() {
        assertThat(BookingStateMachine.resolveSeatStatus(BookingStatus.CONFIRMED)).isEqualTo(SeatStatus.BOOKED);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"EXPIRED", "CANCELLED"})
    void terminalNonActiveStatusesResolveToAvailable(BookingStatus status) {
        assertThat(BookingStateMachine.resolveSeatStatus(status)).isEqualTo(SeatStatus.AVAILABLE);
    }
}
