package com.cineverse.backend.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cineverse.backend.auth.dto.LoginRequest;
import com.cineverse.backend.auth.dto.RegisterRequest;
import com.cineverse.backend.booking.dto.CreateBookingRequest;
import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.cinema.repository.SeatRepository;
import com.cineverse.backend.movie.dto.MovieRequest;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.payment.entity.Payment;
import com.cineverse.backend.payment.repository.PaymentRepository;
import com.cineverse.backend.showtime.dto.CreateShowtimeRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * GET /api/v1/admin/payments against real Postgres + Redis (Testcontainers).
 * There is no API path that produces an ORPHANED_SUCCESS payment (that
 * transition only happens inside PaymentService's real Stripe webhook
 * handling, already covered by PaymentFlowIntegrationTest) — this test
 * isn't re-testing that transition, only the search query and its HTTP
 * gating, so the fixture is built by autowiring BookingRepository/
 * PaymentRepository directly and calling the same entity methods the real
 * webhook path calls, matching the precedent already set by
 * ShowtimeAdminFlowIntegrationTest's bookedSeats test (constructed CONFIRMED/
 * PENDING bookings directly rather than driving a full Stripe checkout).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminPaymentFlowIntegrationTest {

    private static final String SEEDED_HALL_ID = "21111111-1111-1111-1111-111111111111"; // Hall 1, from V6 seed

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

    @Test
    void anonymousCannotSearch() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotSearch() throws Exception {
        String customerToken = registerAndLoginCustomer();
        mockMvc.perform(get("/api/v1/admin/payments").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void findsAnOrphanedSuccessPaymentWithItsFullBookingContext() throws Exception {
        String adminToken = loginAsAdmin();
        String customerEmail = "payment-e2e-" + UUID.randomUUID() + "@cineverse.local";
        String customerToken = registerAndLoginCustomer(customerEmail);
        String movieTitle = "Payment Search Test " + UUID.randomUUID();
        UUID showtimeId = createShowtime(adminToken, movieTitle, "2026-12-01T10:00:00Z");
        UUID seatId = seatIdAt(0);

        UUID bookingId = createBooking(customerToken, showtimeId, seatId);
        // Not reachable via any API: mirrors the real webhook transition
        // (PaymentService.handleCheckoutSessionCompleted marking a payment
        // ORPHANED_SUCCESS when its booking is no longer PENDING), see this
        // class's doc comment for why this is constructed directly.
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.markExpired();
        bookingRepository.saveAndFlush(booking);

        Payment payment = new Payment(booking, "cs_test_" + UUID.randomUUID(), new BigDecimal("25.00"), "myr");
        payment.markOrphanedSuccess("pi_test_" + UUID.randomUUID());
        // saveAndFlush: Payment has @CreationTimestamp/@UpdateTimestamp.
        paymentRepository.saveAndFlush(payment);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/payments")
                        .param("status", "ORPHANED_SUCCESS")
                        .param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        // Found by this payment's own id, not an exact count/position — the
        // Postgres container is shared across every test method in this
        // class (and potentially other ORPHANED_SUCCESS fixtures from a
        // parallel run), same reasoning as AdminBookingFlowIntegrationTest.
        JsonNode row = findByPaymentId(result, payment.getId());
        assertThat(row).as("payment %s present in the ORPHANED_SUCCESS results", payment.getId()).isNotNull();
        assertThat(row.get("status").asText()).isEqualTo("ORPHANED_SUCCESS");
        assertThat(row.get("amount").decimalValue()).isEqualByComparingTo("25.00");
        assertThat(row.get("stripeSessionId").asText()).isEqualTo(payment.getStripeSessionId());
        assertThat(row.get("booking").get("userEmail").asText()).isEqualTo(customerEmail);
        assertThat(row.get("booking").get("movieTitle").asText()).isEqualTo(movieTitle);
        assertThat(row.get("booking").get("status").asText()).isEqualTo("EXPIRED");
    }

    @Test
    void statusFilterExcludesOtherStatuses() throws Exception {
        String adminToken = loginAsAdmin();
        String customerToken = registerAndLoginCustomer("payment-filter-" + UUID.randomUUID() + "@cineverse.local");
        UUID showtimeId = createShowtime(adminToken, "2026-12-02T10:00:00Z");
        UUID bookingId = createBooking(customerToken, showtimeId, seatIdAt(1));

        Payment failed = new Payment(
                bookingRepository.findById(bookingId).orElseThrow(),
                "cs_test_" + UUID.randomUUID(),
                new BigDecimal("25.00"),
                "myr");
        failed.markFailed();
        paymentRepository.saveAndFlush(failed);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/payments")
                        .param("status", "ORPHANED_SUCCESS")
                        .param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(findByPaymentId(result, failed.getId()))
                .as("a FAILED payment must not appear in an ORPHANED_SUCCESS search")
                .isNull();
    }

    private JsonNode findByPaymentId(MvcResult result, UUID paymentId) throws Exception {
        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        for (JsonNode row : content) {
            if (row.get("id").asText().equals(paymentId.toString())) {
                return row;
            }
        }
        return null;
    }

    private UUID createBooking(String accessToken, UUID showtimeId, UUID seatId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(showtimeId, List.of(seatId)))))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private UUID seatIdAt(int index) {
        return seatRepository.findByHallIdOrderByRowLabelAscColumnNumberAsc(UUID.fromString(SEEDED_HALL_ID))
                .get(index)
                .getId();
    }

    private UUID createShowtime(String accessToken, String startTimeIso) throws Exception {
        return createShowtime(accessToken, "Admin Payment Search " + UUID.randomUUID(), startTimeIso);
    }

    private UUID createShowtime(String accessToken, String movieTitle, String startTimeIso) throws Exception {
        UUID movieId = createMovie(accessToken, movieTitle);

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
        return registerAndLoginCustomer("payment-flow-" + UUID.randomUUID() + "@cineverse.local");
    }

    private String registerAndLoginCustomer(String email) throws Exception {
        String password = "Sup3rSecret!";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, password, "Admin Payment Search Customer"))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private UUID readId(MvcResult result) throws Exception {
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
}
