package com.cineverse.backend.demo.service;

import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.cinema.entity.Hall;
import com.cineverse.backend.cinema.repository.HallRepository;
import com.cineverse.backend.demo.config.DemoResetProperties;
import com.cineverse.backend.demo.dto.DemoResetResult;
import com.cineverse.backend.movie.entity.Movie;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.movie.repository.MovieRepository;
import com.cineverse.backend.movie.repository.MovieSpecifications;
import com.cineverse.backend.payment.repository.PaymentRepository;
import com.cineverse.backend.showtime.dto.CreateShowtimeRequest;
import com.cineverse.backend.showtime.repository.ShowtimeRepository;
import com.cineverse.backend.showtime.service.ShowtimeService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Backs the two /internal/demo-reset endpoints (DemoResetController) — a
 * scheduled cron's way of undoing customer-caused "damage" to a public demo
 * deployment (seats booked out, cards used up) without wiping the catalog
 * itself. Scoped deliberately narrow: this never touches movies, genres,
 * cinemas, halls, seats, or movie_status_history — see CLAUDE.md's "Demo
 * data reset" entry for why (admin stays private on a public demo, so none
 * of that data is ever customer-reachable in the first place).
 *
 * <p>Two operations, not one endpoint with a mode switch (see
 * DemoResetController): resetTransactions() is the cheap, frequent one
 * (bookings/payments/seat locks only); resetShowtimes() is the more
 * expensive daily one. They're not actually independent, though —
 * regenerating showtimes requires deleting the existing ones first, and
 * showtimes.id is ON DELETE RESTRICT from bookings (V9), so resetShowtimes()
 * has to run the exact same transactional-data clear first regardless of
 * when resetTransactions() last ran. clearTransactionalData() is the one
 * shared step both call, not two copies of the same FK-ordered deletes.
 */
@Service
public class DemoResetService {

    /** Cinema-local (Asia/Kuala_Lumpur, fixed UTC+8 — same reasoning as
     * lib/format.ts's cinemaLocalTimeToIso on the frontend: no DST, so a
     * constant offset is enough, no ZoneId table lookup needed) daily
     * showtime slots. Three, not four, and spaced 4.5 hours apart — wide
     * enough that the 20-minute cleanup buffer (ShowtimeService
     * .CLEANUP_BUFFER) can never be violated regardless of which movie a
     * slot happens to get assigned (even a 3-hour film plus the buffer is
     * under 200 minutes, well inside a 270-minute gap), so this template
     * never needs to know individual movie durations to stay conflict-free. */
    private static final ZoneOffset CINEMA_OFFSET = ZoneOffset.of("+08:00");
    private static final List<LocalTime> DAILY_SLOTS =
            List.of(LocalTime.of(11, 0), LocalTime.of(15, 30), LocalTime.of(20, 0));
    /** Matches the audit's "regenerate showtimes daily, covering the next
     * ~week" recommendation — long enough that a visitor browsing any day
     * this week sees something bookable, short enough that a daily cron
     * keeps the window from ever running dry. */
    private static final int RESET_WINDOW_DAYS = 7;
    /** Same flat RM 25.00 every existing test/demo showtime in this codebase
     * already uses (see ShowtimeAdminFlowIntegrationTest, AdminPaymentFlowIntegrationTest,
     * etc.) — not a new value invented for this feature. */
    private static final BigDecimal SHOWTIME_PRICE = new BigDecimal("25.00");
    private static final String SEAT_LOCK_KEY_PATTERN = "seat-lock:*";

    private final DemoResetProperties demoResetProperties;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final ShowtimeRepository showtimeRepository;
    private final ShowtimeService showtimeService;
    private final MovieRepository movieRepository;
    private final HallRepository hallRepository;
    private final StringRedisTemplate redisTemplate;

    public DemoResetService(
            DemoResetProperties demoResetProperties,
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            ShowtimeRepository showtimeRepository,
            ShowtimeService showtimeService,
            MovieRepository movieRepository,
            HallRepository hallRepository,
            StringRedisTemplate redisTemplate) {
        this.demoResetProperties = demoResetProperties;
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.showtimeRepository = showtimeRepository;
        this.showtimeService = showtimeService;
        this.movieRepository = movieRepository;
        this.hallRepository = hallRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Called from the controller before either reset method, not inside
     * them — a rejected request never opens a transaction. Order matters:
     * "not configured" is checked and thrown before the provided header is
     * ever compared against anything, so a blank/unset secret can never
     * accidentally match a blank/missing header. Comparison itself is
     * {@link MessageDigest#isEqual} (constant-time), not {@code String
     * .equals} — this is the entire access control for a data-destructive
     * endpoint, worth the one extra import.
     */
    public void verifySecret(String providedSecret) {
        String configuredSecret = demoResetProperties.secret();
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Demo reset is not enabled in this environment");
        }
        boolean matches = providedSecret != null && MessageDigest.isEqual(
                configuredSecret.getBytes(StandardCharsets.UTF_8), providedSecret.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing demo reset secret");
        }
    }

