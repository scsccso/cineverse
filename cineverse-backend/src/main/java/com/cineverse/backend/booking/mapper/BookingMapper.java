package com.cineverse.backend.booking.mapper;

import com.cineverse.backend.booking.dto.BookingResponse;
import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.entity.BookingSeat;
import com.cineverse.backend.cinema.entity.Seat;
import com.cineverse.backend.showtime.entity.Showtime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Not a MapStruct mapper: its one method combines two separately-fetched
 * inputs (a Booking plus its List&lt;BookingSeat&gt; — neither entity holds
 * a bidirectional collection, matching how Hall/Seat and Movie/Showtime are
 * modeled elsewhere in this codebase), which is imperative assembly rather
 * than the declarative field-to-field mapping MapStruct is meant for.
 */
@Component
public class BookingMapper {

    public BookingResponse toResponse(Booking booking, List<BookingSeat> bookingSeats) {
        Showtime showtime = booking.getShowtime();
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
                bookingSeats.stream().map(this::toSeatResponse).toList());
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
