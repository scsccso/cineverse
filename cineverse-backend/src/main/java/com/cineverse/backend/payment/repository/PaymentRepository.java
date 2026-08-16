package com.cineverse.backend.payment.repository;

import com.cineverse.backend.payment.entity.Payment;
import com.cineverse.backend.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Admin support listing (GET /api/v1/admin/payments) — Phase 8's reports
     * only ever surfaced pendingReconciliationAmount as a single summed
     * figure; this is the drill-down that shows which specific rows make it
     * up. {@code status} is optional (null browses every payment, paginated).
     *
     * <p>Unlike BookingRepository.search's userEmail/movieTitle filters,
     * {@code status} is bound directly with no {@code lower()}/string
     * pattern involved — it's a plain enum equality comparison, so this
     * doesn't hit the Postgres parameter-type-inference issue documented on
     * that method (a type that's unambiguous from the comparison operator
     * alone was never the problem there; comparing a placeholder used only
     * inside {@code lower(concat(...))} was).
     *
     * <p>Fetch joins mirror BookingRepository.search's reasoning: every row
     * is mapped through AdminPaymentMapper, which reads the booking's owner
     * email and the showtime's movie/hall — all four are FetchType.LAZY on
     * their owning entities, so leaving any one out reintroduces N+1 for
     * that column specifically, not a query failure that would be caught by
     * simply running the tests once.
     */
    @Query(
            value = """
                    select p from Payment p
                    join fetch p.booking b
                    join fetch b.user u
                    join fetch b.showtime s
                    join fetch s.movie m
                    join fetch s.hall h
                    where (:status is null or p.status = :status)
                    order by p.updatedAt desc
                    """,
            countQuery = """
                    select count(p) from Payment p
                    where (:status is null or p.status = :status)
                    """)
    Page<Payment> search(@Param("status") PaymentStatus status, Pageable pageable);
}
