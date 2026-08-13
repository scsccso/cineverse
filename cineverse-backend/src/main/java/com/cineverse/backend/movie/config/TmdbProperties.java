package com.cineverse.backend.movie.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Blank default on purpose — unlike {@code StripeProperties} (no default,
 * lets Stripe's own API reject an empty key remotely), TMDB search is a
 * convenience prefill for movie creation, not a payment-critical path, and
 * the admin form already has a manual-entry fallback. A blank key is checked
 * explicitly in {@code TmdbGatewayImpl} before any network call, so "not
 * configured" is a clean, immediate, local error instead of a confusing one
 * from a remote 401.
 */
@ConfigurationProperties(prefix = "app.tmdb")
public record TmdbProperties(String apiKey) {
}
