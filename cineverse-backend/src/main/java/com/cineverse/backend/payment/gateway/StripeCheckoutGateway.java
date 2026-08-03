package com.cineverse.backend.payment.gateway;

/**
 * Wraps only the one Stripe operation that requires a real network call to a
 * real Stripe account (Session.create) — deliberately the single seam
 * PaymentService depends on, so tests can substitute a fake implementation
 * without a live Stripe test-mode key. Webhook signature verification is
 * pure local HMAC computation (no network, no account needed) and is called
 * directly against the Stripe SDK from PaymentService instead of being
 * hidden behind this interface, so tests exercise the real verification
 * logic rather than a stand-in for it.
 */
public interface StripeCheckoutGateway {

    CreatedCheckoutSession createSession(CheckoutSessionSpec spec);
}
