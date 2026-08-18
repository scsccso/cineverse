package com.cineverse.backend.movie.entity;

import com.cineverse.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One row per movie status change, plus the initial row MovieService.create
 * writes with fromStatus = null. Immutable audit trail — no setters, no
 * code path is ever expected to modify an existing row.
 */
@Entity
@Table(name = "movie_status_history")
@Getter
@NoArgsConstructor
public class MovieStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private MovieStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private MovieStatus toStatus;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    /** Nullable — ON DELETE SET NULL (see V18): the row survives even if the
     * acting admin's account is later deleted, only the "who" is lost. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    public MovieStatusHistory(Movie movie, MovieStatus fromStatus, MovieStatus toStatus, User changedBy) {
        this.movie = movie;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
    }
}
