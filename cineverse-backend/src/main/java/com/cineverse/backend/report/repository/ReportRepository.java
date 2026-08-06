package com.cineverse.backend.report.repository;

import com.cineverse.backend.report.dto.SalesBucket;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Hand-written native SQL behind the reports, deliberately not JPA/Hibernate
 * entity queries — the point of this Phase is real GROUP BY/date_trunc
 * aggregation running in Postgres, not pulling rows into Java and summing
 * them with streams (see CLAUDE.md Phase 8). {@link NamedParameterJdbcTemplate}
 * is autoconfigured by Spring Boot from the same DataSource JPA already uses
 * (spring-boot-starter-data-jpa pulls in spring-jdbc transitively), so this
 * doesn't need its own connection management or a new dependency.
 *
 * <p>Every timestamp bound into these queries is a {@link OffsetDateTime} at
 * UTC, never a raw {@code java.sql.Timestamp}/{@code Instant} — the pgjdbc
 * driver maps {@code OffsetDateTime} to {@code timestamptz} unambiguously
 * regardless of the JVM's default timezone, which matters here because the
 * sales query also does {@code ... AT TIME ZONE :zone} on the same bound
 * value to bucket by the cinema's local calendar day (see
 * CINEMA_ZONE in ReportService) — a value whose wire representation depended
 * on the JVM default zone would silently shift bucket boundaries.
 */
