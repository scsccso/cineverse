package com.cineverse.backend.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.entity.BookingSeat;
import com.cineverse.backend.booking.entity.BookingStatus;
import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.booking.repository.BookingSeatRepository;
import com.cineverse.backend.booking.service.BookingService;
import com.cineverse.backend.booking.service.SeatLockService;
import com.cineverse.backend.cinema.entity.Cinema;
import com.cineverse.backend.cinema.entity.Hall;
import com.cineverse.backend.cinema.entity.Seat;
import com.cineverse.backend.cinema.entity.SeatType;
import com.cineverse.backend.config.FrontendProperties;
import com.cineverse.backend.movie.entity.Movie;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.payment.config.StripeProperties;
import com.cineverse.backend.payment.dto.CheckoutSessionResponse;
import com.cineverse.backend.payment.entity.Payment;
import com.cineverse.backend.payment.entity.PaymentStatus;
import com.cineverse.backend.payment.exception.InvalidStripeSignatureException;
import com.cineverse.backend.payment.gateway.CheckoutSessionSpec;
import com.cineverse.backend.payment.gateway.CreatedCheckoutSession;
import com.cineverse.backend.payment.gateway.StripeCheckoutGateway;
import com.cineverse.backend.payment.repository.PaymentRepository;
import com.cineverse.backend.showtime.entity.Showtime;
import com.cineverse.backend.user.entity.Role;
import com.cineverse.backend.user.entity.User;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pure-Mockito coverage of PaymentService's own logic — the parts that
 * don't need a real database or a real Stripe account: checkout-creation
 * math/wiring, and the webhook dispatcher's idempotency *guard* (does it
 * skip already-terminal payments and leave bookingService alone?). The
 * guard's underlying mechanism (a real Postgres row lock actually
 * serializing two concurrent/duplicate deliveries) and real signature
 * verification are covered by PaymentFlowIntegrationTest instead — neither
 * means anything against a mock.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret_for_unit_tests";

    @Mock
    private BookingService bookingService;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingSeatRepository bookingSeatRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SeatLockService seatLockService;
    @Mock
    private StripeCheckoutGateway stripeCheckoutGateway;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        StripeProperties stripeProperties = new StripeProperties("sk_test_dummy", WEBHOOK_SECRET, "myr");
        FrontendProperties frontendProperties = new FrontendProperties("http://localhost:3000");
        paymentService = new PaymentService(
                bookingService,
                bookingRepository,
                bookingSeatRepository,
                paymentRepository,
                seatLockService,
                stripeCheckoutGateway,
                stripeProperties,
                frontendProperties);
    }

    @Test
    void createCheckoutSessionExtendsHoldAndCreatesPendingPaymentRow() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID showtimeId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        Showtime showtime = showtime(showtimeId, "Interstellar", "Hall 1");
        Booking booking = booking(bookingId, showtime, new BigDecimal("50.00"));
        BookingSeat bookingSeat = new BookingSeat(booking, seat(seatId), new BigDecimal("50.00"));

        when(bookingService.loadPendingBookingForCheckout(userId, bookingId)).thenReturn(booking);
        when(bookingSeatRepository.findByBookingId(bookingId)).thenReturn(List.of(bookingSeat));
        when(stripeCheckoutGateway.createSession(any()))
                .thenReturn(new CreatedCheckoutSession("cs_test_123", "https://checkout.stripe.com/pay/cs_test_123"));

        Instant before = Instant.now();
        CheckoutSessionResponse response = paymentService.createCheckoutSession(userId, bookingId);
        Instant after = Instant.now();

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.com/pay/cs_test_123");

        // Hold extended to 35 minutes (both the DB row and every seat's Redis TTL) — see PaymentService.CHECKOUT_HOLD_DURATION.
        assertThat(booking.getExpiresAt()).isBetween(
                before.plus(PaymentService.CHECKOUT_HOLD_DURATION).minusSeconds(5),
                after.plus(PaymentService.CHECKOUT_HOLD_DURATION).plusSeconds(5));
        verify(bookingRepository).saveAndFlush(booking);
        verify(seatLockService).extend(showtimeId, seatId, PaymentService.CHECKOUT_HOLD_DURATION);

        ArgumentCaptor<CheckoutSessionSpec> specCaptor = ArgumentCaptor.forClass(CheckoutSessionSpec.class);
        verify(stripeCheckoutGateway).createSession(specCaptor.capture());
        CheckoutSessionSpec spec = specCaptor.getValue();
        assertThat(spec.bookingId()).isEqualTo(bookingId);
        assertThat(spec.amount()).isEqualByComparingTo("50.00");
        assertThat(spec.currency()).isEqualTo("myr");
        assertThat(spec.successUrl()).isEqualTo(
                "http://localhost:3000/bookings/" + bookingId + "/confirmed?session_id={CHECKOUT_SESSION_ID}");
        assertThat(spec.cancelUrl()).isEqualTo(
                "http://localhost:3000/showtimes/" + showtimeId + "/seats?bookingId=" + bookingId);
        // Stripe's own 30-minute floor — kept 5 minutes shorter than the internal hold (CHECKOUT_HOLD_DURATION)
        // so Stripe's hosted page can never outlive the seat lock backing it.
        assertThat(spec.expiresAt()).isBetween(
                before.plus(PaymentService.STRIPE_SESSION_DURATION).minusSeconds(5),
                after.plus(PaymentService.STRIPE_SESSION_DURATION).plusSeconds(5));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getStripeSessionId()).isEqualTo("cs_test_123");
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(savedPayment.getAmount()).isEqualByComparingTo("50.00");
        assertThat(savedPayment.getCurrency()).isEqualTo("myr");
    }

    @Test
    void createCheckoutSessionPropagatesRejectionFromBookingServiceWithoutTouchingStripe() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(bookingService.loadPendingBookingForCheckout(userId, bookingId))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, "Booking is EXPIRED, cannot start checkout"));

        assertThatThrownBy(() -> paymentService.createCheckoutSession(userId, bookingId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        verify(stripeCheckoutGateway, never()).createSession(any());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void webhookRejectsInvalidSignatureWithoutTouchingRepositories() {
        String payload = checkoutSessionCompletedPayload("cs_test_bad_sig", "pi_test_1");
        String badSignature = sign(payload, "whsec_a_totally_different_secret");

        assertThatThrownBy(() -> paymentService.handleWebhookEvent(payload, badSignature))
                .isInstanceOf(InvalidStripeSignatureException.class);

        verify(paymentRepository, never()).findByStripeSessionIdForUpdate(anyString());
        verify(bookingService, never()).confirmIfPending(any());
    }

    @Test
    void checkoutSessionCompletedConfirmsBookingWhenPaymentWasStillPending() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, showtime(UUID.randomUUID(), "Dune", "Hall 2"), new BigDecimal("30.00"));
        Payment payment = new Payment(booking, "cs_test_ok", new BigDecimal("30.00"), "myr");
        when(paymentRepository.findByStripeSessionIdForUpdate("cs_test_ok")).thenReturn(Optional.of(payment));
        when(bookingService.confirmIfPending(bookingId)).thenReturn(true);

        String payload = checkoutSessionCompletedPayload("cs_test_ok", "pi_test_ok");
        String signature = sign(payload, WEBHOOK_SECRET);

        paymentService.handleWebhookEvent(payload, signature);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getStripePaymentIntentId()).isEqualTo("pi_test_ok");
        verify(paymentRepository).saveAndFlush(payment);
        verify(bookingService).confirmIfPending(bookingId);
    }

    @Test
    void checkoutSessionCompletedIsANoOpWhenAlreadySucceeded() {
        Booking booking = booking(UUID.randomUUID(), showtime(UUID.randomUUID(), "Dune", "Hall 2"), new BigDecimal("30.00"));
        Payment alreadyProcessed = new Payment(booking, "cs_test_dup", new BigDecimal("30.00"), "myr");
        alreadyProcessed.markSucceeded("pi_original");
        when(paymentRepository.findByStripeSessionIdForUpdate("cs_test_dup")).thenReturn(Optional.of(alreadyProcessed));

        String payload = checkoutSessionCompletedPayload("cs_test_dup", "pi_test_dup_retry");
        String signature = sign(payload, WEBHOOK_SECRET);

        // Simulates Stripe redelivering the same (or a duplicate) event for a session already marked SUCCEEDED.
        paymentService.handleWebhookEvent(payload, signature);

        assertThat(alreadyProcessed.getStripePaymentIntentId()).isEqualTo("pi_original");
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(bookingService, never()).confirmIfPending(any());
    }

    @Test
    void checkoutSessionExpiredMarksPaymentFailedWithoutTouchingBooking() {
        Booking booking = booking(UUID.randomUUID(), showtime(UUID.randomUUID(), "Dune", "Hall 2"), new BigDecimal("30.00"));
        Payment payment = new Payment(booking, "cs_test_expired", new BigDecimal("30.00"), "myr");
        when(paymentRepository.findByStripeSessionIdForUpdate("cs_test_expired")).thenReturn(Optional.of(payment));

        String payload = checkoutSessionExpiredPayload("cs_test_expired");
        String signature = sign(payload, WEBHOOK_SECRET);

        paymentService.handleWebhookEvent(payload, signature);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).saveAndFlush(payment);
        verify(bookingService, never()).confirmIfPending(any());
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void unrecognizedEventTypeIsANoOp() {
        String payload = """
                {
                  "id": "evt_test_unhandled",
                  "object": "event",
                  "api_version": "2025-01-01.acacia",
                  "created": 1700000000,
                  "type": "payment_intent.created",
                  "data": { "object": { "id": "pi_irrelevant", "object": "payment_intent" } }
                }
                """;
        String signature = sign(payload, WEBHOOK_SECRET);

        paymentService.handleWebhookEvent(payload, signature);

        verify(paymentRepository, never()).findByStripeSessionIdForUpdate(anyString());
        verify(bookingService, never()).confirmIfPending(any());
    }

    private Booking booking(UUID id, Showtime showtime, BigDecimal totalPrice) {
        User user = new User("customer@example.com", "hash", Role.CUSTOMER, "Test Customer");
        Booking booking = new Booking(user, showtime, totalPrice, Instant.now().plus(Duration.ofMinutes(5)));
        ReflectionTestUtils.setField(booking, "id", id);
        return booking;
    }

    private Showtime showtime(UUID id, String movieTitle, String hallName) {
        Movie movie = new Movie();
        movie.setTitle(movieTitle);
        movie.setDurationMinutes(120);
        movie.setStatus(MovieStatus.NOW_PLAYING);
        Cinema cinema = new Cinema("CineVerse Downtown", "1 Main St");
        Hall hall = new Hall(cinema, hallName, 6, 10);
        Showtime showtime = new Showtime(movie, hall, Instant.now(), Instant.now().plus(Duration.ofHours(2)), new BigDecimal("25.00"));
        ReflectionTestUtils.setField(showtime, "id", id);
        return showtime;
    }

    private Seat seat(UUID id) {
        Cinema cinema = new Cinema("CineVerse Downtown", "1 Main St");
        Hall hall = new Hall(cinema, "Hall 1", 6, 10);
        Seat seat = new Seat(hall, "A", 1, SeatType.STANDARD);
        ReflectionTestUtils.setField(seat, "id", id);
        return seat;
    }

    private String checkoutSessionCompletedPayload(String sessionId, String paymentIntentId) {
        return """
                {
                  "id": "evt_test_%s",
                  "object": "event",
                  "api_version": "2025-01-01.acacia",
                  "created": 1700000000,
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "checkout.session",
                      "payment_intent": "%s"
                    }
                  }
                }
                """.formatted(UUID.randomUUID(), sessionId, paymentIntentId);
    }

    private String checkoutSessionExpiredPayload(String sessionId) {
        return """
                {
                  "id": "evt_test_%s",
                  "object": "event",
                  "api_version": "2025-01-01.acacia",
                  "created": 1700000000,
                  "type": "checkout.session.expired",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "checkout.session"
                    }
                  }
                }
                """.formatted(UUID.randomUUID(), sessionId);
    }

    /** Stripe's own documented webhook signing scheme: {@code t=<timestamp>,v1=hex(HMAC-SHA256(secret, "<timestamp>.<payload>"))}. */
    private String sign(String payload, String secret) {
        try {
            long timestamp = Instant.now().getEpochSecond();
            String signedPayload = timestamp + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
