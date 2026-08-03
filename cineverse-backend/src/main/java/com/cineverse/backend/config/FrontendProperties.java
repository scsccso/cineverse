package com.cineverse.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The single canonical frontend origin used to build redirect URLs (Stripe
 * Checkout success_url/cancel_url) — distinct from CorsProperties, which is
 * an allowlist of origins permitted to call this API, not a URL to redirect
 * a browser to.
 */
@ConfigurationProperties(prefix = "app.frontend")
public record FrontendProperties(String baseUrl) {
}
