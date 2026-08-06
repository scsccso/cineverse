package com.cineverse.backend.report.service;

import com.cineverse.backend.payment.config.StripeProperties;
import com.cineverse.backend.report.dto.OccupancyReportResponse;
import com.cineverse.backend.report.dto.ReportGranularity;
import com.cineverse.backend.report.dto.SalesBucket;
import com.cineverse.backend.report.dto.SalesReportResponse;
import com.cineverse.backend.report.dto.ShowtimeOccupancy;
import com.cineverse.backend.report.repository.ReportRepository;
import com.cineverse.backend.report.repository.ShowtimeOccupancyRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the two admin reports: validates/converts the request range,
 * delegates the actual aggregation to {@link ReportRepository}'s native SQL,
 * and assembles the response DTOs. No aggregation math lives here — summing
 * already-aggregated buckets for a grand total, and the booked/total
 * division in {@link OccupancyMath}, are rollups of numbers Postgres already
 * computed, not a re-implementation of the aggregation itself.
 */
@Service
public class ReportService {

    /**
     * MVP has exactly one cinema (CineVerse Downtown, Kuala Lumpur — see
     * CLAUDE.md Phase 3), so report buckets/ranges are computed in its fixed
     * timezone rather than UTC or the admin's browser timezone: "today"'s
     * sales bucket should mean the cinema's calendar day, not whatever day
     * it happens to be in Greenwich. Same reasoning and same hardcoded zone
     * as frontend/src/lib/format.ts's CINEMA_TIME_ZONE — multi-cinema support
     * would need this to come from cinema data instead.
     */
    static final ZoneId CINEMA_ZONE = ZoneId.of("Asia/Kuala_Lumpur");

    private final ReportRepository reportRepository;
    private final StripeProperties stripeProperties;

    public ReportService(ReportRepository reportRepository, StripeProperties stripeProperties) {
        this.reportRepository = reportRepository;
        this.stripeProperties = stripeProperties;
    }

    public SalesReportResponse salesReport(
            LocalDate from, LocalDate to, ReportGranularity granularity, UUID movieId, UUID hallId) {
        ReportDateRange range = ReportDateRange.of(from, to, CINEMA_ZONE);

        List<SalesBucket> buckets = reportRepository.salesBuckets(
                granularity.sqlValue(), range.from(), range.to(), movieId, hallId, CINEMA_ZONE.getId());
        BigDecimal totalRevenue =
                buckets.stream().map(SalesBucket::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendingReconciliation = reportRepository.sumAmountByStatus(
                "ORPHANED_SUCCESS", range.from(), range.to(), movieId, hallId);

        return new SalesReportResponse(
                from, to, granularity, movieId, hallId, stripeProperties.currency(),
                buckets, totalRevenue, pendingReconciliation);
    }

    public OccupancyReportResponse occupancyReport(LocalDate from, LocalDate to, UUID hallId, UUID movieId) {
        ReportDateRange range = ReportDateRange.of(from, to, CINEMA_ZONE);

        List<ShowtimeOccupancyRow> rows = reportRepository.occupancyRows(range.from(), range.to(), hallId, movieId);
        List<ShowtimeOccupancy> showtimes = rows.stream()
                .map(row -> new ShowtimeOccupancy(
                        row.showtimeId(),
                        row.movieTitle(),
                        row.hallId(),
                        row.hallName(),
                        row.startTime(),
                        row.totalSeats(),
                        row.bookedSeats(),
                        OccupancyMath.rate(row.bookedSeats(), row.totalSeats())))
                .toList();

        long totalSeats = rows.stream().mapToLong(ShowtimeOccupancyRow::totalSeats).sum();
        long totalBookedSeats = rows.stream().mapToLong(ShowtimeOccupancyRow::bookedSeats).sum();

        return new OccupancyReportResponse(
                from, to, hallId, movieId, showtimes,
                totalSeats, totalBookedSeats, OccupancyMath.rate(totalBookedSeats, totalSeats));
    }
}
