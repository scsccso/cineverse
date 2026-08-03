package com.cineverse.backend.payment.entity;

import com.cineverse.backend.booking.entity.Booking;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One row per Stripe Checkout Session attempt — created PENDING the instant
 * the session is created (PaymentService.createCheckoutSession), then
 * transitioned to a terminal state by the webhook. A single booking can have
 * more than one row (an expired/abandoned attempt followed by a successful
 * retry): see V10.
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "stripe_session_id", nullable = false, unique = true)
    private String stripeSessionId;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Payment(Booking booking, String stripeSessionId, BigDecimal amount, String currency) {
        this.booking = booking;
        this.stripeSessionId = stripeSessionId;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.PENDING;
    }

    public void markSucceeded(String stripePaymentIntentId) {
        this.status = PaymentStatus.SUCCEEDED;
        this.stripePaymentIntentId = stripePaymentIntentId;
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public void markOrphanedSuccess(String stripePaymentIntentId) {
        this.status = PaymentStatus.ORPHANED_SUCCESS;
        this.stripePaymentIntentId = stripePaymentIntentId;
    }
}
