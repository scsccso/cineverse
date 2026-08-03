package com.cineverse.backend.payment.gateway;

/** {@code sessionId} is stored as payments.stripe_session_id; {@code url} is what the frontend redirects the browser to. */
public record CreatedCheckoutSession(String sessionId, String url) {
}
