package com.cineverse.backend.report.service;

import com.cineverse.backend.report.exception.InvalidReportRangeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Converts the inclusive {@code from}/{@code to} LocalDate range an admin
 * types into a filter into the half-open Instant range the SQL queries
 * actually bind ({@code [from, to)}). Pulled out as its own pure static
 * factory (no Spring/DB dependency) specifically so the date-boundary math —
 * the part of this Phase most likely to have an off-by-one — has a unit test
 * that doesn't need Testcontainers to run.
 */
public record ReportDateRange(Instant from, Instant to) {

    public static ReportDateRange of(LocalDate from, LocalDate to, ZoneId zone) {
        if (to.isBefore(from)) {
            throw new InvalidReportRangeException(
                    "`to` (" + to + ") must not be before `from` (" + from + ")");
        }
        Instant fromInstant = from.atStartOfDay(zone).toInstant();
        // Exclusive upper bound: the day AFTER `to`, local midnight — so a
        // request for to=2026-08-05 includes every moment of Aug 5th itself.
        Instant toInstant = to.plusDays(1).atStartOfDay(zone).toInstant();
        return new ReportDateRange(fromInstant, toInstant);
    }
}
