package com.cineverse.backend.booking.repository;

import com.cineverse.backend.booking.entity.BookingSeat;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, UUID> {

    List<BookingSeat> findByBookingId(UUID bookingId);

    List<BookingSeat> findByBookingIdIn(Collection<UUID> bookingIds);
}
