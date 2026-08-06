package com.cineverse.backend.report.service;

/**
 * Pure occupancy-rate calculation, pulled out of ReportService so it has its
 * own unit test independent of Testcontainers — this is the one piece of
 * report "math" the app does outside SQL (the aggregation itself — SUM,
 * COUNT, GROUP BY — all happens in ReportRepository's native queries; this is
 * just the final booked/total division on numbers SQL already aggregated).
 */
public final class OccupancyMath {

    private OccupancyMath() {
    }

    /** A hall with zero seats can't happen in practice, but returning 0 rather than NaN/Infinity keeps the API contract sane if it ever does. */
    public static double rate(long booked, long total) {
        if (total <= 0) {
            return 0.0;
        }
        double raw = (double) booked / (double) total;
        // Rounded to 4 decimal places (0.01% resolution) purely to keep the
        // JSON free of binary-floating-point noise like 0.30000000000000004
        // — not a precision requirement of the report itself.
        return Math.round(raw * 10_000.0) / 10_000.0;
    }
}
