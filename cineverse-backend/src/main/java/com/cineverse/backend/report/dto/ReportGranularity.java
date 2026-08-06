package com.cineverse.backend.report.dto;

/**
 * Bucket width for the sales report's time series. {@link #sqlValue()} feeds
 * both {@code date_trunc(...)} and the {@code generate_series} step interval
 * in {@code ReportRepository} — always via a bound query parameter, never
 * string-concatenated into the SQL text, even though these three values are
 * server-controlled (enum-validated) and would be safe either way.
 */
public enum ReportGranularity {
    DAY("day"),
    WEEK("week"),
    MONTH("month");

    private final String sqlValue;

    ReportGranularity(String sqlValue) {
        this.sqlValue = sqlValue;
    }

    /** Postgres date_trunc field name / generate_series interval unit — lowercase, matches Postgres's own vocabulary. */
    public String sqlValue() {
        return sqlValue;
    }
}
