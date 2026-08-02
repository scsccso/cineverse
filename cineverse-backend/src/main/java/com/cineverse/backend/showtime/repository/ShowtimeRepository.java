package com.cineverse.backend.showtime.repository;

import com.cineverse.backend.showtime.entity.Showtime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ShowtimeRepository extends JpaRepository<Showtime, UUID>, JpaSpecificationExecutor<Showtime> {

    // Fine at MVP scale (one cinema, a handful of halls) to load a hall's
    // full showtime list and check overlap in Java via ShowtimeOverlapChecker;
    // would need a narrower windowed query if this ever needs to scale.
    List<Showtime> findByHallId(UUID hallId);
}
