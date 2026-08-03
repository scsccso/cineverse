package com.cineverse.backend.payment.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Everything StripeCheckoutGateway needs to create one Checkout Session — deliberately not a Stripe SDK type. */
public record CheckoutSessionSpec(
        UUID bookingId,
        BigDecimal amount,
        String currency,
        String productName,
        String successUrl,
        String cancelUrl,
        Instant expiresAt) {
}
