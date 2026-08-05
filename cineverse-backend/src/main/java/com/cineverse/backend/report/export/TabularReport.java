package com.cineverse.backend.report.export;

import java.util.List;

/**
 * Both reports export to the exact same shape — a title, a header row, and
 * data rows already formatted as strings — so {@link CsvWriter} and
 * {@link PdfTableWriter} only need to be written once each, instead of once
 * per report type per format. {@link ReportExportService} is the only place
 * that knows how to turn a SalesReportResponse/OccupancyReportResponse into
 * one of these.
 */
public record TabularReport(String title, List<String> headers, List<List<String>> rows) {
}