@Repository
public class ReportRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReportRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * One row per bucket in [from, to), gap-filled via {@code generate_series}
     * so a day/week/month with zero revenue still comes back as a
     * zero-value point instead of being absent — the frontend chart never
     * has to invent that gap-fill itself. Movie/hall filters are appended as
     * plain SQL predicates (not an {@code IS NULL OR} trick) only when
     * non-null, which sidesteps Postgres's parameter-type-inference
     * ambiguity for a bind variable compared against nothing.
     */
    public List<SalesBucket> salesBuckets(
            String granularitySql, Instant from, Instant to, UUID movieId, UUID hallId, String cinemaZone) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("granularity", granularitySql)
                .addValue("zone", cinemaZone)
                .addValue("from", toOffsetDateTime(from))
                .addValue("to", toOffsetDateTime(to));
        String filterClause = movieAndHallFilter(params, movieId, hallId, "s.");

        String sql =
                """
                WITH buckets AS (
                    SELECT generate_series(
                        date_trunc(:granularity, (:from AT TIME ZONE :zone)),
                        date_trunc(:granularity, ((:to - interval '1 second') AT TIME ZONE :zone)),
                        (('1 ' || :granularity))::interval
                    )::date AS bucket_date
                ),
                sales AS (
                    SELECT (date_trunc(:granularity, p.updated_at AT TIME ZONE :zone))::date AS bucket_date,
                           SUM(p.amount) AS revenue,
                           COUNT(DISTINCT b.id) AS booking_count
                    FROM payments p
                    JOIN bookings b ON b.id = p.booking_id
                    JOIN showtimes s ON s.id = b.showtime_id
                    WHERE p.status = 'SUCCEEDED'
                      AND b.status = 'CONFIRMED'
                      AND p.updated_at >= :from
                      AND p.updated_at < :to
                      %s
                    GROUP BY 1
                )
                SELECT buckets.bucket_date AS bucket_date,
                       COALESCE(sales.revenue, 0) AS revenue,
                       COALESCE(sales.booking_count, 0) AS booking_count
                FROM buckets
                LEFT JOIN sales ON sales.bucket_date = buckets.bucket_date
                ORDER BY buckets.bucket_date
                """
                        .formatted(filterClause);

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new SalesBucket(
                        rs.getObject("bucket_date", java.time.LocalDate.class),
                        rs.getBigDecimal("revenue"),
                        rs.getLong("booking_count")));
    }

    /**
     * Same shape as the sales revenue query but filtered by a caller-chosen
     * payment status with no bucketing — used both for CONFIRMED+SUCCEEDED
     * revenue (via {@link #salesBuckets}, which sums its own buckets in
     * ReportService rather than re-querying) and, with status=
     * ORPHANED_SUCCESS, for the "pending reconciliation" total. Deliberately
     * does NOT filter on booking.status — ORPHANED_SUCCESS by definition
     * means the booking was no longer PENDING when the payment succeeded, so
     * requiring CONFIRMED here would always return zero.
     */
    public BigDecimal sumAmountByStatus(String status, Instant from, Instant to, UUID movieId, UUID hallId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("status", status)
                .addValue("from", toOffsetDateTime(from))
                .addValue("to", toOffsetDateTime(to));
        String filterClause = movieAndHallFilter(params, movieId, hallId, "s.");

        String sql =
                """
                SELECT COALESCE(SUM(p.amount), 0) AS amount
                FROM payments p
                JOIN bookings b ON b.id = p.booking_id
                JOIN showtimes s ON s.id = b.showtime_id
                WHERE p.status = :status
                  AND p.updated_at >= :from
                  AND p.updated_at < :to
                  %s
                """
                        .formatted(filterClause);

        BigDecimal result = jdbcTemplate.queryForObject(sql, params, BigDecimal.class);
        return result != null ? result : BigDecimal.ZERO;
    }

    /**
     * Per-showtime seat totals + CONFIRMED-booked counts in [from, to) on
     * {@code start_time}. {@code total_seats} is a correlated COUNT
     * subquery per hall rather than a pre-joined GROUP BY — at this
     * project's scale (a handful of halls) that's clearer to read than the
     * join-then-dedupe alternative and costs nothing measurable. The booked
     * count is a real GROUP BY in its own subquery (not a Java-side count)
     * so a showtime with zero CONFIRMED bookings still gets a row via the
     * LEFT JOIN + COALESCE, rather than being silently absent.
     */
    public List<ShowtimeOccupancyRow> occupancyRows(Instant from, Instant to, UUID hallId, UUID movieId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", toOffsetDateTime(from))
                .addValue("to", toOffsetDateTime(to));
        String filterClause = movieAndHallFilter(params, movieId, hallId, "s.");

        String sql =
                """
                SELECT s.id AS showtime_id,
                       m.title AS movie_title,
                       h.id AS hall_id,
                       h.name AS hall_name,
                       s.start_time AS start_time,
                       (SELECT COUNT(*) FROM seats st WHERE st.hall_id = h.id) AS total_seats,
                       COALESCE(booked.booked_count, 0) AS booked_seats
                FROM showtimes s
                JOIN movies m ON m.id = s.movie_id
                JOIN halls h ON h.id = s.hall_id
                LEFT JOIN (
                    SELECT b.showtime_id, COUNT(bs.id) AS booked_count
                    FROM bookings b
                    JOIN booking_seats bs ON bs.booking_id = b.id
                    WHERE b.status = 'CONFIRMED'
                    GROUP BY b.showtime_id
                ) booked ON booked.showtime_id = s.id
                WHERE s.start_time >= :from AND s.start_time < :to
                  %s
                ORDER BY s.start_time
                """
                        .formatted(filterClause);

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new ShowtimeOccupancyRow(
                        (UUID) rs.getObject("showtime_id"),
                        rs.getString("movie_title"),
                        (UUID) rs.getObject("hall_id"),
                        rs.getString("hall_name"),
                        rs.getTimestamp("start_time").toInstant(),
                        rs.getLong("total_seats"),
                        rs.getLong("booked_seats")));
    }

    private String movieAndHallFilter(MapSqlParameterSource params, UUID movieId, UUID hallId, String showtimeAlias) {
        StringBuilder clause = new StringBuilder();
        if (movieId != null) {
            clause.append(" AND ").append(showtimeAlias).append("movie_id = :movieId");
            params.addValue("movieId", movieId);
        }
        if (hallId != null) {
            clause.append(" AND ").append(showtimeAlias).append("hall_id = :hallId");
            params.addValue("hallId", hallId);
        }
        return clause.toString();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
