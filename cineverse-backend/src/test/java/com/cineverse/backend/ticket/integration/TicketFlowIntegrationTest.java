package com.cineverse.backend.ticket.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cineverse.backend.auth.dto.LoginRequest;
import com.cineverse.backend.auth.dto.RegisterRequest;
import com.cineverse.backend.booking.dto.CreateBookingRequest;
import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.booking.service.BookingService;
import com.cineverse.backend.cinema.repository.SeatRepository;
import com.cineverse.backend.movie.dto.MovieRequest;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.showtime.dto.CreateShowtimeRequest;
import com.cineverse.backend.ticket.service.TicketCodeService;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Phase 7 acceptance criteria against real Postgres + Redis (Testcontainers).
 * Payment success is simulated by calling BookingService.confirmIfPending
 * directly (the same method PaymentService's webhook handler calls) rather
 * than going through a real/mocked Stripe flow — Phase 6 already covers that
 * separately; this phase's own concern is check-in, which starts from "the
 * booking is already CONFIRMED".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TicketFlowIntegrationTest {

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
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private TicketCodeService ticketCodeService;

    @Test
    void redeemingATicketTwiceRejectsTheSecondAttempt() throws Exception {
        String adminToken = loginAsAdmin();
        String customerToken = registerAndLoginCustomer();
        UUID showtimeId = createShowtime(adminToken, "2027-01-10T10:00:00Z");
        UUID bookingId = createConfirmedBooking(customerToken, showtimeId);
        String ticketCode = fetchTicketCode(customerToken, bookingId);

        MvcResult firstRedemption = mockMvc.perform(post("/api/v1/tickets/redeem")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RedeemRequest(ticketCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.seats[0].rowLabel").exists())
                .andReturn();
        assertThat(firstRedemption.getResponse().getContentAsString()).contains("redeemedAt");

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getRedeemedAt()).isNotNull();

        // Re-entry with the same code (e.g. a photo/screenshot of the QR) must be rejected.
        mockMvc.perform(post("/api/v1/tickets/redeem")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RedeemRequest(ticketCode))))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyAdminCanRedeemTickets() throws Exception {
        String adminToken = loginAsAdmin();
        String customerToken = registerAndLoginCustomer();
        UUID showtimeId = createShowtime(adminToken, "2027-01-11T10:00:00Z");
        UUID bookingId = createConfirmedBooking(customerToken, showtimeId);
        String ticketCode = fetchTicketCode(customerToken, bookingId);

        mockMvc.perform(post("/api/v1/tickets/redeem")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RedeemRequest(ticketCode))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTamperedOrGarbageCodeIsRejectedAsBadRequest() throws Exception {
        String adminToken = loginAsAdmin();

        mockMvc.perform(post("/api/v1/tickets/redeem")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RedeemRequest("not-a-real-ticket-code"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTicketForABookingThatIsNotConfirmedIsRejected() throws Exception {
        String adminToken = loginAsAdmin();
        String customerToken = registerAndLoginCustomer();
        UUID showtimeId = createShowtime(adminToken, "2027-01-12T10:00:00Z");
        UUID seatId = firstSeatId();
        MvcResult created = mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(showtimeId, List.of(seatId)))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID bookingId = readId(created);
        // Never confirmed — still PENDING. The API itself would never hand out a
        // ticketCode for this booking (BookingMapper only signs one when
        // CONFIRMED), so this constructs the code directly to exercise the
        // server-side defense-in-depth check rather than only relying on
        // "the frontend would never ask for this".
        String ticketCode = ticketCodeService.sign(bookingId);

        mockMvc.perform(post("/api/v1/tickets/redeem")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RedeemRequest(ticketCode))))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmedBookingExposesATicketCodeThatUnconfirmedOnesDoNot() throws Exception {
        String adminToken = loginAsAdmin();
        String customerToken = registerAndLoginCustomer();
        UUID showtimeId = createShowtime(adminToken, "2027-01-13T10:00:00Z");
        UUID seatId = firstSeatId();
        MvcResult created = mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(showtimeId, List.of(seatId)))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID bookingId = readId(created);

        mockMvc.perform(get("/api/v1/bookings/{id}", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").doesNotExist());

        bookingService.confirmIfPending(bookingId);

        mockMvc.perform(get("/api/v1/bookings/{id}", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").isString())
                .andExpect(jsonPath("$.redeemedAt").doesNotExist());
    }

    private record RedeemRequest(String ticketCode) {
    }

    private UUID createConfirmedBooking(String customerToken, UUID showtimeId) throws Exception {
        UUID seatId = firstSeatId();
        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(showtimeId, List.of(seatId)))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID bookingId = readId(result);
        boolean confirmed = bookingService.confirmIfPending(bookingId);
        assertThat(confirmed).isTrue();
        return bookingId;
    }

    private String fetchTicketCode(String customerToken, UUID bookingId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/bookings/{id}", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("ticketCode").asText();
    }

    private UUID firstSeatId() {
        return seatRepository.findByHallIdOrderByRowLabelAscColumnNumberAsc(UUID.fromString(SEEDED_HALL_ID))
                .get(0)
                .getId();
    }

    private UUID createShowtime(String accessToken, String startTimeIso) throws Exception {
        UUID movieId = createMovie(accessToken, "Ticket Flow " + UUID.randomUUID());

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
                                new RegisterRequest(email, password, "Ticket Flow Customer"))))
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
