package com.cineverse.backend.cinema.repository;

import com.cineverse.backend.cinema.entity.Seat;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByHallIdOrderByRowLabelAscColumnNumberAsc(UUID hallId);
}
