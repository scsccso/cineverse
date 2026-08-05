package com.cineverse.backend.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cineverse.backend.report.exception.InvalidReportRangeException;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ReportDateRangeTest {

    private static final ZoneId CINEMA_ZONE = ZoneId.of("Asia/Kuala_Lumpur");

    @Test
    void singleDayRangeIsFromLocalMidnightToTheNextLocalMidnight() {
        LocalDate day = LocalDate.of(2026, 8, 1);

        ReportDateRange range = ReportDateRange.of(day, day, CINEMA_ZONE);

        assertThat(range.from()).isEqualTo(day.atStartOfDay(CINEMA_ZONE).toInstant());
        assertThat(range.to()).isEqualTo(day.plusDays(1).atStartOfDay(CINEMA_ZONE).toInstant());
    }

    @Test
    void multiDayRangeIsInclusiveOfTheToDate() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 7);

        ReportDateRange range = ReportDateRange.of(from, to, CINEMA_ZONE);

        // 7 full local days: Aug 1 00:00 (inclusive) through Aug 8 00:00 (exclusive).
        assertThat(range.from()).isEqualTo(from.atStartOfDay(CINEMA_ZONE).toInstant());
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 8, 8).atStartOfDay(CINEMA_ZONE).toInstant());
    }

    @Test
    void toBeforeFromIsRejected() {
        LocalDate from = LocalDate.of(2026, 8, 7);
        LocalDate to = LocalDate.of(2026, 8, 1);

        assertThatThrownBy(() -> ReportDateRange.of(from, to, CINEMA_ZONE))
                .isInstanceOf(InvalidReportRangeException.class);
    }

    @Test
    void rangeIsComputedInTheGivenZoneNotUtc() {
        // Kuala Lumpur is UTC+8 with no DST — local midnight on Aug 1 is
        // 2026-07-31T16:00:00Z, not 2026-08-01T00:00:00Z. This pins the
        // conversion to that fixed offset instead of accidentally UTC.
        LocalDate day = LocalDate.of(2026, 8, 1);

        ReportDateRange range = ReportDateRange.of(day, day, CINEMA_ZONE);

        assertThat(range.from().toString()).isEqualTo("2026-07-31T16:00:00Z");
        assertThat(range.to().toString()).isEqualTo("2026-08-01T16:00:00Z");
    }
}
