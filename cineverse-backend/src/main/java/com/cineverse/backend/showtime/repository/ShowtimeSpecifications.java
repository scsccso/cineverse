package com.cineverse.backend.showtime.repository;

import com.cineverse.backend.showtime.entity.Showtime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class ShowtimeSpecifications {

    private ShowtimeSpecifications() {
    }

    public static Specification<Showtime> hasMovie(UUID movieId) {
        return (root, query, cb) -> movieId == null ? null : cb.equal(root.get("movie").get("id"), movieId);
    }

    public static Specification<Showtime> hasHall(UUID hallId) {
        return (root, query, cb) -> hallId == null ? null : cb.equal(root.get("hall").get("id"), hallId);
    }

    /** Filters by start_time falling within the given calendar day, interpreted in UTC. */
    public static Specification<Showtime> startsOnDate(LocalDate date) {
        return (root, query, cb) -> {
            if (date == null) {
                return null;
            }
            Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            return cb.and(
                    cb.greaterThanOrEqualTo(root.get("startTime"), dayStart),
                    cb.lessThan(root.get("startTime"), dayEnd));
        };
    }
}
