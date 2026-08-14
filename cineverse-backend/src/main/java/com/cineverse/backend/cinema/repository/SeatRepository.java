package com.cineverse.backend.cinema.repository;

import com.cineverse.backend.cinema.entity.Seat;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByHallIdOrderByRowLabelAscColumnNumberAsc(UUID hallId);

    /**
     * Bookable seat units per hall (one row per COUPLE seat, not two — see
     * Seat's own row/column semantics), batched across every hall id
     * involved in one showtime list/query rather than one lookup per
     * showtime.
     */
    @Query("select new com.cineverse.backend.cinema.repository.HallSeatCount(s.hall.id, count(s.id)) "
            + "from Seat s where s.hall.id in :hallIds group by s.hall.id")
    List<HallSeatCount> countByHallIdIn(@Param("hallIds") Collection<UUID> hallIds);
}
