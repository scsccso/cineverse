package com.cineverse.backend.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cineverse.backend.auth.dto.LoginRequest;
import com.cineverse.backend.auth.dto.RegisterRequest;
import com.cineverse.backend.booking.dto.CreateBookingRequest;
import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.entity.BookingStatus;
import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.booking.repository.BookingSeatRepository;
import com.cineverse.backend.cinema.repository.SeatRepository;
import com.cineverse.backend.movie.dto.MovieRequest;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.showtime.dto.CreateShowtimeRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This phase's real acceptance criteria: the Redis seat lock must be
 * genuinely atomic under real concurrent requests, and lazy expiry must
 * actually work. If either test here is ever flaky (sometimes both requests
 * succeed, or both fail), the lock is not atomic and must be fixed — this
 * is not a test to relax or retry-loop around.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookingConcurrencyIntegrationTest {

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
    private BookingSeatRepository bookingSeatRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void exactlyOneOfTwoConcurrentRequestsForTheSameSeatSucceeds() throws Exception {
        String adminToken = loginAsAdmin();
        String customerAToken = registerAndLoginCustomer();
        String customerBToken = registerAndLoginCustomer();
        UUID showtimeId = createShowtime(adminToken, "2026-11-01T10:00:00Z");
        UUID seatId = firstSeatId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<MvcResult> requestA = raceRequest(customerAToken, showtimeId, seatId, bothReady, go);
        Callable<MvcResult> requestB = raceRequest(customerBToken, showtimeId, seatId, bothReady, go);

        try {
            Future<MvcResult> futureA = executor.submit(requestA);
            Future<MvcResult> futureB = executor.submit(requestB);

            // Wait until both threads are parked right before the real
            // call, then release them at (as close to) the same instant.
            bothReady.await(10, TimeUnit.SECONDS);
            go.countDown();

            MvcResult resultA = futureA.get(15, TimeUnit.SECONDS);
            MvcResult resultB = futureB.get(15, TimeUnit.SECONDS);

            int statusA = resultA.getResponse().getStatus();
            int statusB = resultB.getResponse().getStatus();

            assertThat(List.of(statusA, statusB))
                    .as("exactly one request must succeed (201) and the other must be rejected (409); "
                            + "both-succeed or both-fail means the Redis lock is not atomic")
                    .containsExactlyInAnyOrder(201, 409);

            MvcResult failedResult = statusA == 409 ? resultA : resultB;
            String failedBody = failedResult.getResponse().getContentAsString();
            assertThat(failedBody).contains(seatId.toString());

            // The failed request must not have left any trace in the DB.
            List<Booking> bookingsForShowtime =
                    bookingRepository.findByShowtimeIdAndStatusIn(showtimeId, List.of(BookingStatus.PENDING));
            assertThat(bookingsForShowtime).hasSize(1);
            assertThat(bookingSeatRepository.findByBookingId(bookingsForShowtime.get(0).getId()))
                    .hasSize(1)
                    .allSatisfy(bookingSeat -> assertThat(bookingSeat.getSeat().getId()).isEqualTo(seatId));
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<MvcResult> raceRequest(
            String accessToken, UUID showtimeId, UUID seatId, CountDownLatch bothReady, CountDownLatch go) {
        return () -> {
            bothReady.countDown();
            go.await();
            return mockMvc.perform(post("/api/v1/bookings")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new CreateBookingRequest(showtimeId, List.of(seatId)))))
                    .andReturn();
        };
    }

    @Test
    void aPendingBookingPastItsHoldWindowIsLazilyExpiredAndFreesTheSeat() throws Exception {
        String adminToken = loginAsAdmin();
        String customerToken = registerAndLoginCustomer();
        UUID showtimeId = createShowtime(adminToken, "2026-11-02T10:00:00Z");
        UUID seatId = firstSeatId();

        MvcResult createResult = mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(showtimeId, List.of(seatId)))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID bookingId = readId(createResult);

        mockMvc.perform(get("/api/v1/showtimes/{id}/seats", showtimeId))
                .andExpect(jsonPath("$.seats[?(@.seatId=='" + seatId + "')].status").value("LOCKED"));

        forceExpiresAtIntoThePast(bookingId);

        mockMvc.perform(get("/api/v1/showtimes/{id}/seats", showtimeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[?(@.seatId=='" + seatId + "')].status").value("AVAILABLE"));

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.EXPIRED);
    }

    private void forceExpiresAtIntoThePast(UUID bookingId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createQuery("UPDATE Booking b SET b.expiresAt = :expiresAt WHERE b.id = :id")
                        .setParameter("expiresAt", Instant.now().minus(Duration.ofMinutes(10)))
                        .setParameter("id", bookingId)
                        .executeUpdate());
    }

    private UUID firstSeatId() {
        return seatRepository.findByHallIdOrderByRowLabelAscColumnNumberAsc(UUID.fromString(SEEDED_HALL_ID))
                .get(0)
                .getId();
    }

    private UUID createShowtime(String accessToken, String startTimeIso) throws Exception {
        UUID movieId = createMovie(accessToken, "Concurrency Test " + UUID.randomUUID());

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
                                new RegisterRequest(email, password, "Concurrency Test Customer"))))
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
