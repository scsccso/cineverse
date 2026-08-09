package com.cineverse.backend.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cineverse.backend.auth.dto.LoginRequest;
import com.cineverse.backend.auth.dto.RegisterRequest;
import com.cineverse.backend.booking.dto.CreateBookingRequest;
import com.cineverse.backend.cinema.repository.SeatRepository;
import com.cineverse.backend.movie.dto.MovieRequest;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.showtime.dto.CreateShowtimeRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Seat booking (Phase 5) against real Postgres + Redis (Testcontainers):
 * public seat-status polling, the full lock -> cancel -> available-again
 * lifecycle, and ownership enforcement on booking detail/cancel. The
 * concurrency and TTL-expiry guarantees have their own dedicated test class
 * (BookingConcurrencyIntegrationTest) since they're this phase's real
 * acceptance criteria.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookingFlowIntegrationTest {

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

    @Test
    void anonymousCanViewSeatsButNotCreateBooking() throws Exception {
        String adminToken = loginAsAdmin();
        UUID showtimeId = createShowtime(adminToken, "2026-10-01T10:00:00Z");

        mockMvc.perform(get("/api/v1/showtimes/{id}/seats", showtimeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[0].status").value("AVAILABLE"));

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateBookingRequest(showtimeId, List.of(firstSeatId())))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lockingThenCancellingReturnsTheSeatToAvailable() throws Exception {
        String adminToken = loginAsAdmin();
        String customerToken = registerAndLoginCustomer();
        UUID showtimeId = createShowtime(adminToken, "2026-10-02T10:00:00Z");
        UUID seatId = firstSeatId();

        MvcResult createResult = mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(showtimeId, List.of(seatId)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.seats[0].seatId").value(seatId.toString()))
                .andReturn();
        UUID bookingId = readId(createResult);

        mockMvc.perform(get("/api/v1/showtimes/{id}/seats", showtimeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[?(@.seatId=='" + seatId + "')].status").value("LOCKED"));

        mockMvc.perform(delete("/api/v1/bookings/{id}", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/showtimes/{id}/seats", showtimeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[?(@.seatId=='" + seatId + "')].status").value("AVAILABLE"));

        // Already cancelled — cancelling again must not silently succeed.
        mockMvc.perform(delete("/api/v1/bookings/{id}", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyTheOwnerOrAdminCanViewOrCancelABooking() throws Exception {
        String adminToken = loginAsAdmin();
        String ownerToken = registerAndLoginCustomer();
        String strangerToken = registerAndLoginCustomer();
        UUID showtimeId = createShowtime(adminToken, "2026-10-03T10:00:00Z");
        UUID seatId = firstSeatId();

        MvcResult createResult = mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(showtimeId, List.of(seatId)))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID bookingId = readId(createResult);

        mockMvc.perform(get("/api/v1/bookings/{id}", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/bookings/{id}", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/bookings/{id}", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bookings/{id}", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousCannotListBookings() throws Exception {
        mockMvc.perform(get("/api/v1/bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsOnlyTheCallersOwnBookingsNewestFirst() throws Exception {
        String adminToken = loginAsAdmin();
        String ownerToken = registerAndLoginCustomer();
        String strangerToken = registerAndLoginCustomer();
        UUID showtimeId = createShowtime(adminToken, "2026-10-04T10:00:00Z");

        // Sequential HTTP round-trips, so created_at (microsecond precision)
        // strictly increases — the newest-first assertion below isn't racing.
        UUID firstBookingId = createBooking(ownerToken, showtimeId, seatIdAt(0));
        UUID secondBookingId = createBooking(ownerToken, showtimeId, seatIdAt(1));
        UUID strangerBookingId = createBooking(strangerToken, showtimeId, seatIdAt(2));

        mockMvc.perform(get("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(secondBookingId.toString()))
                .andExpect(jsonPath("$[1].id").value(firstBookingId.toString()))
                .andExpect(jsonPath("$[0].showtime.id").value(showtimeId.toString()))
                .andExpect(jsonPath("$[0].seats.length()").value(1));

        mockMvc.perform(get("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(strangerBookingId.toString()));
    }

    /**
     * The list endpoint has no admin override on purpose — unlike GET
     * /bookings/{id}, where an admin can look up a specific customer's booking
     * for support. An unfiltered list would quietly become an all-customers
     * order dump; cross-user data belongs to the Phase 8 admin report
     * endpoints instead.
     */
    @Test
    void adminListingSeesOnlyItsOwnBookingsNotEveryCustomers() throws Exception {
        String adminToken = loginAsAdmin();
        String customerToken = registerAndLoginCustomer();
        UUID showtimeId = createShowtime(adminToken, "2026-10-05T10:00:00Z");

        UUID customerBookingId = createBooking(customerToken, showtimeId, seatIdAt(0));

        MvcResult adminList = mockMvc.perform(get("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(adminList.getResponse().getContentAsString())
                .doesNotContain(customerBookingId.toString());

        // ...while the per-id support lookup still works for an admin.
        mockMvc.perform(get("/api/v1/bookings/{id}", customerBookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
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

    private UUID firstSeatId() {
        return seatIdAt(0);
    }

    private UUID seatIdAt(int index) {
        return seatRepository.findByHallIdOrderByRowLabelAscColumnNumberAsc(UUID.fromString(SEEDED_HALL_ID))
                .get(index)
                .getId();
    }

    private UUID createShowtime(String accessToken, String startTimeIso) throws Exception {
        UUID movieId = createMovie(accessToken, "Booking Flow " + UUID.randomUUID());

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
                                new RegisterRequest(email, password, "Booking Flow Customer"))))
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
