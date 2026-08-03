package com.cineverse.backend.ticket.exception;

import java.time.Instant;

/** Guards against re-entry with a photo/screenshot of an already-used ticket. Maps to 409. */
public class TicketAlreadyRedeemedException extends RuntimeException {

    public TicketAlreadyRedeemedException(Instant redeemedAt) {
        super("Ticket was already redeemed at " + redeemedAt);
    }
}
