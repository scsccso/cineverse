package com.cineverse.backend.booking.exception;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SeatUnavailableException extends RuntimeException {

    public SeatUnavailableException(Set<UUID> seatIds) {
        super("Seat(s) already unavailable: "
                + seatIds.stream().map(UUID::toString).collect(Collectors.joining(", ")));
    }
}
