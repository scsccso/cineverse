package com.cineverse.backend.booking.service;

import com.cineverse.backend.booking.dto.SeatStatus;
import com.cineverse.backend.booking.entity.BookingStatus;
import java.time.Instant;

/**
 * Pure, dependency-free seat/booking state-machine logic — deliberately
 * pulled out of BookingService so it's directly unit-testable without a
 * database, same reasoning as ShowtimeOverlapChecker/SeatLayoutGenerator.
 */
public final class BookingStateMachine {

    private BookingStateMachine() {
    }

    /**
     * There is no scheduled sweep job for expiry — every read path (GET
     * .../seats, GET/DELETE a booking) calls this and persists the
     * transition on the spot if it returns true. A PENDING hold is treated
     * as expired the instant "now" reaches or passes expiresAt (hence
     * {@code !isAfter}, not strict {@code isBefore}). CONFIRMED/EXPIRED/
     * CANCELLED are all terminal and never re-evaluated against the clock.
     */
    public static boolean shouldLazilyExpire(BookingStatus status, Instant expiresAt, Instant now) {
        return status == BookingStatus.PENDING && !expiresAt.isAfter(now);
    }

    /** Maps a seat's current active booking status (null = no active booking at all) to its display status. */
    public static SeatStatus resolveSeatStatus(BookingStatus activeBookingStatus) {
        if (activeBookingStatus == null) {
            return SeatStatus.AVAILABLE;
        }
        return switch (activeBookingStatus) {
            case PENDING -> SeatStatus.LOCKED;
            case CONFIRMED -> SeatStatus.BOOKED;
            // Defensive only: callers are expected to have already filtered
            // these out (they're not "active"), but the switch must stay
            // exhaustive.
            case EXPIRED, CANCELLED -> SeatStatus.AVAILABLE;
        };
    }
}
