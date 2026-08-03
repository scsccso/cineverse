package com.cineverse.backend.booking.mapper;

import com.cineverse.backend.booking.dto.BookingResponse;
import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.entity.BookingSeat;
import com.cineverse.backend.booking.entity.BookingStatus;
import com.cineverse.backend.cinema.entity.Seat;
import com.cineverse.backend.showtime.entity.Showtime;
import com.cineverse.backend.ticket.service.TicketCodeService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Not a MapStruct mapper: its one method combines two separately-fetched
 * inputs (a Booking plus its List&lt;BookingSeat&gt; — neither entity holds
 * a bidirectional collection, matching how Hall/Seat and Movie/Showtime are
 * modeled elsewhere in this codebase), which is imperative assembly rather
 * than the declarative field-to-field mapping MapStruct is meant for.
 *
 * <p>Depends on TicketCodeService (Phase 7) purely as a stateless signing
 * utility — not a circular dependency on the ticket package's business
 * logic, since TicketCodeService itself has zero awareness of Booking; only
 * TicketService (the check-in orchestration) depends back on booking.
 */
@Component
public class BookingMapper {

    private final TicketCodeService ticketCodeService;

    public BookingMapper(TicketCodeService ticketCodeService) {
        this.ticketCodeService = ticketCodeService;
    }

    public BookingResponse toResponse(Booking booking, List<BookingSeat> bookingSeats) {
        Showtime showtime = booking.getShowtime();
        boolean confirmed = booking.getStatus() == BookingStatus.CONFIRMED;
        return new BookingResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getTotalPrice(),
                booking.getExpiresAt(),
                booking.getCreatedAt(),
                new BookingResponse.ShowtimeSummary(
                        showtime.getId(),
                        showtime.getMovie().getTitle(),
                        showtime.getHall().getName(),
                        showtime.getStartTime()),
                bookingSeats.stream().map(this::toSeatResponse).toList(),
                confirmed ? ticketCodeService.sign(booking.getId()) : null,
                booking.getRedeemedAt());
    }

    private BookingResponse.BookingSeatResponse toSeatResponse(BookingSeat bookingSeat) {
        Seat seat = bookingSeat.getSeat();
        return new BookingResponse.BookingSeatResponse(
                seat.getId(),
                seat.getRowLabel(),
                seat.getColumnNumber(),
                seat.getSeatType(),
                bookingSeat.getPriceAtBooking());
    }
}
