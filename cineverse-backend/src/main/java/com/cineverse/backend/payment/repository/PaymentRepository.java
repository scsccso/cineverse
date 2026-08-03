package com.cineverse.backend.payment.repository;

import com.cineverse.backend.payment.entity.Payment;
import com.cineverse.backend.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Row-locked lookup used only by webhook processing
     * (PaymentService.handleCheckoutSessionCompleted/Expired): two
     * concurrent deliveries of the same Stripe event must not both observe
     * "still PENDING" and both act. The first caller's transaction holds
     * this row lock until commit; the second blocks until then and sees the
     * already-terminal status, so it no-ops instead of double-processing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.stripeSessionId = :stripeSessionId")
    Optional<Payment> findByStripeSessionIdForUpdate(String stripeSessionId);

    /** Backs PaymentService.onBookingReleased — every still-open Checkout Session attempt for a booking that just got released. */
    List<Payment> findByBookingIdAndStatus(UUID bookingId, PaymentStatus status);
}
