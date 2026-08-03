package com.cineverse.backend.ticket.exception;

import com.cineverse.backend.booking.entity.BookingStatus;

/** The ticket code is genuine but the booking it points to isn't (or is no longer) CONFIRMED. Maps to 409. */
public class BookingNotConfirmedException extends RuntimeException {

    public BookingNotConfirmedException(BookingStatus actualStatus) {
        super("Booking is " + actualStatus + ", not CONFIRMED — cannot be redeemed");
    }
}
