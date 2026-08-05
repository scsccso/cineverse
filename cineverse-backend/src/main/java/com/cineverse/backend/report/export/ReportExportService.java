package com.cineverse.backend.report.export;

import com.cineverse.backend.report.dto.OccupancyReportResponse;
import com.cineverse.backend.report.dto.SalesReportResponse;
import com.cineverse.backend.report.dto.ShowtimeOccupancy;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Turns the two report DTOs into a {@link TabularReport} and hands that to
 * {@link CsvWriter}/{@link PdfTableWriter} — this is the only class that
 * knows both "what a report response looks like" and "what a flat table
 * looks like", so CSV/PDF format concerns never leak into ReportService and
 * report-shape concerns never leak into the writers.
 */
@Service
public class ReportExportService {

    private static final ZoneId CINEMA_ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private static final DateTimeFormatter SHOWTIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(CINEMA_ZONE);

    public byte[] salesCsv(SalesReportResponse report) {
        return CsvWriter.write(toTabular(report));
    }

    public byte[] salesPdf(SalesReportResponse report) {
        return PdfTableWriter.write(toTabular(report));
    }

    public byte[] occupancyCsv(OccupancyReportResponse report) {
        return CsvWriter.write(toTabular(report));
    }

    public byte[] occupancyPdf(OccupancyReportResponse report) {
        return PdfTableWriter.write(toTabular(report));
    }

    private TabularReport toTabular(SalesReportResponse report) {
        List<String> headers = List.of("Period Start", "Revenue (%s)".formatted(report.currency().toUpperCase(Locale.ROOT)), "Booking Count");
        List<List<String>> rows = new ArrayList<>();
        for (var bucket : report.buckets()) {
            rows.add(List.of(
                    bucket.periodStart().toString(),
                    bucket.revenue().toPlainString(),
                    String.valueOf(bucket.bookingCount())));
        }
        rows.add(List.of("TOTAL", report.totalRevenue().toPlainString(), ""));
        rows.add(List.of(
                "Pending reconciliation (ORPHANED_SUCCESS, not included above)",
                report.pendingReconciliationAmount().toPlainString(),
                ""));

        String title = "Sales Report %s to %s (%s)".formatted(report.from(), report.to(), report.granularity());
        return new TabularReport(title, headers, rows);
    }

    private TabularReport toTabular(OccupancyReportResponse report) {
        List<String> headers = List.of(
                "Showtime", "Movie", "Hall", "Total Seats", "Booked Seats", "Occupancy Rate");
        List<List<String>> rows = new ArrayList<>();
        for (ShowtimeOccupancy showtime : report.showtimes()) {
            rows.add(List.of(
                    SHOWTIME_FORMAT.format(showtime.startTime()),
                    showtime.movieTitle(),
                    showtime.hallName(),
                    String.valueOf(showtime.totalSeats()),
                    String.valueOf(showtime.bookedSeats()),
                    formatRate(showtime.occupancyRate())));
        }
        rows.add(List.of(
                "TOTAL", "", "",
                String.valueOf(report.totalSeats()),
                String.valueOf(report.totalBookedSeats()),
                formatRate(report.overallOccupancyRate())));

        String title = "Occupancy Report %s to %s".formatted(report.from(), report.to());
        return new TabularReport(title, headers, rows);
    }

    private String formatRate(double rate) {
        return "%.2f%%".formatted(rate * 100);
    }
}
