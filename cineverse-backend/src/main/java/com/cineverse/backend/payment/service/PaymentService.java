package com.cineverse.backend.payment.service;

import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.event.BookingReleasedEvent;
import com.cineverse.backend.booking.service.BookingService;
import com.cineverse.backend.config.FrontendProperties;
import com.cineverse.backend.payment.config.StripeProperties;
import com.cineverse.backend.payment.dto.CheckoutSessionResponse;
import com.cineverse.backend.payment.entity.Payment;
import com.cineverse.backend.payment.entity.PaymentStatus;
import com.cineverse.backend.payment.exception.InvalidStripeSignatureException;
import com.cineverse.backend.payment.exception.StripeGatewayException;
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
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
public class PaymentService {

    /**
     * Stripe's own hard floor — a Checkout Session cannot be made to expire
     * in under 30 minutes. Phase 5's 5-minute seat hold is deliberately left
     * unchanged rather than stretched to match this (see CLAUDE.md Phase 6
     * for the full trade-off write-up): the mismatch is instead closed by
     * proactively expiring the Stripe session the moment the booking behind
     * it is released — see onBookingReleased.
     */
    static final Duration STRIPE_SESSION_DURATION = Duration.ofMinutes(30);

    private final BookingService bookingService;
    private final PaymentRepository paymentRepository;
    private final StripeCheckoutGateway stripeCheckoutGateway;
    private final StripeProperties stripeProperties;
    private final FrontendProperties frontendProperties;

    public PaymentService(
            BookingService bookingService,
            PaymentRepository paymentRepository,
            StripeCheckoutGateway stripeCheckoutGateway,
            StripeProperties stripeProperties,
            FrontendProperties frontendProperties) {
        this.bookingService = bookingService;
        this.paymentRepository = paymentRepository;
        this.stripeCheckoutGateway = stripeCheckoutGateway;
        this.stripeProperties = stripeProperties;
        this.frontendProperties = frontendProperties;
    }

    @Transactional
    public CheckoutSessionResponse createCheckoutSession(UUID userId, UUID bookingId) {
        Booking booking = bookingService.loadPendingBookingForCheckout(userId, bookingId);

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
     * already-terminal-status check makes a redelivered/duplicated event a
     * no-op — no separate processed-event-id log is needed because this
     * event type can only meaningfully fire once per session's lifecycle
     * (see CLAUDE.md Phase 6 for the full reasoning).
     *
     * <p>The booking may no longer be PENDING by the time this arrives (it
     * can be lazily expired the instant its 5-minute hold elapses, well
     * before Stripe's own 30-minute session floor — see
     * onBookingReleased for why we don't just wait for that to happen
     * passively). When that's the case, the payment is recorded as
     * {@link PaymentStatus#ORPHANED_SUCCESS} instead of being silently
     * dropped or incorrectly resurrecting the booking to CONFIRMED — money
     * was captured for a booking that may no longer own its seat, which
     * needs a human to reconcile, not an automatic refund (out of scope for
     * this MVP).
     */
    private void handleCheckoutSessionCompleted(Event event) {
        Session session = extractSession(event);
        Payment payment = paymentRepository
                .findByStripeSessionIdForUpdate(session.getId())
                .orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        boolean confirmed = bookingService.confirmIfPending(payment.getBooking().getId());
        if (confirmed) {
            payment.markSucceeded(session.getPaymentIntent());
        } else {
            log.warn(
                    "Stripe session {} completed successfully but booking {} was no longer PENDING — "
                            + "marking payment ORPHANED_SUCCESS for manual reconciliation",
                    session.getId(), payment.getBooking().getId());
            payment.markOrphanedSuccess(session.getPaymentIntent());
        }
        paymentRepository.saveAndFlush(payment);
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
     * Reacts to a booking being released (lazily expired or cancelled —
     * published by BookingService, which has no idea this listener, or
     * payments at all, exist) by proactively telling Stripe to close out
     * any Checkout Session still open for it, rather than waiting up to 30
     * minutes for Stripe's own floor to do it. {@code AFTER_COMMIT} so this
     * only runs once the booking's released status is durably persisted —
     * calling Stripe based on a transaction that might still roll back
     * would be worse than not calling it at all.
     *
     * <p>A session that can't be expired (most often: it already completed
     * — the payment succeeded in the narrow window before this ran) is not
     * an error to propagate. That race is exactly what
     * handleCheckoutSessionCompleted's ORPHANED_SUCCESS path exists for:
     * whichever event Stripe sends next resolves it, so this listener just
     * logs and leaves the Payment row as-is.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingReleased(BookingReleasedEvent event) {
        List<Payment> openAttempts = paymentRepository.findByBookingIdAndStatus(event.bookingId(), PaymentStatus.PENDING);
        for (Payment payment : openAttempts) {
            try {
                stripeCheckoutGateway.expireSession(payment.getStripeSessionId());
                payment.markFailed();
                paymentRepository.saveAndFlush(payment);
            } catch (StripeGatewayException e) {
                log.warn(
                        "Could not proactively expire Stripe session {} for released booking {} "
                                + "(likely already completed/expired on Stripe's side): {}",
                        payment.getStripeSessionId(), event.bookingId(), e.getMessage());
            }
        }
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
