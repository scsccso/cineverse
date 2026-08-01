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
}
