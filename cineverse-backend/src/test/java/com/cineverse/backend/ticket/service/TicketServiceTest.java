package com.cineverse.backend.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.entity.BookingSeat;
import com.cineverse.backend.booking.entity.BookingStatus;
import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.booking.repository.BookingSeatRepository;
import com.cineverse.backend.cinema.entity.Cinema;
import com.cineverse.backend.cinema.entity.Hall;
import com.cineverse.backend.cinema.entity.Seat;
import com.cineverse.backend.cinema.entity.SeatType;
import com.cineverse.backend.movie.entity.Movie;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.showtime.entity.Showtime;
import com.cineverse.backend.ticket.dto.TicketRedemptionResponse;
import com.cineverse.backend.ticket.exception.BookingNotConfirmedException;
import com.cineverse.backend.ticket.exception.TicketAlreadyRedeemedException;
import com.cineverse.backend.user.entity.Role;
import com.cineverse.backend.user.entity.User;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingSeatRepository bookingSeatRepository;

    private TicketCodeService ticketCodeService;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketCodeService = new TicketCodeService(
                new com.cineverse.backend.ticket.config.TicketProperties("test-ticket-secret-must-be-at-least-32-bytes-long"));
        ticketService = new TicketService(bookingRepository, bookingSeatRepository, ticketCodeService);
    }

    @Test
    void redeemMarksAConfirmedUnusedBookingAsRedeemedAndReturnsShowtimeAndSeats() {
        UUID bookingId = UUID.randomUUID();
        Showtime showtime = showtime(UUID.randomUUID(), "Dune", "Hall 2");
        Booking booking = booking(bookingId, showtime, BookingStatus.CONFIRMED, null);
        BookingSeat bookingSeat = new BookingSeat(booking, seat("A", 5, SeatType.STANDARD), new BigDecimal("25.00"));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingSeatRepository.findByBookingId(bookingId)).thenReturn(List.of(bookingSeat));
        String ticketCode = ticketCodeService.sign(bookingId);

        Instant before = Instant.now();
        TicketRedemptionResponse response = ticketService.redeem(ticketCode);
        Instant after = Instant.now();

        assertThat(response.bookingId()).isEqualTo(bookingId);
        assertThat(response.movieTitle()).isEqualTo("Dune");
        assertThat(response.hallName()).isEqualTo("Hall 2");
        assertThat(response.seats()).hasSize(1);
        assertThat(response.seats().get(0).rowLabel()).isEqualTo("A");
        assertThat(response.seats().get(0).columnNumber()).isEqualTo(5);
        assertThat(response.redeemedAt()).isBetween(before, after);
        assertThat(booking.getRedeemedAt()).isEqualTo(response.redeemedAt());
    }

    @Test
    void redeemRejectsATicketForABookingThatIsNotConfirmed() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, showtime(UUID.randomUUID(), "Dune", "Hall 2"), BookingStatus.PENDING, null);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        String ticketCode = ticketCodeService.sign(bookingId);

        assertThatThrownBy(() -> ticketService.redeem(ticketCode))
                .isInstanceOf(BookingNotConfirmedException.class);
    }

    @Test
    void redeemRejectsATicketThatHasAlreadyBeenRedeemed() {
        UUID bookingId = UUID.randomUUID();
        Instant firstRedemption = Instant.now().minus(Duration.ofHours(1));
        Booking booking = booking(
                bookingId, showtime(UUID.randomUUID(), "Dune", "Hall 2"), BookingStatus.CONFIRMED, firstRedemption);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        String ticketCode = ticketCodeService.sign(bookingId);

        // Simulates re-entry with a photo/screenshot of an already-used ticket.
        assertThatThrownBy(() -> ticketService.redeem(ticketCode))
                .isInstanceOf(TicketAlreadyRedeemedException.class);

        // Must not have overwritten the original redemption timestamp.
        assertThat(booking.getRedeemedAt()).isEqualTo(firstRedemption);
    }

    private Booking booking(UUID id, Showtime showtime, BookingStatus status, Instant redeemedAt) {
        User user = new User("customer@example.com", "hash", Role.CUSTOMER, "Test Customer");
        Booking booking = new Booking(user, showtime, new BigDecimal("25.00"), Instant.now().plus(Duration.ofMinutes(5)));
        ReflectionTestUtils.setField(booking, "id", id);
        ReflectionTestUtils.setField(booking, "status", status);
        ReflectionTestUtils.setField(booking, "redeemedAt", redeemedAt);
        return booking;
    }

    private Showtime showtime(UUID id, String movieTitle, String hallName) {
        Movie movie = new Movie();
        movie.setTitle(movieTitle);
        movie.setDurationMinutes(120);
        movie.setStatus(MovieStatus.NOW_PLAYING);
        Cinema cinema = new Cinema("CineVerse Downtown", "1 Main St");
        Hall hall = new Hall(cinema, hallName, 6, 10);
        Showtime showtime = new Showtime(
                movie, hall, Instant.now(), Instant.now().plus(Duration.ofHours(2)), new BigDecimal("25.00"));
        ReflectionTestUtils.setField(showtime, "id", id);
        return showtime;
    }

    private Seat seat(String rowLabel, int columnNumber, SeatType seatType) {
        Cinema cinema = new Cinema("CineVerse Downtown", "1 Main St");
        Hall hall = new Hall(cinema, "Hall 1", 6, 10);
        return new Seat(hall, rowLabel, columnNumber, seatType);
    }
}
