package com.cineverse.backend.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Blank default on purpose, in every profile including prod — same pattern
 * as {@code TmdbProperties}, not {@code StripeProperties}: an unconfigured
 * demo-reset secret isn't a misconfiguration, it's the correct steady state
 * for a deployment that isn't running scheduled demo resets at all (which
 * most won't — this only matters for a public, unauthenticated-visitor demo
 * deployment, see docs/DEPLOYMENT.md). DemoResetService checks this for
 * blank explicitly and returns 503 rather than letting a blank secret ever
 * participate in the actual comparison against a caller-supplied header.
 */
@ConfigurationProperties(prefix = "app.demo-reset")
public record DemoResetProperties(String secret) {
}
