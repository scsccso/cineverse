package com.cineverse.backend.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cineverse.backend.booking.dto.AdminBookingSearchResult;
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
import com.cineverse.backend.movie.entity.Movie;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.showtime.entity.Showtime;
import com.cineverse.backend.ticket.config.TicketProperties;
import com.cineverse.backend.ticket.service.TicketCodeService;
import com.cineverse.backend.user.entity.Role;
import com.cineverse.backend.user.entity.User;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit coverage for GET /api/v1/admin/bookings' assembly logic — same split
 * of responsibility as BookingServiceListTest: pure in-memory assembly
 * (filter pass-through, seat grouping, lazy expiry) is covered here without
 * a database; the thing this can't prove — that 401/403 actually gate the
 * endpoint over real HTTP — lives in AdminBookingFlowIntegrationTest instead.
 *
 * <p>BookingMapper is real, not mocked, so assertions run against the actual
 * response shape (matches BookingServiceListTest's own reasoning).
 */
@ExtendWith(MockitoExtension.class)
class AdminBookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingSeatRepository bookingSeatRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AdminBookingService adminBookingService;

    @BeforeEach
    void setUp() {
        BookingMapper bookingMapper = new BookingMapper(new TicketCodeService(
                new TicketProperties("test-ticket-secret-must-be-at-least-32-bytes-long")));
        adminBookingService = new AdminBookingService(bookingRepository, bookingSeatRepository, bookingMapper, eventPublisher);
    }

    @Test
    void turnsFiltersIntoLowerCasedWildcardPatternsAndBlankIntoNull() {
        // Not the raw trimmed value: BookingRepository.search's doc comment
        // explains why the "%...%" wildcarding and lower-casing happen here
        // in Java rather than via lower(concat(...)) in JPQL — the latter
        // 500s on PostgreSQL for any non-null parameter (caught by
        // AdminBookingFlowIntegrationTest before this test existed).
        Pageable pageable = PageRequest.of(0, 20);
        when(bookingRepository.search(eq("%fan@example.com%"), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        adminBookingService.search("  Fan@Example.com  ", "   ", null, pageable);

        verify(bookingRepository).search(eq("%fan@example.com%"), isNull(), isNull(), eq(pageable));
    }

    @Test
    void passesStatusThroughAndAttachesEachRowsOwnerEmail() {
        Pageable pageable = PageRequest.of(0, 20);
        Showtime showtime = showtime("Dune", "Hall 2");
        Booking booking = booking("fan@example.com", showtime, BookingStatus.CONFIRMED, Instant.now().plus(Duration.ofMinutes(5)));
        when(bookingRepository.search(isNull(), isNull(), eq(BookingStatus.CONFIRMED), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(booking), pageable, 1));
        when(bookingSeatRepository.findByBookingIdInWithSeat(anyCollection())).thenReturn(List.of());

        Page<AdminBookingSearchResult> result =
                adminBookingService.search(null, null, BookingStatus.CONFIRMED, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).userEmail()).isEqualTo("fan@example.com");
        assertThat(result.getContent().get(0).booking().id()).isEqualTo(booking.getId());
        assertThat(result.getContent().get(0).booking().status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void givesEachBookingOnlyItsOwnSeatsEvenReturnedOutOfOrder() {
        Pageable pageable = PageRequest.of(0, 20);
        Showtime showtime = showtime("Interstellar", "Hall 1");
        Booking first = booking("a@example.com", showtime, BookingStatus.CONFIRMED, Instant.now().plus(Duration.ofMinutes(5)));
        Booking second = booking("b@example.com", showtime, BookingStatus.CONFIRMED, Instant.now().plus(Duration.ofMinutes(5)));
        BookingSeat firstSeat = new BookingSeat(first, seat("A", 1, SeatType.STANDARD), new BigDecimal("25.00"));
        BookingSeat secondSeatOne = new BookingSeat(second, seat("B", 7, SeatType.STANDARD), new BigDecimal("25.00"));
        BookingSeat secondSeatTwo = new BookingSeat(second, seat("B", 8, SeatType.COUPLE), new BigDecimal("40.00"));
        when(bookingRepository.search(isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        // Deliberately interleaved and unrelated to booking order — grouping
        // must key off each row's own booking, not return order.
        when(bookingSeatRepository.findByBookingIdInWithSeat(anyCollection()))
                .thenReturn(List.of(secondSeatTwo, firstSeat, secondSeatOne));

        Page<AdminBookingSearchResult> result = adminBookingService.search(null, null, null, pageable);

        AdminBookingSearchResult firstResult = result.getContent().stream()
                .filter(r -> r.booking().id().equals(first.getId())).findFirst().orElseThrow();
        AdminBookingSearchResult secondResult = result.getContent().stream()
                .filter(r -> r.booking().id().equals(second.getId())).findFirst().orElseThrow();
        assertThat(firstResult.booking().seats())
                .extracting(BookingResponse.BookingSeatResponse::rowLabel).containsExactly("A");
        assertThat(secondResult.booking().seats())
                .extracting(BookingResponse.BookingSeatResponse::rowLabel).containsExactlyInAnyOrder("B", "B");
    }

    @Test
    void expiresAnElapsedPendingHoldWhileSearching() {
        Pageable pageable = PageRequest.of(0, 20);
        Showtime showtime = showtime("Interstellar", "Hall 1");
        Booking elapsed = booking("a@example.com", showtime, BookingStatus.PENDING, Instant.now().minus(Duration.ofSeconds(1)));
        Booking stillHeld = booking("b@example.com", showtime, BookingStatus.PENDING, Instant.now().plus(Duration.ofMinutes(4)));
        when(bookingRepository.search(isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(elapsed, stillHeld), pageable, 2));
        when(bookingSeatRepository.findByBookingIdInWithSeat(anyCollection())).thenReturn(List.of());

        Page<AdminBookingSearchResult> result = adminBookingService.search(null, null, null, pageable);

        AdminBookingSearchResult elapsedResult = result.getContent().stream()
                .filter(r -> r.booking().id().equals(elapsed.getId())).findFirst().orElseThrow();
        assertThat(elapsedResult.booking().status()).isEqualTo(BookingStatus.EXPIRED);

        // Persisted, not just reflected in the response.
        ArgumentCaptor<List<Booking>> saved = ArgumentCaptor.captor();
        verify(bookingRepository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).containsExactly(elapsed);

        // Same release notification every other lazy-expiry path publishes,
        // so Phase 6's Stripe-session cleanup still fires for a hold that
        // expired via this read path.
        ArgumentCaptor<BookingReleasedEvent> published = ArgumentCaptor.captor();
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue().bookingId()).isEqualTo(elapsed.getId());
    }

    @Test
    void returnsEmptyPageWithoutQueryingSeatsWhenNothingMatches() {
        Pageable pageable = PageRequest.of(3, 20);
        // Total is nonzero (page 3 requested, but only 1 element exists
        // total) — a real scenario (paged past the last result), not a
        // contrived one, and the point of this assertion: the short
        // circuit must still carry the true total through rather than
        // reporting 0 just because this page's content is empty.
        when(bookingRepository.search(isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 1));

        Page<AdminBookingSearchResult> result = adminBookingService.search(null, null, null, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
        // An "in ()" seat query for an empty id list is both pointless and,
        // on some drivers, invalid SQL — the short-circuit must come before it.
        verifyNoInteractions(bookingSeatRepository);
        verify(bookingRepository, never()).saveAllAndFlush(anyCollection());
    }

    private Booking booking(String userEmail, Showtime showtime, BookingStatus status, Instant expiresAt) {
        User user = new User(userEmail, "hash", Role.CUSTOMER, "Test Customer");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
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
