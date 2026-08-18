package com.cineverse.backend.movie.repository;

import com.cineverse.backend.movie.entity.MovieStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovieStatusHistoryRepository extends JpaRepository<MovieStatusHistory, UUID> {

    /** Join-fetches changedBy so rendering a movie's whole timeline (a
     * handful of rows) doesn't N+1 into a separate users lookup per row. */
    @Query("select h from MovieStatusHistory h left join fetch h.changedBy where h.movie.id = :movieId "
            + "order by h.changedAt desc")
    List<MovieStatusHistory> findByMovieIdOrderByChangedAtDesc(@Param("movieId") UUID movieId);
}
