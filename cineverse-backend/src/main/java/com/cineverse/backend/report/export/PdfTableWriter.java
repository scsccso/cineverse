package com.cineverse.backend.report.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * OpenPDF (LGPL/MPL), not iText — see the pom.xml comment on the dependency
 * for why. A single title + one flat table is all either report needs; this
 * intentionally doesn't try to be a general PDF report framework.
 */
public final class PdfTableWriter {

    private static final DateTimeFormatter GENERATED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private PdfTableWriter() {
    }

    public static byte[] write(TabularReport report) {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 54, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            document.add(new Paragraph(report.title(), titleFont));

            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Paragraph generatedAt = new Paragraph(
                    "Generated at " + GENERATED_AT_FORMAT.format(Instant.now()) + " UTC", metaFont);
            generatedAt.setSpacingAfter(12);
            document.add(generatedAt);

            document.add(buildTable(report));
            document.close();
        } catch (DocumentException e) {
            // OpenPDF's checked DocumentException only fires for malformed
            // document structure (e.g. adding content before open()) — a
            // programming error in this class, not a runtime/data condition
            // callers could sensibly recover from.
            throw new IllegalStateException("Failed to generate PDF report", e);
        }
        return out.toByteArray();
    }

    private static PdfPTable buildTable(TabularReport report) {
        PdfPTable table = new PdfPTable(report.headers().size());
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        for (String header : report.headers()) {
            PdfPCell cell = new PdfPCell(new Paragraph(header, headerFont));
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            cell.setPadding(6);
            table.addCell(cell);
        }

        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
        for (var row : report.rows()) {
            for (String value : row) {
                PdfPCell cell = new PdfPCell(new Paragraph(value == null ? "" : value, bodyFont));
                cell.setPadding(5);
                table.addCell(cell);
            }
        }

        return table;
    }
}