    /** The frequent (e.g. every 6h), cheap reset — clears what customer
     * checkout activity produces, leaves the showtime schedule alone. */
    @Transactional
    public DemoResetResult resetTransactions() {
        ClearedCounts cleared = clearTransactionalData();
        return new DemoResetResult(cleared.bookings(), cleared.payments(), 0, 0);
    }

    /** The daily, more expensive reset — everything resetTransactions()
     * does, plus a full showtime regeneration. Bundled rather than assuming
     * resetTransactions() already ran recently: showtimes.id is RESTRICT
     * from bookings, so deleting showtimes here requires a clear slate
     * regardless. */
    @Transactional
    public DemoResetResult resetShowtimes() {
        ClearedCounts cleared = clearTransactionalData();
        int showtimesDeleted = (int) showtimeRepository.count();
        showtimeRepository.deleteAllInBatch();
        int created = regenerateShowtimes();
        return new DemoResetResult(cleared.bookings(), cleared.payments(), showtimesDeleted, created);
    }

    /**
     * payments, then bookings — not booking_seats explicitly at all.
     * payments.booking_id is ON DELETE RESTRICT (V10), so payments has to
     * go first; booking_seats.booking_id is ON DELETE CASCADE (V9, "a pure
     * junction row with no independent meaning" — see DATABASE.md), which
     * is a database-level constraint action that fires on a plain bulk
     * {@code deleteAllInBatch()} exactly the same as it would on a
     * one-row-at-a-time delete, so a third explicit delete against
     * booking_seats would just be redundant, not more correct.
     * deleteAllInBatch (not deleteAll, which loads every row into memory
     * first) issues one bulk SQL DELETE per table — this project has no
     * entity listeners either table's rows would need to fire, so nothing
     * is lost by skipping the ORM-level, one-by-one delete path.
     */
    private ClearedCounts clearTransactionalData() {
        int paymentsCleared = (int) paymentRepository.count();
        paymentRepository.deleteAllInBatch();
        int bookingsCleared = (int) bookingRepository.count();
        bookingRepository.deleteAllInBatch();
        clearSeatLockKeys();
        return new ClearedCounts(bookingsCleared, paymentsCleared);
    }

    /** KEYS, not SCAN — this Redis instance has no persistence configured
     * (see docker-compose.yml/CLAUDE.md Phase 5) and holds nothing but a
     * handful of seat-lock keys at any moment (this app's only Redis
     * consumer is SeatLockService), so a single KEYS call blocking briefly
     * is a non-issue at this scale; SCAN's incremental-cursor complexity
     * would be solving a production-Redis problem this deployment doesn't
     * have. */
    private void clearSeatLockKeys() {
        Set<String> keys = redisTemplate.keys(SEAT_LOCK_KEY_PATTERN);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * Deletion (showtimeRepository.deleteAllInBatch(), in resetShowtimes())
     * is bulk because every existsByShowtimeId-style guard it would
     * otherwise trigger is already vacuously satisfied — clearTransactionalData()
     * just removed every booking, so there is nothing left for
     * ShowtimeService.delete()'s existence check to find. Recreation goes
     * through ShowtimeService.create() instead of a matching bulk insert,
     * on purpose: creation still needs its real behavior (the 20-minute
     * conflict check, saveAndFlush, response assembly), and there is
     * exactly one place in this codebase that's allowed to define what a
     * valid showtime is — this method doesn't get a second, competing copy
     * of that logic just because it's inserting many rows at once. NOW_PLAYING
     * movies only (COMING_SOON/ENDED never got a showtime here either), and
     * every hall gets an identical slot template, cycling round-robin
     * through the NOW_PLAYING list so no single movie monopolizes every slot.
     */
    private int regenerateShowtimes() {
        List<Movie> nowPlayingMovies = movieRepository.findAll(
                MovieSpecifications.hasStatus(MovieStatus.NOW_PLAYING), Sort.by("title"));
        if (nowPlayingMovies.isEmpty()) {
            return 0;
        }
        List<Hall> halls = hallRepository.findAll(Sort.by("name"));
        LocalDate today = Instant.now().atOffset(CINEMA_OFFSET).toLocalDate();

        int created = 0;
        int movieIndex = 0;
        for (int dayOffset = 0; dayOffset < RESET_WINDOW_DAYS; dayOffset++) {
            LocalDate date = today.plusDays(dayOffset);
            for (Hall hall : halls) {
                for (LocalTime slot : DAILY_SLOTS) {
                    Movie movie = nowPlayingMovies.get(movieIndex % nowPlayingMovies.size());
                    Instant startTime = date.atTime(slot).atOffset(CINEMA_OFFSET).toInstant();
                    showtimeService.create(new CreateShowtimeRequest(movie.getId(), hall.getId(), startTime, SHOWTIME_PRICE));
                    created++;
                    movieIndex++;
                }
            }
        }
        return created;
    }

    private record ClearedCounts(int bookings, int payments) {
    }
}
