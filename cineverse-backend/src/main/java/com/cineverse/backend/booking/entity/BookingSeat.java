package com.cineverse.backend.booking.entity;

import com.cineverse.backend.cinema.entity.Seat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Pure junction/snapshot row — immutable once created, no timestamps (unlike
 * every other entity in this codebase): it never changes after the booking
 * that owns it is created, so there's nothing for created_at/updated_at to
 * track.
 */
@Entity
@Table(name = "booking_seats")
@Getter
@NoArgsConstructor
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(name = "price_at_booking", nullable = false, precision = 8, scale = 2)
    private BigDecimal priceAtBooking;

    public BookingSeat(Booking booking, Seat seat, BigDecimal priceAtBooking) {
        this.booking = booking;
        this.seat = seat;
        this.priceAtBooking = priceAtBooking;
    }
}
