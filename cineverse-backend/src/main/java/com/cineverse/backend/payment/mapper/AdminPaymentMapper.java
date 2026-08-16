package com.cineverse.backend.payment.mapper;

import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.payment.dto.AdminPaymentResponse;
import com.cineverse.backend.payment.entity.Payment;
import com.cineverse.backend.showtime.entity.Showtime;
import org.springframework.stereotype.Component;

/**
 * Not a MapStruct mapper, same reasoning as BookingMapper: MapStruct is the
 * right tool for a flat field-to-field copy (see UserMapper/CinemaMapper),
 * but this reaches five hops deep (payment -> booking -> user/showtime ->
 * movie/hall) to assemble one nested record, which reads more clearly as
 * plain imperative assembly than as a declarative mapping — and this
 * project has already been burned once by trusting MapStruct's generated
 * code implicitly (see CLAUDE.md "Admin 用户管理"), so an explicit method
 * body is the more trustworthy choice here too.
 */
@Component
public class AdminPaymentMapper {

    public AdminPaymentResponse toResponse(Payment payment) {
        Booking booking = payment.getBooking();
        Showtime showtime = booking.getShowtime();
        return new AdminPaymentResponse(
                payment.getId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStripeSessionId(),
                payment.getStripePaymentIntentId(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                new AdminPaymentResponse.BookingSummary(
                        booking.getId(),
                        booking.getStatus(),
                        booking.getUser().getEmail(),
                        showtime.getMovie().getTitle(),
                        showtime.getHall().getName(),
                        showtime.getStartTime()));
    }
}
