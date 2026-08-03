package com.cineverse.backend.ticket.service;

import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.entity.BookingSeat;
import com.cineverse.backend.booking.entity.BookingStatus;
import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.booking.repository.BookingSeatRepository;
import com.cineverse.backend.showtime.entity.Showtime;
import com.cineverse.backend.ticket.dto.TicketRedemptionResponse;
import com.cineverse.backend.ticket.exception.BookingNotConfirmedException;
import com.cineverse.backend.ticket.exception.TicketAlreadyRedeemedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Check-in / redemption. Not time-gated to the showtime's start time in
 * this Phase's scope — a ticket is valid to redeem any time after payment
 * confirms it, and rejected exactly once it's already been used; whether to
 * also restrict redemption to a window around the showtime is a separate,
 * unaddressed concern (see CLAUDE.md Phase 7).
 */
@Service
public class TicketService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final TicketCodeService ticketCodeService;

    public TicketService(
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            TicketCodeService ticketCodeService) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.ticketCodeService = ticketCodeService;
    }

    @Transactional
    public TicketRedemptionResponse redeem(String ticketCode) {
        UUID bookingId = ticketCodeService.verify(ticketCode);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BookingNotConfirmedException(booking.getStatus());
        }
        if (booking.getRedeemedAt() != null) {
            throw new TicketAlreadyRedeemedException(booking.getRedeemedAt());
        }

        Instant redeemedAt = Instant.now();
        booking.markRedeemed(redeemedAt);
        bookingRepository.saveAndFlush(booking);

        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
        Showtime showtime = booking.getShowtime();
        return new TicketRedemptionResponse(
                booking.getId(),
                showtime.getMovie().getTitle(),
                showtime.getHall().getName(),
                showtime.getStartTime(),
                bookingSeats.stream()
                        .map(bookingSeat -> new TicketRedemptionResponse.SeatSummary(
                                bookingSeat.getSeat().getRowLabel(),
                                bookingSeat.getSeat().getColumnNumber(),
                                bookingSeat.getSeat().getSeatType()))
                        .toList(),
                redeemedAt);
    }
}
