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

    // Backs MovieService.delete()'s "still has scheduled showtimes" 409 —
    // deleting a movie must not cascade-delete its showtimes, see V8.
    boolean existsByMovieId(UUID movieId);

    // No existsByHallId yet — Hall has no delete endpoint (Phase 3 MVP
    // boundary), so there's no caller for it. Whenever that endpoint is
    // added, HallService.delete should add one and guard the same way
    // MovieService.delete guards on existsByMovieId, rather than relying
    // only on the V8 FK RESTRICT backstop to surface a raw DB error.
}
