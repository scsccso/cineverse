package com.cineverse.backend.report.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class PdfTableWriterTest {

    @Test
    void producesANonEmptyValidPdfDocument() {
        TabularReport report = new TabularReport(
                "Sales Report", List.of("Period", "Revenue"), List.of(List.of("2026-08-01", "75.00")));

        byte[] pdf = PdfTableWriter.write(report);

        assertThat(pdf).isNotEmpty();
        // PDF files start with the "%PDF-" magic header — a cheap way to
        // confirm OpenPDF actually produced a well-formed document without
        // parsing the whole thing back.
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void handlesAnEmptyRowSetWithoutThrowing() {
        TabularReport report = new TabularReport("Empty Report", List.of("Period", "Revenue"), List.of());

        byte[] pdf = PdfTableWriter.write(report);

        assertThat(pdf).isNotEmpty();
    }
}
