package com.cineverse.backend.report.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cineverse.backend.auth.dto.LoginRequest;
import com.cineverse.backend.auth.dto.RegisterRequest;
import com.cineverse.backend.booking.dto.CreateBookingRequest;
import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.booking.service.BookingService;
import com.cineverse.backend.cinema.entity.Seat;
import com.cineverse.backend.cinema.repository.SeatRepository;
import com.cineverse.backend.movie.dto.MovieRequest;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.payment.config.StripeProperties;
import com.cineverse.backend.payment.entity.Payment;
import com.cineverse.backend.payment.repository.PaymentRepository;
import com.cineverse.backend.showtime.dto.CreateShowtimeRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 8 acceptance criteria against real Postgres + Redis (Testcontainers)
 * — asserts exact aggregated numbers against hand-computed fixtures, not just
 * "200 OK" (per CLAUDE.md Phase 8's testing requirement). The container is
 * shared across every {@code @Test} method in this class (JUnit's default
 * per-class Testcontainers lifecycle), so every test creates its own movie
 * (random UUID in the title) and always filters the report by that movieId —
 * that's what keeps one test's fixtures from polluting another's sums,
 * exactly like the random-title-per-test pattern already used in
 * TicketFlowIntegrationTest, rather than relying on execution order or
 * resetting the database between methods.
 *
 * <p>Payment success is simulated by constructing {@link Payment} rows
 * directly (the same shortcut TicketFlowIntegrationTest takes for booking
 * confirmation) rather than driving a real/mocked Stripe checkout — Phase 6
 * already covers that flow; this Phase's own concern is the aggregation, not
 * how a payment reaches SUCCEEDED.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReportFlowIntegrationTest {

    private static final String SEEDED_HALL_ID = "21111111-1111-1111-1111-111111111111"; // Hall 1, 55 seats total (see V6 seed / CLAUDE.md Phase 3)
    private static final ZoneId CINEMA_ZONE = ZoneId.of("Asia/Kuala_Lumpur");

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
    private PaymentRepository paymentRepository;

    @Autowired
    private StripeProperties stripeProperties;

    @Test
    void salesReportSumsOnlyConfirmedSucceededPaymentsAndReportsOrphanedSuccessSeparately() throws Exception {
        String adminToken = loginAsAdmin();
        String customerToken = registerAndLoginCustomer();
        UUID movieId = createMovie(adminToken, "Sales Report " + UUID.randomUUID());
        UUID showtimeId = createShowtime(adminToken, movieId, "2027-04-01T10:00:00Z", "25.00");
        List<UUID> seats = hallSeatIds();

        // 1 seat @ 25.00, CONFIRMED + SUCCEEDED -> counts.
        UUID booking1 = createAndConfirmBooking(customerToken, showtimeId, seats.subList(0, 1));
        succeedPayment(booking1, new BigDecimal("25.00"));

        // 2 seats @ 25.00 = 50.00, CONFIRMED + SUCCEEDED -> counts.
        UUID booking2 = createAndConfirmBooking(customerToken, showtimeId, seats.subList(1, 3));
        succeedPayment(booking2, new BigDecimal("50.00"));

        // Left PENDING with a PENDING payment -> must NOT count.
        UUID booking3 = createBooking(customerToken, showtimeId, seats.subList(3, 4));
        pendingPayment(booking3, new BigDecimal("25.00"));

        // Cancelled booking whose Stripe payment reports success late ->
        // ORPHANED_SUCCESS, must show up as pending reconciliation, not revenue.
        UUID booking4 = createBooking(customerToken, showtimeId, seats.subList(4, 5));
        cancelBooking(customerToken, booking4);
        orphanedSuccessPayment(booking4, new BigDecimal("25.00"));

        LocalDate today = LocalDate.now(CINEMA_ZONE);
        MvcResult result = mockMvc.perform(get("/api/v1/admin/reports/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .param("granularity", "day")
                        .param("movieId", movieId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("buckets")).hasSize(1);
        JsonNode bucket = json.get("buckets").get(0);
        assertThat(bucket.get("periodStart").asString()).isEqualTo(today.toString());
        assertThat(new BigDecimal(bucket.get("revenue").asString())).isEqualByComparingTo("75.00");
        assertThat(bucket.get("bookingCount").asLong()).isEqualTo(2);
        assertThat(new BigDecimal(json.get("totalRevenue").asString())).isEqualByComparingTo("75.00");
        assertThat(new BigDecimal(json.get("pendingReconciliationAmount").asString())).isEqualByComparingTo("25.00");
        assertThat(json.get("currency").asString()).isEqualTo(stripeProperties.currency());
    }

    @Test
    void salesReportGapFillsBucketsWithNoRevenueAsZero() throws Exception {
        String adminToken = loginAsAdmin();
        UUID movieId = createMovie(adminToken, "Sales Gap Fill " + UUID.randomUUID());
        // No bookings/payments at all for this movie — every bucket in the
        // 3-day range must still appear, with zero revenue, via the
        // generate_series gap-fill in ReportRepository.salesBuckets.
        LocalDate from = LocalDate.now(CINEMA_ZONE).minusDays(2);
        LocalDate to = LocalDate.now(CINEMA_ZONE);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/reports/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("granularity", "day")
                        .param("movieId", movieId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("buckets")).hasSize(3);
        for (JsonNode bucket : json.get("buckets")) {
            assertThat(new BigDecimal(bucket.get("revenue").asString())).isEqualByComparingTo("0");
            assertThat(bucket.get("bookingCount").asLong()).isEqualTo(0);
        }
        assertThat(new BigDecimal(json.get("totalRevenue").asString())).isEqualByComparingTo("0");
    }

    @Test
    void occupancyReportCountsOnlyConfirmedBookingsSeatsAgainstHallTotal() throws Exception {
        String adminToken = loginAsAdmin();
        String customerToken = registerAndLoginCustomer();
        UUID movieId = createMovie(adminToken, "Occupancy Report " + UUID.randomUUID());
        UUID showtimeId = createShowtime(adminToken, movieId, "2027-04-02T10:00:00Z", "25.00");
        List<UUID> seats = hallSeatIds();

        createAndConfirmBooking(customerToken, showtimeId, seats.subList(0, 3));
        // Left PENDING -> must not count toward bookedSeats.
        createBooking(customerToken, showtimeId, seats.subList(3, 5));

        LocalDate day = LocalDate.of(2027, 4, 2);
        MvcResult result = mockMvc.perform(get("/api/v1/admin/reports/occupancy")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("from", day.toString())
                        .param("to", day.toString())
                        .param("movieId", movieId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("showtimes")).hasSize(1);
        JsonNode showtime = json.get("showtimes").get(0);
        assertThat(showtime.get("showtimeId").asString()).isEqualTo(showtimeId.toString());
        assertThat(showtime.get("totalSeats").asLong()).isEqualTo(55);
        assertThat(showtime.get("bookedSeats").asLong()).isEqualTo(3);
        assertThat(showtime.get("occupancyRate").asDouble()).isCloseTo(3.0 / 55.0, offset(0.001));
        assertThat(json.get("totalSeats").asLong()).isEqualTo(55);
        assertThat(json.get("totalBookedSeats").asLong()).isEqualTo(3);
    }

    @Test
    void reportsAreAdminOnly() throws Exception {
        String customerToken = registerAndLoginCustomer();
        LocalDate today = LocalDate.now(CINEMA_ZONE);

        mockMvc.perform(get("/api/v1/admin/reports/sales")
                        .param("from", today.toString())
                        .param("to", today.toString()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/reports/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .param("from", today.toString())
                        .param("to", today.toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/reports/occupancy")
                        .param("from", today.toString())
                        .param("to", today.toString()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/reports/occupancy")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .param("from", today.toString())
                        .param("to", today.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void toBeforeFromIsRejectedWith400() throws Exception {
        String adminToken = loginAsAdmin();

        mockMvc.perform(get("/api/v1/admin/reports/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("from", "2027-01-10")
                        .param("to", "2027-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void salesExportSupportsCsvAndPdfAndRejectsUnknownFormat() throws Exception {
        String adminToken = loginAsAdmin();
        LocalDate today = LocalDate.now(CINEMA_ZONE);

        MvcResult csv = mockMvc.perform(get("/api/v1/admin/reports/sales/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(new MediaType("text", "csv")))
                .andReturn();
        assertThat(csv.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");
        assertThat(csv.getResponse().getContentAsByteArray()).isNotEmpty();

        MvcResult pdf = mockMvc.perform(get("/api/v1/admin/reports/sales/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .param("format", "PDF"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();
        assertThat(pdf.getResponse().getContentAsByteArray()).isNotEmpty();

        mockMvc.perform(get("/api/v1/admin/reports/sales/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .param("format", "xml"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void occupancyExportSupportsCsvAndPdf() throws Exception {
        String adminToken = loginAsAdmin();
        LocalDate today = LocalDate.now(CINEMA_ZONE);

        mockMvc.perform(get("/api/v1/admin/reports/occupancy/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(new MediaType("text", "csv")));

        mockMvc.perform(get("/api/v1/admin/reports/occupancy/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    private List<UUID> hallSeatIds() {
        return seatRepository.findByHallIdOrderByRowLabelAscColumnNumberAsc(UUID.fromString(SEEDED_HALL_ID))
                .stream()
                .map(Seat::getId)
                .toList();
    }

    private UUID createBooking(String customerToken, UUID showtimeId, List<UUID> seatIds) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(showtimeId, seatIds))))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private UUID createAndConfirmBooking(String customerToken, UUID showtimeId, List<UUID> seatIds) throws Exception {
        UUID bookingId = createBooking(customerToken, showtimeId, seatIds);
        boolean confirmed = bookingService.confirmIfPending(bookingId);
        assertThat(confirmed).isTrue();
        return bookingId;
    }

    private void cancelBooking(String customerToken, UUID bookingId) throws Exception {
        mockMvc.perform(delete("/api/v1/bookings/{id}", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isNoContent());
    }

    private void succeedPayment(UUID bookingId, BigDecimal amount) {
        Payment payment = newPayment(bookingId, amount);
        payment.markSucceeded("pi_test_" + UUID.randomUUID());
        paymentRepository.saveAndFlush(payment);
    }

    private void pendingPayment(UUID bookingId, BigDecimal amount) {
        paymentRepository.saveAndFlush(newPayment(bookingId, amount));
    }

    private void orphanedSuccessPayment(UUID bookingId, BigDecimal amount) {
        Payment payment = newPayment(bookingId, amount);
        payment.markOrphanedSuccess("pi_test_" + UUID.randomUUID());
        paymentRepository.saveAndFlush(payment);
    }

    private Payment newPayment(UUID bookingId, BigDecimal amount) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        return new Payment(booking, "cs_test_" + UUID.randomUUID(), amount, stripeProperties.currency());
    }

    private UUID createShowtime(String accessToken, UUID movieId, String startTimeIso, String price) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/showtimes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShowtimeRequest(
                                movieId, UUID.fromString(SEEDED_HALL_ID),
                                Instant.parse(startTimeIso), new BigDecimal(price)))))
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private String registerAndLoginCustomer() throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@cineverse.local";
        String password = "Sup3rSecret!";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, password, "Report Flow Customer"))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private UUID readId(MvcResult result) throws Exception {
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();
        return UUID.fromString(id);
    }
}
