package com.cineverse.backend.report.controller;

import com.cineverse.backend.report.dto.ExportFormat;
import com.cineverse.backend.report.dto.OccupancyReportResponse;
import com.cineverse.backend.report.dto.ReportGranularity;
import com.cineverse.backend.report.dto.SalesReportResponse;
import com.cineverse.backend.report.export.ReportExportService;
import com.cineverse.backend.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 8 reporting — ADMIN-only (see SecurityConfig's {@code /api/v1/admin/**}
 * matcher), same 401 (anonymous) / 403 (wrong role) split the rest of the app
 * uses. {@code from}/{@code to} are inclusive calendar dates in the cinema's
 * own timezone; see {@code ReportService.CINEMA_ZONE} / {@code ReportDateRange}
 * for how that turns into the half-open instant range the SQL actually binds.
 * Presets ("today" / "last 7 days" / "last 30 days") are a frontend concern —
 * they resolve to concrete from/to values before calling this endpoint, so
 * the API surface only needs to support one shape (explicit range), not two.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
@Tag(name = "Admin Reports", description = "销售报表 + 上座率分析,仅 ADMIN(Phase 8)")
public class AdminReportController {

    private final ReportService reportService;
    private final ReportExportService reportExportService;

    public AdminReportController(ReportService reportService, ReportExportService reportExportService) {
        this.reportService = reportService;
        this.reportExportService = reportExportService;
    }

    @GetMapping("/sales")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "销售报表", description = "按 day/week/month 粒度统计营收(仅 CONFIRMED booking 的 SUCCEEDED "
            + "payment),可选按电影/影厅筛选;ORPHANED_SUCCESS 金额单独作为 pendingReconciliationAmount 返回,不计入 totalRevenue")
    public SalesReportResponse sales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") ReportGranularity granularity,
            @RequestParam(required = false) UUID movieId,
            @RequestParam(required = false) UUID hallId) {
        return reportService.salesReport(from, to, granularity, movieId, hallId);
    }

    @GetMapping("/occupancy")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "上座率分析", description = "按场次统计已订座位数(仅 CONFIRMED)/ 总座位数,可选按影厅/电影筛选;"
            + "响应同时带每场次明细和 from/to 范围内的汇总")
    public OccupancyReportResponse occupancy(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID hallId,
            @RequestParam(required = false) UUID movieId) {
        return reportService.occupancyReport(from, to, hallId, movieId);
    }

    @GetMapping("/sales/export")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "导出销售报表", description = "format=csv|pdf,其余参数同 /sales")
    public ResponseEntity<byte[]> exportSales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") ReportGranularity granularity,
            @RequestParam(required = false) UUID movieId,
            @RequestParam(required = false) UUID hallId,
            @Parameter(description = "csv 或 pdf") @RequestParam String format) {
        SalesReportResponse report = reportService.salesReport(from, to, granularity, movieId, hallId);
        ExportFormat exportFormat = parseFormat(format);
        byte[] body = exportFormat == ExportFormat.CSV
                ? reportExportService.salesCsv(report)
                : reportExportService.salesPdf(report);
        return download(body, exportFormat, "sales-report_%s_%s".formatted(from, to));
    }

    @GetMapping("/occupancy/export")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "导出上座率报表", description = "format=csv|pdf,其余参数同 /occupancy")
    public ResponseEntity<byte[]> exportOccupancy(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID hallId,
            @RequestParam(required = false) UUID movieId,
            @Parameter(description = "csv 或 pdf") @RequestParam String format) {
        OccupancyReportResponse report = reportService.occupancyReport(from, to, hallId, movieId);
        ExportFormat exportFormat = parseFormat(format);
        byte[] body = exportFormat == ExportFormat.CSV
                ? reportExportService.occupancyCsv(report)
                : reportExportService.occupancyPdf(report);
        return download(body, exportFormat, "occupancy-report_%s_%s".formatted(from, to));
    }

    private ExportFormat parseFormat(String format) {
        try {
            return ExportFormat.valueOf(format.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported export format \"" + format + "\" (expected csv or pdf)");
        }
    }

    private ResponseEntity<byte[]> download(byte[] body, ExportFormat format, String filenameWithoutExtension) {
        String filename = filenameWithoutExtension + "." + format.extension();
        return ResponseEntity.ok()
                .contentType(format.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
