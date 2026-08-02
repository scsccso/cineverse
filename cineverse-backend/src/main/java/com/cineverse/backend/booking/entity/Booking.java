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
 * No generic status setter — only the two transitions Phase 5 actually
 * performs (markExpired/markCancelled) are exposed. markConfirmed() belongs
 * to whichever Phase 6 payment-success handler first needs it.
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
}
