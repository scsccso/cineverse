package com.cineverse.backend.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row of GET /api/v1/admin/bookings. Wraps the existing BookingResponse
 * rather than duplicating its fields or adding userEmail onto it directly —
 * BookingResponse is also the shape GET /bookings/{id} and GET /bookings
 * return to customers, and neither of those callers has any use for another
 * user's email. Adding it there would mean either fetch-joining Booking.user
 * on every customer read (wasted, since a customer already knows their own
 * email) or leaving it null there and only-sometimes-populated, which is a
 * worse contract than a small purpose-built wrapper.
 */
public record AdminBookingSearchResult(
        @Schema(example = "moviefan@example.com") String userEmail,
        BookingResponse booking) {
}
