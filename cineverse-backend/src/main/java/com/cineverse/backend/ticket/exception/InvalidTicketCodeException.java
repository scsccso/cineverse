package com.cineverse.backend.ticket.exception;

/** The scanned/entered code doesn't verify — malformed, tampered with, or signed by a different secret. Maps to 400. */
public class InvalidTicketCodeException extends RuntimeException {

    public InvalidTicketCodeException(String message) {
        super(message);
    }

    public InvalidTicketCodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
