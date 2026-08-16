package com.cineverse.backend.movie.repository;

import com.cineverse.backend.movie.entity.Genre;
import com.cineverse.backend.movie.entity.Movie;
import com.cineverse.backend.movie.entity.MovieStatus;
import jakarta.persistence.criteria.Join;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class MovieSpecifications {

    private MovieSpecifications() {
    }

    public static Specification<Movie> hasStatus(MovieStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Movie> hasGenre(UUID genreId) {
        return (root, query, cb) -> {
            if (genreId == null) {
                return null;
            }
            query.distinct(true);
            Join<Movie, Genre> genres = root.join("genres");
            return cb.equal(genres.get("id"), genreId);
        };
    }

    /**
     * Case-insensitive "contains" match on title. Built with
     * {@code CriteriaBuilder.like}/{@code lower} rather than a hand-written
     * JPQL string — the Criteria API binds the pattern as a single,
     * unambiguously-typed parameter, which is exactly the class of thing
     * that broke on Postgres for {@code lower(concat('%', ?, '%'))} in raw
     * JPQL (see BookingRepository.search's doc comment for the full story);
     * Specifications were never at risk of that bug, not because of any
     * special care taken here, but because Criteria API expressions carry
     * their Java type through to the bind parameter by construction.
     */
    public static Specification<Movie> hasTitleContaining(String title) {
        return (root, query, cb) -> {
            if (title == null || title.isBlank()) {
                return null;
            }
            return cb.like(cb.lower(root.get("title")), "%" + title.trim().toLowerCase() + "%");
        };
    }
}
