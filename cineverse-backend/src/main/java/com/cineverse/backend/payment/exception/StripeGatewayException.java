package com.cineverse.backend.payment.exception;

/** Wraps a checked StripeException so it can cross a plain interface method — not a business-domain fault, falls through to the generic 500 handler. */
public class StripeGatewayException extends RuntimeException {

    public StripeGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
