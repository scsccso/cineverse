package com.cineverse.backend.showtime.exception;

public class ShowtimeHasBookingsException extends RuntimeException {

    public ShowtimeHasBookingsException() {
        super("Cannot delete this showtime: it still has bookings.");
    }
}
