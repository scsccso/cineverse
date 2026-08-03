package com.cineverse.backend.ticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A dedicated signing secret, separate from the JWT access/refresh secrets
 * — same reasoning as those two being separate from each other (see
 * JwtProperties): rotating the ticket secret shouldn't force every session
 * to log out, and vice versa.
 */
@ConfigurationProperties(prefix = "app.ticket")
public record TicketProperties(String signingSecret) {
}
