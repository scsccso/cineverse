package com.cineverse.backend.ticket.service;

import com.cineverse.backend.ticket.config.TicketProperties;
import com.cineverse.backend.ticket.exception.InvalidTicketCodeException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Signs and verifies e-ticket codes — reuses the same jjwt library and
 * pattern as JwtService (a signed, tamper-evident token; not encryption,
 * since the booking id inside isn't secret, only forgeable-ness is the
 * concern) rather than hand-rolling HMAC/base64 concatenation. A "type"
 * claim keeps a ticket code from ever being accepted where an access/refresh
 * token is expected or vice versa, even though they're signed with
 * different secrets anyway (defense in depth, same reasoning as
 * JwtService's own access/refresh separation).
 *
 * <p>Deliberately unbounded validity (no exp claim) — this Phase's scope is
 * "was this ticket paid for and not yet used", not "is it currently
 * showtime". Not encoded: showtime timing gates, if ever wanted, are a
 * separate concern layered on top, not part of what makes a code
 * forge-proof.
 */
@Service
public class TicketCodeService {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_TICKET = "ticket";

    private final SecretKey key;

    public TicketCodeService(TicketProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.signingSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String sign(UUID bookingId) {
        return Jwts.builder()
                .subject(bookingId.toString())
                .claim(CLAIM_TYPE, TYPE_TICKET)
                .issuedAt(Date.from(Instant.now()))
                .signWith(key)
                .compact();
    }

    /** @throws InvalidTicketCodeException if the code is malformed, tampered with, or not a ticket code */
    public UUID verify(String ticketCode) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(ticketCode).getPayload();
            if (!TYPE_TICKET.equals(claims.get(CLAIM_TYPE, String.class))) {
                throw new InvalidTicketCodeException("Not a valid ticket code");
            }
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTicketCodeException("Invalid or tampered ticket code", e);
        }
    }
}
