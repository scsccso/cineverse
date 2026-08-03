package com.cineverse.backend.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cineverse.backend.ticket.config.TicketProperties;
import com.cineverse.backend.ticket.exception.InvalidTicketCodeException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TicketCodeServiceTest {

    private static final String SECRET = "test-ticket-secret-must-be-at-least-32-bytes-long";

    private TicketCodeService ticketCodeService;

    @BeforeEach
    void setUp() {
        ticketCodeService = new TicketCodeService(new TicketProperties(SECRET));
    }

    @Test
    void signThenVerifyRoundTripsToTheSameBookingId() {
        UUID bookingId = UUID.randomUUID();

        String code = ticketCodeService.sign(bookingId);

        assertThat(ticketCodeService.verify(code)).isEqualTo(bookingId);
    }

    @Test
    void garbageInputIsRejected() {
        assertThatThrownBy(() -> ticketCodeService.verify("not-a-real-ticket-code"))
                .isInstanceOf(InvalidTicketCodeException.class);
    }

    @Test
    void tamperedSignatureIsRejected() {
        String code = ticketCodeService.sign(UUID.randomUUID());
        // Flip the last character of the signature segment — a forged/corrupted code.
        String tampered = code.substring(0, code.length() - 1) + (code.endsWith("a") ? "b" : "a");

        assertThatThrownBy(() -> ticketCodeService.verify(tampered))
                .isInstanceOf(InvalidTicketCodeException.class);
    }

    @Test
    void codeSignedWithADifferentSecretIsRejected() {
        TicketCodeService attackerService = new TicketCodeService(new TicketProperties("a-completely-different-secret-32-bytes-min"));
        String forgedCode = attackerService.sign(UUID.randomUUID());

        assertThatThrownBy(() -> ticketCodeService.verify(forgedCode))
                .isInstanceOf(InvalidTicketCodeException.class);
    }

    @Test
    void aJwtOfTheWrongTypeIsRejectedEvenIfCorrectlySigned() {
        // Simulates cross-token-type confusion — a JWT signed with the same
        // key but missing/wrong "type" claim must not be accepted as a ticket.
        String notATicket = io.jsonwebtoken.Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("type", "something-else")
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> ticketCodeService.verify(notATicket))
                .isInstanceOf(InvalidTicketCodeException.class);
    }
}
