package com.cineverse.backend.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cineverse.backend.booking.dto.BookingResponse;
import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.entity.BookingSeat;
import com.cineverse.backend.booking.entity.BookingStatus;
import com.cineverse.backend.booking.event.BookingReleasedEvent;
import com.cineverse.backend.booking.mapper.BookingMapper;
import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.booking.repository.BookingSeatRepository;
import com.cineverse.backend.cinema.entity.Cinema;
import com.cineverse.backend.cinema.entity.Hall;
import com.cineverse.backend.cinema.entity.Seat;
import com.cineverse.backend.cinema.entity.SeatType;
import com.cineverse.backend.cinema.repository.SeatRepository;
import com.cineverse.backend.movie.entity.Movie;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.showtime.entity.Showtime;
import com.cineverse.backend.showtime.repository.ShowtimeRepository;
import com.cineverse.backend.ticket.config.TicketProperties;
import com.cineverse.backend.ticket.service.TicketCodeService;
import com.cineverse.backend.user.entity.Role;
import com.cineverse.backend.user.entity.User;
import com.cineverse.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit coverage for the order-history read path (GET /api/v1/bookings).
 * Deliberately not a Testcontainers test: the three things worth pinning down
 * here — that the repository's ordering survives assembly, that each booking
 * gets its own seats rather than the pooled query's, and that an elapsed hold
 * is expired on the way out — are all pure in-memory assembly logic, and
 * checking them without a database keeps the feedback loop fast. The
 * cross-user isolation guarantee is the part that genuinely needs a real
 * database and lives in BookingFlowIntegrationTest instead.
 *
 * <p>BookingMapper is used for real (not mocked) so the assertions run against
 * the response shape the controller actually returns, same approach as
 * TicketServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceListTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingSeatRepository bookingSeatRepository;
    @Mock
    private ShowtimeRepository showtimeRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SeatLockService seatLockService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BookingService bookingService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        BookingMapper bookingMapper = new BookingMapper(new TicketCodeService(
                new TicketProperties("test-ticket-secret-must-be-at-least-32-bytes-long")));
        bookingService = new BookingService(
                bookingRepository,
                bookingSeatRepository,
                showtimeRepository,
                seatRepository,
                userRepository,
                seatLockService,
                bookingMapper,
                eventPublisher);
    }

    @Test
    void keepsRepositoryOrderAndGivesEachBookingOnlyItsOwnSeats() {
        Showtime showtime = showtime("Dune", "Hall 2");
        Booking newer = booking(showtime, BookingStatus.CONFIRMED, Instant.now().plus(Duration.ofMinutes(5)));
        Booking older = booking(showtime, BookingStatus.CANCELLED, Instant.now().plus(Duration.ofMinutes(5)));
        BookingSeat newerSeat = new BookingSeat(newer, seat("A", 1, SeatType.STANDARD), new BigDecimal("25.00"));
        BookingSeat olderSeatOne = new BookingSeat(older, seat("B", 7, SeatType.STANDARD), new BigDecimal("25.00"));
        BookingSeat olderSeatTwo = new BookingSeat(older, seat("B", 8, SeatType.COUPLE), new BigDecimal("40.00"));

        // Repository contract is "newest first" — the service must not re-sort.
        when(bookingRepository.findAllByUserIdNewestFirst(userId)).thenReturn(List.of(newer, older));
        // Returned deliberately interleaved and in an unrelated order: grouping
        // must key off each row's own booking, not the query's ordering.
        when(bookingSeatRepository.findByBookingIdInWithSeat(anyCollection()))
                .thenReturn(List.of(olderSeatTwo, newerSeat, olderSeatOne));

        List<BookingResponse> responses = bookingService.listForUser(userId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(newer.getId());
        assertThat(responses.get(1).id()).isEqualTo(older.getId());
        assertThat(responses.get(0).seats()).extracting(BookingResponse.BookingSeatResponse::rowLabel)
                .containsExactly("A");
        assertThat(responses.get(1).seats()).extracting(BookingResponse.BookingSeatResponse::columnNumber)
                .containsExactlyInAnyOrder(7, 8);
        // CONFIRMED gets a ticket code; a cancelled order must never carry one.
        assertThat(responses.get(0).ticketCode()).isNotBlank();
        assertThat(responses.get(1).ticketCode()).isNull();
    }

    @Test
    void expiresAnElapsedPendingHoldWhileBuildingTheList() {
        Showtime showtime = showtime("Interstellar", "Hall 1");
        Booking elapsed = booking(showtime, BookingStatus.PENDING, Instant.now().minus(Duration.ofSeconds(1)));
        Booking stillHeld = booking(showtime, BookingStatus.PENDING, Instant.now().plus(Duration.ofMinutes(4)));
        when(bookingRepository.findAllByUserIdNewestFirst(userId)).thenReturn(List.of(elapsed, stillHeld));
        when(bookingSeatRepository.findByBookingIdInWithSeat(anyCollection())).thenReturn(List.of());

        List<BookingResponse> responses = bookingService.listForUser(userId);

        assertThat(responses.get(0).status()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(responses.get(1).status()).isEqualTo(BookingStatus.PENDING);

        // Persisted, not just reflected in the response.
        ArgumentCaptor<List<Booking>> saved = ArgumentCaptor.captor();
        verify(bookingRepository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).containsExactly(elapsed);

        // Releasing the hold must notify Phase 6 so the Stripe session is
        // expired too — the same event every other release path publishes.
        ArgumentCaptor<BookingReleasedEvent> published = ArgumentCaptor.captor();
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue().bookingId()).isEqualTo(elapsed.getId());
    }

    @Test
    void returnsEmptyWithoutQueryingSeatsWhenTheUserHasNoOrders() {
        when(bookingRepository.findAllByUserIdNewestFirst(userId)).thenReturn(List.of());

        assertThat(bookingService.listForUser(userId)).isEmpty();

        // An "in ()" query for an empty id list is both pointless and, on some
        // drivers, invalid SQL — the short-circuit must come before it.
        verifyNoInteractions(bookingSeatRepository);
        verify(bookingRepository, never()).saveAllAndFlush(anyCollection());
    }

    private Booking booking(Showtime showtime, BookingStatus status, Instant expiresAt) {
        User user = new User("customer@example.com", "hash", Role.CUSTOMER, "Test Customer");
        ReflectionTestUtils.setField(user, "id", userId);
        Booking booking = new Booking(user, showtime, new BigDecimal("25.00"), expiresAt);
        ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(booking, "status", status);
        ReflectionTestUtils.setField(booking, "createdAt", Instant.now());
        return booking;
    }

    private Showtime showtime(String movieTitle, String hallName) {
        Movie movie = new Movie();
        movie.setTitle(movieTitle);
        movie.setDurationMinutes(120);
        movie.setStatus(MovieStatus.NOW_PLAYING);
        Cinema cinema = new Cinema("CineVerse Downtown", "1 Main St");
        Hall hall = new Hall(cinema, hallName, 6, 10);
        Showtime showtime = new Showtime(
                movie, hall, Instant.now(), Instant.now().plus(Duration.ofHours(2)), new BigDecimal("25.00"));
        ReflectionTestUtils.setField(showtime, "id", UUID.randomUUID());
        return showtime;
    }

    private Seat seat(String rowLabel, int columnNumber, SeatType seatType) {
        Cinema cinema = new Cinema("CineVerse Downtown", "1 Main St");
        Hall hall = new Hall(cinema, "Hall 1", 6, 10);
        Seat seat = new Seat(hall, rowLabel, columnNumber, seatType);
        ReflectionTestUtils.setField(seat, "id", UUID.randomUUID());
        return seat;
    }
}
