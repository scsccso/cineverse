package com.cineverse.backend.payment.gateway;

/**
 * Wraps only the Stripe operations that require a real network call to a
 * real Stripe account (Session.create/expire) — deliberately the seam
 * PaymentService depends on, so tests can substitute a fake implementation
 * without a live Stripe test-mode key. Webhook signature verification is
 * pure local HMAC computation (no network, no account needed) and is called
 * directly against the Stripe SDK from PaymentService instead of being
 * hidden behind this interface, so tests exercise the real verification
 * logic rather than a stand-in for it.
 */
public interface StripeCheckoutGateway {

    CreatedCheckoutSession createSession(CheckoutSessionSpec spec);

    /**
     * Proactively ends a Checkout Session before its own {@code expires_at}
     * — called when the booking behind it is released (lazily expired or
     * cancelled) well before Stripe's 30-minute floor would otherwise close
     * it on its own. Implementations should let a StripeGatewayException
     * propagate for genuine failures (including "session already
     * completed/expired", which the caller treats as informational, not
     * fatal — see PaymentService.onBookingReleased).
     */
    void expireSession(String stripeSessionId);
}
