package com.cineverse.backend.payment.exception;

/** The Stripe-Signature header didn't verify against the configured webhook secret — maps to 400 via GlobalExceptionHandler. */
public class InvalidStripeSignatureException extends RuntimeException {

    public InvalidStripeSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
