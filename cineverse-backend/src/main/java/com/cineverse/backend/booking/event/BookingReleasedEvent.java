package com.cineverse.backend.booking.event;

import java.util.UUID;

/**
 * Published whenever a booking transitions away from PENDING without being
 * confirmed — lazily expired (hold elapsed) or explicitly cancelled. Booking
 * itself has no idea who (if anyone) is listening; this is what lets
 * Phase 6's payment module react (proactively expiring any in-flight Stripe
 * Checkout Session for it) without booking depending on payment at all. See
 * BookingService for the publish sites and PaymentService.onBookingReleased
 * for the (only) current listener.
 */
public record BookingReleasedEvent(UUID bookingId) {
}
