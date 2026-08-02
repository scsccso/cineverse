package com.cineverse.backend.showtime.entity;

import com.cineverse.backend.cinema.entity.Hall;
import com.cineverse.backend.movie.entity.Movie;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * No setters: there is deliberately no update path (see
 * ShowtimeService/ShowtimeController) — a mis-scheduled showtime is deleted
 * and recreated, not patched, so partial mutation isn't exposed here either.
 */
@Entity
@Table(name = "showtimes")
@Getter
@NoArgsConstructor
public class Showtime {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hall_id", nullable = false)
    private Hall hall;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    /** start_time + movie.durationMinutes only — the cleanup buffer is not baked in, see V7 migration. */
    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal price;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Showtime(Movie movie, Hall hall, Instant startTime, Instant endTime, BigDecimal price) {
        this.movie = movie;
        this.hall = hall;
        this.startTime = startTime;
        this.endTime = endTime;
        this.price = price;
    }
}
