package com.cineverse.backend.booking.integration;

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
 * GET /api/v1/admin/bookings against real Postgres + Redis (Testcontainers) —
 * the thing AdminBookingServiceTest's mocked-repository coverage can't prove
 * is that the route is actually gated to ADMIN over real HTTP, and that the
 * JPQL filters in BookingRepository.search really do filter (a typo'd
 * `where` clause would pass every unit test, which only checks that
 * whatever the mock returns gets assembled correctly).
 *
 * <p>Each test creates its own showtime on a distinct date on the seeded
 * hall (see BookingFlowIntegrationTest) to stay clear of the 20-minute
 * changeover conflict rule, and scopes its assertions with a filter unique
 * to that test (a fresh random email, or a fresh random movie title) rather
 * than asserting an exact result count against the whole table — the
 * Postgres container is shared across every test method in this class, so
 * other tests' bookings are always present too.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminBookingFlowIntegrationTest {

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
    void anonymousCannotSearch() throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotSearch() throws Exception {
        String customerToken = registerAndLoginCustomer("admin-booking-search-guard-" + UUID.randomUUID() + "@cineverse.local");

        mockMvc.perform(get("/api/v1/admin/bookings").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchByUserEmailIsCaseInsensitiveAndScopedToThatCustomer() throws Exception {
        String adminToken = loginAsAdmin();
        String targetEmail = "search-target-" + UUID.randomUUID() + "@cineverse.local";
        String targetToken = registerAndLoginCustomer(targetEmail);
        String otherToken = registerAndLoginCustomer("search-other-" + UUID.randomUUID() + "@cineverse.local");
        UUID showtimeId = createShowtime(adminToken, "2026-11-01T10:00:00Z");

        UUID targetBookingId = createBooking(targetToken, showtimeId, seatIdAt(0));
        createBooking(otherToken, showtimeId, seatIdAt(1));

        mockMvc.perform(get("/api/v1/admin/bookings")
                        .param("userEmail", targetEmail)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].booking.id").value(targetBookingId.toString()))
                .andExpect(jsonPath("$.content[0].userEmail").value(targetEmail));

        // Partial, differently-cased substring — confirms this is a
        // case-insensitive "contains" search, not an exact match. The two
        // emails' distinguishing word ("target" vs "other") starts right
        // after this prefix, so it can't accidentally match both.
        mockMvc.perform(get("/api/v1/admin/bookings")
                        .param("userEmail", targetEmail.substring(0, 10).toUpperCase())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].booking.id").value(targetBookingId.toString()));
    }

    @Test
    void searchByMovieTitleIsCaseInsensitiveAndScopedToThatMovie() throws Exception {
        String adminToken = loginAsAdmin();
        String customerToken = registerAndLoginCustomer("search-movie-" + UUID.randomUUID() + "@cineverse.local");
        String uniqueTitle = "Zzyzx Search Target " + UUID.randomUUID();
        UUID matchingShowtimeId = createShowtime(adminToken, uniqueTitle, "2026-11-02T10:00:00Z");
        UUID otherShowtimeId = createShowtime(adminToken, "2026-11-03T10:00:00Z");

        UUID matchingBookingId = createBooking(customerToken, matchingShowtimeId, seatIdAt(0));
        createBooking(customerToken, otherShowtimeId, seatIdAt(0));

        mockMvc.perform(get("/api/v1/admin/bookings")
                        .param("movieTitle", "zzyzx search target")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].booking.id").value(matchingBookingId.toString()))
                .andExpect(jsonPath("$.content[0].booking.showtime.movieTitle").value(uniqueTitle));
    }

    @Test
    void searchByStatusCombinedWithUserEmailNarrowsToTheMatchingBooking() throws Exception {
        String adminToken = loginAsAdmin();
        String email = "search-status-" + UUID.randomUUID() + "@cineverse.local";
        String customerToken = registerAndLoginCustomer(email);
        UUID showtimeId = createShowtime(adminToken, "2026-11-04T10:00:00Z");

        UUID pendingBookingId = createBooking(customerToken, showtimeId, seatIdAt(0));
        UUID cancelledBookingId = createBooking(customerToken, showtimeId, seatIdAt(1));
        mockMvc.perform(delete("/api/v1/bookings/{id}", cancelledBookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/bookings")
                        .param("userEmail", email)
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].booking.id").value(pendingBookingId.toString()));

        mockMvc.perform(get("/api/v1/admin/bookings")
                        .param("userEmail", email)
                        .param("status", "CANCELLED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].booking.id").value(cancelledBookingId.toString()));
    }

    @Test
    void paginationRespectsPageAndSize() throws Exception {
        String adminToken = loginAsAdmin();
        String email = "search-page-" + UUID.randomUUID() + "@cineverse.local";
        String customerToken = registerAndLoginCustomer(email);
        UUID showtimeId = createShowtime(adminToken, "2026-11-05T10:00:00Z");

        createBooking(customerToken, showtimeId, seatIdAt(0));
        createBooking(customerToken, showtimeId, seatIdAt(1));
        createBooking(customerToken, showtimeId, seatIdAt(2));

        mockMvc.perform(get("/api/v1/admin/bookings")
                        .param("userEmail", email)
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/v1/admin/bookings")
                        .param("userEmail", email)
                        .param("page", "1")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
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
        return createShowtime(accessToken, "Admin Booking Search " + UUID.randomUUID(), startTimeIso);
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

    private String registerAndLoginCustomer(String email) throws Exception {
        String password = "Sup3rSecret!";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, password, "Admin Booking Search Customer"))))
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
