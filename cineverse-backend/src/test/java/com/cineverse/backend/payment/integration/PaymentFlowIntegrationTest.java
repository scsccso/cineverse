package com.cineverse.backend.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cineverse.backend.auth.dto.LoginRequest;
import com.cineverse.backend.auth.dto.RegisterRequest;
import com.cineverse.backend.booking.dto.CreateBookingRequest;
import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.entity.BookingStatus;
import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.cinema.repository.SeatRepository;
import com.cineverse.backend.movie.dto.MovieRequest;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.payment.entity.Payment;
import com.cineverse.backend.payment.entity.PaymentStatus;
import com.cineverse.backend.payment.gateway.CreatedCheckoutSession;
import com.cineverse.backend.payment.gateway.StripeCheckoutGateway;
import com.cineverse.backend.payment.repository.PaymentRepository;
import com.cineverse.backend.showtime.dto.CreateShowtimeRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 6 acceptance criteria against real Postgres + Redis (Testcontainers)
 * and the real Stripe SDK signature-verification/deserialization code path.
 * Only StripeCheckoutGateway is mocked — the one operation that genuinely
 * needs a live Stripe account (see StripeCheckoutGateway's javadoc); webhook
 * signing/verification here uses Stripe's real published HMAC scheme against
 * a webhook secret this test controls, not a stand-in for it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaymentFlowIntegrationTest {

    private static final String SEEDED_HALL_ID = "21111111-1111-1111-1111-111111111111"; // Hall 1, from V6 seed
    private static final String WEBHOOK_SECRET = "whsec_integration_test_secret";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.security.cookie-secure", () -> "false");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("app.stripe.webhook-secret", () -> WEBHOOK_SECRET);
        registry.add("app.stripe.secret-key", () -> "sk_test_not_actually_called_because_gateway_is_mocked");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private StripeCheckoutGateway stripeCheckoutGateway;

    @Test
    void checkoutCreatesStripeSessionAndExtendsTheHold() throws Exception {
        String adminToken = loginAsAdmin();
        String customerToken = registerAndLoginCustomer();
        UUID showtimeId = createShowtime(adminToken, "2026-12-01T10:00:00Z");
        UUID bookingId = createPendingBooking(customerToken, showtimeId);
        String stripeSessionId = "cs_test_" + UUID.randomUUID();
        String checkoutUrl = "https://checkout.stripe.com/pay/" + stripeSessionId;
        when(stripeCheckoutGateway.createSession(any()))
                .thenReturn(new CreatedCheckoutSession(stripeSessionId, checkoutUrl));

        Instant before = Instant.now();
        mockMvc.perform(post("/api/v1/bookings/{id}/checkout", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value(checkoutUrl));

        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        // Extended well past Phase 5's original 5-minute hold — see
        // PaymentService.CHECKOUT_HOLD_DURATION (35 min) for the exact value
        // and why (Stripe Checkout Sessions can't expire in under 30 min).
        assertThat(booking.getExpiresAt()).isAfter(before.plus(Duration.ofMinutes(30)));

        List<Payment> payments = paymentRepository.findAll().stream()
                .filter(p -> p.getBooking().getId().equals(bookingId))
                .toList();
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getStripeSessionId()).isEqualTo(stripeSessionId);
        assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void checkoutRejectsSomeoneElsesBooking() throws Exception {
        String adminToken = loginAsAdmin();
        String ownerToken = registerAndLoginCustomer();
        String strangerToken = registerAndLoginCustomer();
        UUID showtimeId = createShowtime(adminToken, "2026-12-02T10:00:00Z");
        UUID bookingId = createPendingBooking(ownerToken, showtimeId);

        mockMvc.perform(post("/api/v1/bookings/{id}/checkout", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void validSignedWebhookConfirmsBookingAndIsIdempotentOnRedelivery() throws Exception {
        String adminToken = loginAsAdmin();
        String customerToken = registerAndLoginCustomer();
        UUID showtimeId = createShowtime(adminToken, "2026-12-03T10:00:00Z");
        UUID bookingId = createPendingBooking(customerToken, showtimeId);
        String stripeSessionId = "cs_test_" + UUID.randomUUID();
        when(stripeCheckoutGateway.createSession(any()))
                .thenReturn(new CreatedCheckoutSession(
                        stripeSessionId, "https://checkout.stripe.com/pay/" + stripeSessionId));
        mockMvc.perform(post("/api/v1/bookings/{id}/checkout", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk());

        String payload = checkoutSessionCompletedPayload(stripeSessionId, "pi_test_" + UUID.randomUUID());
        String signature = sign(payload, WEBHOOK_SECRET);

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signature)
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
        Payment payment = paymentRepository.findAll().stream()
                .filter(p -> p.getStripeSessionId().equals(stripeSessionId))
                .findFirst()
                .orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        // Stripe redelivering the exact same event (e.g. after a network
        // blip on our first 200) must not double-process: still one payment
        // row, booking stays CONFIRMED, no error.
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signature)
                        .content(payload))
                .andExpect(status().isOk());

        long paymentRowsForThisSession = paymentRepository.findAll().stream()
                .filter(p -> p.getStripeSessionId().equals(stripeSessionId))
                .count();
        assertThat(paymentRowsForThisSession).isEqualTo(1);
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void webhookWithBadSignatureIsRejectedAndChangesNothing() throws Exception {
        String payload = checkoutSessionCompletedPayload("cs_test_irrelevant", "pi_test_irrelevant");
        String badSignature = sign(payload, "whsec_wrong_secret");

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", badSignature)
                        .content(payload))
                .andExpect(status().isBadRequest());

        assertThat(paymentRepository.findAll().stream()
                        .anyMatch(p -> p.getStripeSessionId().equals("cs_test_irrelevant")))
                .isFalse();
    }

    private UUID createPendingBooking(String customerToken, UUID showtimeId) throws Exception {
        UUID seatId = seatRepository
                .findByHallIdOrderByRowLabelAscColumnNumberAsc(UUID.fromString(SEEDED_HALL_ID))
                .get(0)
                .getId();
        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(showtimeId, List.of(seatId)))))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
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

    /** Stripe's own documented webhook signing scheme: {@code t=<timestamp>,v1=hex(HMAC-SHA256(secret, "<timestamp>.<payload>"))}. */
    private String sign(String payload, String secret) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hash);
    }

    private UUID createShowtime(String accessToken, String startTimeIso) throws Exception {
        UUID movieId = createMovie(accessToken, "Payment Flow " + UUID.randomUUID());

        MvcResult result = mockMvc.perform(post("/api/v1/showtimes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShowtimeRequest(
                                movieId, UUID.fromString(SEEDED_HALL_ID),
                                Instant.parse(startTimeIso), new BigDecimal("25.00")))))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private UUID createMovie(String accessToken, String title) throws Exception {
        MovieRequest request = new MovieRequest(
                title, "desc", "tagline", 100, "PG", null, null, MovieStatus.NOW_PLAYING, Set.of());

        MvcResult result = mockMvc.perform(post("/api/v1/movies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin@cineverse.local", "Admin@12345"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String registerAndLoginCustomer() throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@cineverse.local";
        String password = "Sup3rSecret!";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, password, "Payment Flow Customer"))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private UUID readId(MvcResult result) throws Exception {
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        return UUID.fromString(id);
    }
}
