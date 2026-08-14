package com.cineverse.backend.showtime.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cineverse.backend.auth.dto.LoginRequest;
import com.cineverse.backend.auth.dto.RegisterRequest;
import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.entity.BookingSeat;
import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.booking.repository.BookingSeatRepository;
import com.cineverse.backend.cinema.entity.Seat;
import com.cineverse.backend.cinema.repository.SeatRepository;
import com.cineverse.backend.movie.dto.MovieRequest;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.showtime.dto.CreateShowtimeRequest;
import com.cineverse.backend.showtime.entity.Showtime;
import com.cineverse.backend.showtime.repository.ShowtimeRepository;
import com.cineverse.backend.user.entity.User;
import com.cineverse.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Showtime Scheduling against a real Postgres (Testcontainers): the
 * 20-minute cleanup buffer conflict rule, the public/ADMIN split (401 for
 * anonymous vs 403 for wrong-role, per the Phase 2 semantics), and the
 * full admin flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ShowtimeAdminFlowIntegrationTest {

    private static final String SEEDED_HALL_ID = "21111111-1111-1111-1111-111111111111"; // Hall 1, from V6 seed
    private static final String SEEDED_HALL_2_ID = "22222222-1111-1111-1111-111111111111"; // Hall 2, from V6 seed

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void cookieProperties(DynamicPropertyRegistry registry) {
        registry.add("app.security.cookie-secure", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    @Test
    void adminCanCreateShowtimeAndPublicCanFetchIt() throws Exception {
        String accessToken = loginAsAdmin();
        UUID movieId = createMovie(accessToken, "Interstellar " + UUID.randomUUID(), 120);
        Instant startTime = Instant.parse("2026-09-01T10:00:00Z");

        MvcResult createResult = mockMvc.perform(post("/api/v1/showtimes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShowtimeRequest(movieId, UUID.fromString(SEEDED_HALL_ID), startTime,
                                        new BigDecimal("25.00")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movie.id").value(movieId.toString()))
                .andExpect(jsonPath("$.hall.id").value(SEEDED_HALL_ID))
                .andExpect(jsonPath("$.hall.cinemaName").value("CineVerse Downtown"))
                .andExpect(jsonPath("$.startTime").value("2026-09-01T10:00:00Z"))
                .andExpect(jsonPath("$.endTime").value("2026-09-01T12:00:00Z")) // 120 min later, no buffer baked in
                .andExpect(jsonPath("$.price").value(25.00))
                .andReturn();
        UUID showtimeId = readId(createResult);

        mockMvc.perform(get("/api/v1/showtimes/{id}", showtimeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movie.title").value(org.hamcrest.Matchers.startsWith("Interstellar")));

        mockMvc.perform(get("/api/v1/showtimes").param("movieId", movieId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(showtimeId.toString()));

        mockMvc.perform(get("/api/v1/showtimes").param("date", "2026-09-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + showtimeId + "')]").exists());

        mockMvc.perform(delete("/api/v1/showtimes/{id}", showtimeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/showtimes/{id}", showtimeId))
                .andExpect(status().isNotFound());
    }

    @Test
    void secondOverlappingShowtimeInSameHallIsRejectedWith409() throws Exception {
        String accessToken = loginAsAdmin();
        UUID movieId = createMovie(accessToken, "Conflict Movie " + UUID.randomUUID(), 120);

        createShowtime(accessToken, movieId, "2026-09-02T10:00:00Z"); // occupies 10:00-12:00

        // Starts only 10 minutes after the first ends — needs 20.
        mockMvc.perform(post("/api/v1/showtimes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShowtimeRequest(
                                movieId, UUID.fromString(SEEDED_HALL_ID),
                                Instant.parse("2026-09-02T12:10:00Z"), new BigDecimal("25.00")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Hall 1")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cleanup buffer")));
    }

    @Test
    void showtimesWithAtLeastTwentyMinuteGapBothSucceed() throws Exception {
        String accessToken = loginAsAdmin();
        UUID movieId = createMovie(accessToken, "Gap Movie " + UUID.randomUUID(), 120);

        createShowtime(accessToken, movieId, "2026-09-03T10:00:00Z"); // occupies 10:00-12:00

        // Starts exactly 20 minutes after the first ends — the boundary case, allowed.
        mockMvc.perform(post("/api/v1/showtimes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShowtimeRequest(
                                movieId, UUID.fromString(SEEDED_HALL_ID),
                                Instant.parse("2026-09-03T12:20:00Z"), new BigDecimal("25.00")))))
                .andExpect(status().isCreated());
    }

    @Test
    void showtimesCanBeFilteredByHall() throws Exception {
        String accessToken = loginAsAdmin();
        UUID movieId = createMovie(accessToken, "Hall Filter Movie " + UUID.randomUUID(), 100);

        UUID hall1ShowtimeId = createShowtimeReturningId(accessToken, movieId, SEEDED_HALL_ID, "2026-09-06T10:00:00Z");
        UUID hall2ShowtimeId =
                createShowtimeReturningId(accessToken, movieId, SEEDED_HALL_2_ID, "2026-09-06T10:00:00Z");

        mockMvc.perform(get("/api/v1/showtimes")
                        .param("movieId", movieId.toString())
                        .param("hallId", SEEDED_HALL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(hall1ShowtimeId.toString()));

        mockMvc.perform(get("/api/v1/showtimes")
                        .param("movieId", movieId.toString())
                        .param("hallId", SEEDED_HALL_2_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(hall2ShowtimeId.toString()));
    }

    /**
     * bookedSeats must count the CONFIRMED booking's seat and ignore the
     * PENDING one entirely — same "only CONFIRMED counts" rule the Phase 8
     * occupancy report already established (see ReportRepository
     * .occupancyRows), asserted here at the showtime-list/detail level
     * instead of re-asserted at the report level a second time. totalSeats
     * is asserted against a live count from SeatRepository rather than a
     * hardcoded number, so this doesn't silently rot if the seed layout
     * (V6) ever changes.
     */
    @Test
    void bookedSeatsCountsOnlyConfirmedBookingsNotPending() throws Exception {
        String accessToken = loginAsAdmin();
        UUID movieId = createMovie(accessToken, "Seat Count Movie " + UUID.randomUUID(), 100);
        UUID showtimeId =
                createShowtimeReturningId(accessToken, movieId, SEEDED_HALL_ID, "2026-09-07T10:00:00Z");

        Showtime showtime = showtimeRepository.findById(showtimeId).orElseThrow();
        User admin = userRepository.findByEmail("admin@cineverse.local").orElseThrow();
        List<Seat> hallSeats = seatRepository.findByHallIdOrderByRowLabelAscColumnNumberAsc(
                UUID.fromString(SEEDED_HALL_ID));

        Booking confirmed = new Booking(admin, showtime, new BigDecimal("25.00"), Instant.now().plusSeconds(300));
        confirmed.markConfirmed();
        // saveAndFlush: Booking has @CreationTimestamp/@UpdateTimestamp — see
        // TimestampedEntitySaveFlushRuleTest, this is the same rule that
        // caught AdminUserService.updateUserRole using bare save() before.
        bookingRepository.saveAndFlush(confirmed);
        bookingSeatRepository.saveAndFlush(new BookingSeat(confirmed, hallSeats.get(0), new BigDecimal("25.00")));

        Booking pending = new Booking(admin, showtime, new BigDecimal("25.00"), Instant.now().plusSeconds(300));
        bookingRepository.saveAndFlush(pending);
        bookingSeatRepository.saveAndFlush(new BookingSeat(pending, hallSeats.get(1), new BigDecimal("25.00")));

        mockMvc.perform(get("/api/v1/showtimes/{id}", showtimeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookedSeats").value(1))
                .andExpect(jsonPath("$.totalSeats").value(hallSeats.size()));

        mockMvc.perform(get("/api/v1/showtimes").param("movieId", movieId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookedSeats").value(1))
                .andExpect(jsonPath("$[0].totalSeats").value(hallSeats.size()));
    }

    /** Not previously covered — the only existing delete test deletes a showtime with zero bookings. */
    @Test
    void deletingAShowtimeWithBookingsIsRejectedWith409() throws Exception {
        String accessToken = loginAsAdmin();
        UUID movieId = createMovie(accessToken, "Delete Guard Movie " + UUID.randomUUID(), 100);
        UUID showtimeId =
                createShowtimeReturningId(accessToken, movieId, SEEDED_HALL_ID, "2026-09-08T10:00:00Z");

        Showtime showtime = showtimeRepository.findById(showtimeId).orElseThrow();
        User admin = userRepository.findByEmail("admin@cineverse.local").orElseThrow();
        Booking booking = new Booking(admin, showtime, new BigDecimal("25.00"), Instant.now().plusSeconds(300));
        bookingRepository.saveAndFlush(booking); // stays PENDING — existsByShowtimeId doesn't filter by status

        mockMvc.perform(delete("/api/v1/showtimes/{id}", showtimeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("bookings")));

        mockMvc.perform(get("/api/v1/showtimes/{id}", showtimeId)).andExpect(status().isOk());
    }

    @Test
    void anonymousRequestsToMutateShowtimesAreRejectedWith401() throws Exception {
        String accessToken = loginAsAdmin();
        UUID movieId = createMovie(accessToken, "Anon Guard " + UUID.randomUUID(), 100);

        mockMvc.perform(post("/api/v1/showtimes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShowtimeRequest(
                                movieId, UUID.fromString(SEEDED_HALL_ID),
                                Instant.parse("2026-09-04T10:00:00Z"), new BigDecimal("25.00")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCustomerCannotMutateShowtimes() throws Exception {
        String adminToken = loginAsAdmin();
        String customerToken = registerAndLoginCustomer();
        UUID movieId = createMovie(adminToken, "Role Guard " + UUID.randomUUID(), 100);

        mockMvc.perform(post("/api/v1/showtimes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShowtimeRequest(
                                movieId, UUID.fromString(SEEDED_HALL_ID),
                                Instant.parse("2026-09-05T10:00:00Z"), new BigDecimal("25.00")))))
                .andExpect(status().isForbidden());
    }

    private void createShowtime(String accessToken, UUID movieId, String startTimeIso) throws Exception {
        mockMvc.perform(post("/api/v1/showtimes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShowtimeRequest(
                                movieId, UUID.fromString(SEEDED_HALL_ID), Instant.parse(startTimeIso),
                                new BigDecimal("25.00")))))
                .andExpect(status().isCreated());
    }

    private UUID createShowtimeReturningId(String accessToken, UUID movieId, String hallId, String startTimeIso)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/showtimes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShowtimeRequest(
                                movieId, UUID.fromString(hallId), Instant.parse(startTimeIso),
                                new BigDecimal("25.00")))))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private UUID createMovie(String accessToken, String title, int durationMinutes) throws Exception {
        MovieRequest request = new MovieRequest(
                title, "desc", "tagline", durationMinutes, "PG-13", null, null,
                MovieStatus.NOW_PLAYING, Set.of());

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
                                new RegisterRequest(email, password, "Role Test Customer"))))
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
