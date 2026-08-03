package com.cineverse.backend.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * No defaults on secretKey/webhookSecret on purpose — unlike JwtProperties'
 * dev-only fallback secrets, a missing Stripe key should fail loudly (any
 * call would otherwise hit Stripe's API as anonymous/unauthenticated and
 * fail with a confusing remote error instead of an obvious local one).
 */
@ConfigurationProperties(prefix = "app.stripe")
public record StripeProperties(String secretKey, String webhookSecret, String currency) {
}
