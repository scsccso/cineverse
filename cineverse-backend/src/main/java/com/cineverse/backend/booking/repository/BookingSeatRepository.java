package com.cineverse.backend.booking.repository;

import com.cineverse.backend.booking.entity.BookingSeat;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, UUID> {

    List<BookingSeat> findByBookingId(UUID bookingId);

    /**
     * Seat identity only. Callers of this one (activeSeatStatuses) read
     * nothing but {@code getSeat().getId()}, which a lazy proxy answers
     * without hitting the database — so no fetch join is needed or wanted
     * here.
     */
    List<BookingSeat> findByBookingIdIn(Collection<UUID> bookingIds);

    /**
     * Same rows as {@link #findByBookingIdIn}, but with the seat itself
     * fetched. The order-history mapper reads rowLabel/columnNumber/seatType
     * off every seat, which lazy proxies would answer with one query each;
     * this keeps a whole history at two queries total.
     */
    @Query("select bs from BookingSeat bs join fetch bs.seat where bs.booking.id in :bookingIds")
    List<BookingSeat> findByBookingIdInWithSeat(@Param("bookingIds") Collection<UUID> bookingIds);
}
