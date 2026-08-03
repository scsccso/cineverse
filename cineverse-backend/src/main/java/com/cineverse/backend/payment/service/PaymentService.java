package com.cineverse.backend.payment.service;

import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.booking.repository.BookingSeatRepository;
import com.cineverse.backend.booking.service.BookingService;
import com.cineverse.backend.booking.service.SeatLockService;
import com.cineverse.backend.config.FrontendProperties;
import com.cineverse.backend.payment.config.StripeProperties;
import com.cineverse.backend.payment.dto.CheckoutSessionResponse;
import com.cineverse.backend.payment.entity.Payment;
import com.cineverse.backend.payment.entity.PaymentStatus;
import com.cineverse.backend.payment.exception.InvalidStripeSignatureException;
import com.cineverse.backend.payment.gateway.CheckoutSessionSpec;
import com.cineverse.backend.payment.gateway.CreatedCheckoutSession;
import com.cineverse.backend.payment.gateway.StripeCheckoutGateway;
import com.cineverse.backend.payment.repository.PaymentRepository;
import com.cineverse.backend.showtime.entity.Showtime;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    /** Stripe's own hard floor — a Checkout Session cannot be made to expire in under 30 minutes. */
    static final Duration STRIPE_SESSION_DURATION = Duration.ofMinutes(30);

    /**
     * A few minutes longer than the Stripe session itself, so a payment
     * completed at the very last second of Stripe's window is never
     * rejected as already-expired on our side. This — not Phase 5's
     * 5-minute seat-selection hold — is what the booking's expires_at is
     * extended to the moment checkout begins; see Booking.extendHold.
     */
    static final Duration CHECKOUT_HOLD_DURATION = Duration.ofMinutes(35);

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final PaymentRepository paymentRepository;
    private final SeatLockService seatLockService;
    private final StripeCheckoutGateway stripeCheckoutGateway;
    private final StripeProperties stripeProperties;
    private final FrontendProperties frontendProperties;

    public PaymentService(
            BookingService bookingService,
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            PaymentRepository paymentRepository,
            SeatLockService seatLockService,
            StripeCheckoutGateway stripeCheckoutGateway,
            StripeProperties stripeProperties,
            FrontendProperties frontendProperties) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.paymentRepository = paymentRepository;
        this.seatLockService = seatLockService;
        this.stripeCheckoutGateway = stripeCheckoutGateway;
        this.stripeProperties = stripeProperties;
        this.frontendProperties = frontendProperties;
    }

    @Transactional
    public CheckoutSessionResponse createCheckoutSession(UUID userId, UUID bookingId) {
        Booking booking = bookingService.loadPendingBookingForCheckout(userId, bookingId);

        // Extend the hold to cover a realistic payment-page duration — both
        // the DB row and the Redis seat locks, kept in lockstep so neither
        // can outlive the other (see CHECKOUT_HOLD_DURATION javadoc).
        Instant newExpiresAt = Instant.now().plus(CHECKOUT_HOLD_DURATION);
        booking.extendHold(newExpiresAt);
        bookingRepository.saveAndFlush(booking);
        bookingSeatRepository.findByBookingId(bookingId)
                .forEach(bookingSeat -> seatLockService.extend(
                        booking.getShowtime().getId(), bookingSeat.getSeat().getId(), CHECKOUT_HOLD_DURATION));

        Showtime showtime = booking.getShowtime();
        String productName = "%s · %s".formatted(showtime.getMovie().getTitle(), showtime.getHall().getName());

        CreatedCheckoutSession session = stripeCheckoutGateway.createSession(new CheckoutSessionSpec(
                booking.getId(),
                booking.getTotalPrice(),
                stripeProperties.currency(),
                productName,
                frontendProperties.baseUrl() + "/bookings/" + booking.getId()
                        + "/confirmed?session_id={CHECKOUT_SESSION_ID}",
                // Sends the user back to the seat page with the booking id
                // it should resume — see seat-picker.tsx's initialBookingId
                // handling. Without this, a cancelled/abandoned checkout
                // would land back on an ordinary seat grid with no way to
                // reach the pay screen again for this same PENDING booking.
                frontendProperties.baseUrl() + "/showtimes/" + showtime.getId() + "/seats?bookingId=" + booking.getId(),
                Instant.now().plus(STRIPE_SESSION_DURATION)));

        Payment payment = new Payment(booking, session.sessionId(), booking.getTotalPrice(), stripeProperties.currency());
        paymentRepository.saveAndFlush(payment);

        return new CheckoutSessionResponse(session.url());
    }

    @Transactional
    public void handleWebhookEvent(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeProperties.webhookSecret());
        } catch (SignatureVerificationException e) {
            throw new InvalidStripeSignatureException("Stripe webhook signature verification failed", e);
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutSessionCompleted(event);
            case "checkout.session.expired" -> handleCheckoutSessionExpired(event);
            default -> {
                // Not a type this app acts on — still a 200 (per Stripe's
                // guidance: don't make Stripe retry a type you don't handle).
            }
        }
    }

    /**
     * Idempotency anchor: the row-locked lookup by stripe_session_id
     * (unique) serializes concurrent deliveries of the same event, and the
     * already-SUCCEEDED check makes a redelivered/duplicated event a no-op
     * — no separate processed-event-id log is needed because this event
     * type can only meaningfully fire once per session's lifecycle (see
     * CLAUDE.md Phase 6 for the full reasoning).
     */
    private void handleCheckoutSessionCompleted(Event event) {
        Session session = extractSession(event);
        Payment payment = paymentRepository
                .findByStripeSessionIdForUpdate(session.getId())
                .orElse(null);
        if (payment == null || payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return;
        }

        payment.markSucceeded(session.getPaymentIntent());
        paymentRepository.saveAndFlush(payment);

        // See BookingService.confirmIfPending: a false return here means
        // the payment was captured but the booking could not be safely
        // confirmed (already confirmed by another attempt, or its hold had
        // already lapsed) — deliberately left as a flagged edge case rather
        // than auto-refunded; see CLAUDE.md Phase 6.
        bookingService.confirmIfPending(payment.getBooking().getId());
    }

    private void handleCheckoutSessionExpired(Event event) {
        Session session = extractSession(event);
        Payment payment = paymentRepository
                .findByStripeSessionIdForUpdate(session.getId())
                .orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        payment.markFailed();
        paymentRepository.saveAndFlush(payment);
        // Booking is deliberately left untouched — Phase 5's lazy expiry
        // already handles a PENDING booking whose hold has lapsed.
    }

    /**
     * getObject() only returns a value when the event's api_version exactly
     * matches this SDK build's pinned Stripe#API_VERSION — a real
     * production risk, not a testing nuisance: a live Stripe account can be
     * (and by default is) configured to send whatever API version it was
     * last pinned to in the Dashboard, which won't necessarily match
     * whatever stripe-java version this app happens to be built against.
     * Falling back to deserializeUnsafe() (Stripe's own documented escape
     * hatch) avoids a permanent, un-retriable-into-success 500 on every
     * future webhook delivery just because of a version skew — safe here
     * because the only fields this app reads (session id, payment_intent)
     * are stable across API versions.
     */
    private Session extractSession(Event event) {
        return (Session) event.getDataObjectDeserializer().getObject().orElseGet(() -> deserializeUnsafe(event));
    }

    private StripeObject deserializeUnsafe(Event event) {
        try {
            return event.getDataObjectDeserializer().deserializeUnsafe();
        } catch (EventDataObjectDeserializationException e) {
            throw new IllegalStateException(
                    "Stripe event payload could not be deserialized: " + event.getId(), e);
        }
    }
}
