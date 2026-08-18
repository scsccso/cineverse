package com.cineverse.backend.demo.integration;

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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * /internal/demo-reset/** against real Postgres + Redis (Testcontainers)
 * with app.demo-reset.secret configured — secret gating (wrong/missing
 * header) and the two reset operations' real effects. The "not configured
 * at all" 503 scenario is deliberately a separate test class
 * (DemoResetDisabledFlowIntegrationTest) — needs a Spring context with a
 * blank secret, which this class's context (secret always set) can't also
 * cover.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DemoResetFlowIntegrationTest {

    private static final String SECRET_HEADER = "X-Demo-Reset-Secret";
    private static final String CONFIGURED_SECRET = "integration-test-demo-reset-secret";
    private static final ZoneOffset CINEMA_OFFSET = ZoneOffset.of("+08:00");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.security.cookie-secure", () -> "false");
        registry.add("app.demo-reset.secret", () -> CONFIGURED_SECRET);
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

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void missingSecretHeaderIsRejectedWithUnauthorized() throws Exception {
        mockMvc.perform(post("/internal/demo-reset/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSecretIsRejectedWithUnauthorized() throws Exception {
        mockMvc.perform(post("/internal/demo-reset/showtimes")
                        .header(SECRET_HEADER, "not-the-real-secret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resettingTransactionsClearsBookingsPaymentsAndSeatLocksButLeavesShowtimeAndMovieUntouched() throws Exception {
        String adminToken = loginAsAdmin();
        String customerEmail = "demo-reset-txn-" + UUID.randomUUID() + "@cineverse.local";
        String customerToken = registerAndLoginCustomer(customerEmail);

        UUID movieId = createMovie(adminToken, "Demo Reset Txn Movie " + UUID.randomUUID());
        UUID showtimeId = createShowtime(adminToken, movieId, "2026-09-20T10:00:00Z");
        UUID seatId = seatIdAt(0);

        UUID bookingId = createBooking(customerToken, showtimeId, seatId);
        confirmBookingWithPayment(bookingId);

        // An orphaned lock: the real booking flow's own lock either already
        // exists or was released on confirm — set explicitly so this
        // assertion doesn't depend on inferring that internal timing.
        String seatLockKey = "seat-lock:%s:%s".formatted(showtimeId, seatId);
        redisTemplate.opsForValue().set(seatLockKey, "some-holder-id");
        assertThat(redisTemplate.hasKey(seatLockKey)).isTrue();

        MvcResult result = mockMvc.perform(post("/internal/demo-reset/transactions")
                        .header(SECRET_HEADER, CONFIGURED_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showtimesDeleted").value(0))
                .andExpect(jsonPath("$.showtimesCreated").value(0))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("bookingsCleared").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(body.get("paymentsCleared").asInt()).isGreaterThanOrEqualTo(1);

        assertThat(bookingRepository.findById(bookingId)).isEmpty();
        assertThat(redisTemplate.hasKey(seatLockKey)).isFalse();

        // Not touched by .../transactions.
        mockMvc.perform(get("/api/v1/showtimes/{id}", showtimeId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/movies/{id}", movieId)).andExpect(status().isOk());
    }

    @Test
    void resettingShowtimesClearsTransactionsDeletesAndRegeneratesShowtimesButLeavesMoviesUntouched() throws Exception {
        String adminToken = loginAsAdmin();
        String customerEmail = "demo-reset-showtimes-" + UUID.randomUUID() + "@cineverse.local";
        String customerToken = registerAndLoginCustomer(customerEmail);

        // Our own NOW_PLAYING movie — guarantees regenerateShowtimes() has
        // at least one movie to assign slots to regardless of what status
        // the ambient seed movies happen to be in at test time.
        String testMovieTitle = "Demo Reset Showtime Movie " + UUID.randomUUID();
        UUID testMovieId = createMovie(adminToken, testMovieTitle);

        // An "old" showtime that must not survive the reset, with a real
        // booking still attached — the actual proof that clearing
        // transactional data before deleting showtimes is load-bearing, not
        // cosmetic: without it, this delete would violate showtimes' RESTRICT
        // FK from bookings and the whole request would fail with a raw
        // constraint-violation 500 instead of succeeding.
        UUID oldShowtimeId = createShowtime(adminToken, testMovieId, "2026-09-21T10:00:00Z");
        UUID seatId = seatIdAt(1);
        UUID bookingId = createBooking(customerToken, oldShowtimeId, seatId);
        confirmBookingWithPayment(bookingId);

        int hallCount = hallCount(adminToken);
        JsonNode moviesBefore = listAllMovies();
        int movieCountBefore = moviesBefore.get("totalElements").asInt();

        MvcResult result = mockMvc.perform(post("/internal/demo-reset/showtimes")
                        .header(SECRET_HEADER, CONFIGURED_SECRET))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        int expectedCreated = hallCount * 3 * 7;
        assertThat(body.get("showtimesDeleted").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(body.get("showtimesCreated").asInt()).isEqualTo(expectedCreated);
        assertThat(body.get("bookingsCleared").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(body.get("paymentsCleared").asInt()).isGreaterThanOrEqualTo(1);

        // Old showtime and its booking are gone.
        mockMvc.perform(get("/api/v1/showtimes/{id}", oldShowtimeId)).andExpect(status().isNotFound());
        assertThat(bookingRepository.findById(bookingId)).isEmpty();

        // Movie catalog is untouched — same total count, our own test movie
        // (and a real seed movie) still resolve by id/title.
        JsonNode moviesAfter = listAllMovies();
        assertThat(moviesAfter.get("totalElements").asInt()).isEqualTo(movieCountBefore);
        mockMvc.perform(get("/api/v1/movies/{id}", testMovieId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(testMovieTitle));

        // Fresh showtimes actually landed on our test movie, on dates within
        // [today, today+7) cinema-local time.
        MvcResult newShowtimesResult = mockMvc.perform(get("/api/v1/showtimes").param("movieId", testMovieId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode newShowtimes = objectMapper.readTree(newShowtimesResult.getResponse().getContentAsString());
        assertThat(newShowtimes).isNotEmpty();
        LocalDate today = OffsetDateTime.now(CINEMA_OFFSET).toLocalDate();
        LocalDate windowEnd = today.plusDays(7);
        for (JsonNode showtime : newShowtimes) {
            Instant startTime = Instant.parse(showtime.get("startTime").asText());
            LocalDate localDate = startTime.atOffset(CINEMA_OFFSET).toLocalDate();
            assertThat(localDate).isBetween(today, windowEnd.minusDays(1));
        }
    }

    private void confirmBookingWithPayment(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.markConfirmed();
        bookingRepository.saveAndFlush(booking);

        Payment payment = new Payment(booking, "cs_test_" + UUID.randomUUID(), new BigDecimal("25.00"), "myr");
        payment.markSucceeded("pi_test_" + UUID.randomUUID());
        paymentRepository.saveAndFlush(payment);
    }

    private int hallCount(String accessToken) throws Exception {
        MvcResult cinemasResult = mockMvc.perform(get("/api/v1/cinemas"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode cinemas = objectMapper.readTree(cinemasResult.getResponse().getContentAsString());
        String cinemaId = cinemas.get(0).get("id").asText();

        MvcResult hallsResult = mockMvc.perform(get("/api/v1/cinemas/{id}/halls", cinemaId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(hallsResult.getResponse().getContentAsString()).size();
    }

    private JsonNode listAllMovies() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/movies").param("size", "200"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
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

    private UUID seatIdAt(int index) throws Exception {
        // Hall 1 from V6 seed — same fixture id every showtime/showtime-admin
        // integration test in this codebase already relies on.
        return seatRepository.findByHallIdOrderByRowLabelAscColumnNumberAsc(
                        UUID.fromString("21111111-1111-1111-1111-111111111111"))
                .get(index)
                .getId();
    }

    private UUID createShowtime(String accessToken, UUID movieId, String startTimeIso) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/showtimes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShowtimeRequest(
                                movieId, UUID.fromString("21111111-1111-1111-1111-111111111111"),
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

    private String registerAndLoginCustomer(String email) throws Exception {
        String password = "Sup3rSecret!";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, password, "Demo Reset Test Customer"))))
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
