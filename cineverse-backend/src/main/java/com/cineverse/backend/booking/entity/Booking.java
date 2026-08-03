package com.cineverse.backend.booking.entity;

import com.cineverse.backend.showtime.entity.Showtime;
import com.cineverse.backend.user.entity.User;
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
 * No generic status setter — only the transitions each phase actually
 * performs are exposed (markExpired/markCancelled from Phase 5,
 * markConfirmed from Phase 6's payment flow, markRedeemed from Phase 7's
 * check-in flow). expires_at itself is never extended for Stripe's sake —
 * see CLAUDE.md Phase 6 for why the 5-minute hold is left untouched and
 * Stripe's own Checkout Session is proactively expired instead when this
 * booking is released.
 */
@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** NULL = not yet checked in; non-null = the check-in instant — see V12. */
    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    public Booking(User user, Showtime showtime, BigDecimal totalPrice, Instant expiresAt) {
        this.user = user;
        this.showtime = showtime;
        this.status = BookingStatus.PENDING;
        this.totalPrice = totalPrice;
        this.expiresAt = expiresAt;
    }

    public void markExpired() {
        this.status = BookingStatus.EXPIRED;
    }

    public void markCancelled() {
        this.status = BookingStatus.CANCELLED;
    }

    public void markConfirmed() {
        this.status = BookingStatus.CONFIRMED;
    }

    public void markRedeemed(Instant redeemedAt) {
        this.redeemedAt = redeemedAt;
    }
}
