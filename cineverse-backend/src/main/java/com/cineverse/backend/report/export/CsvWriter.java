package com.cineverse.backend.report.export;

import java.nio.charset.StandardCharsets;

/**
 * Minimal RFC 4180 writer — no library dependency needed for something this
 * small. A field is quoted (with internal double-quotes doubled) only when
 * it actually contains a comma, quote, or newline; movie titles are the only
 * user-authored text that ends up in these reports, so that path is
 * exercised for real, not just theoretical.
 */
public final class CsvWriter {

    private static final String LINE_ENDING = "\r\n";

    private CsvWriter() {
    }

    public static byte[] write(TabularReport report) {
        StringBuilder sb = new StringBuilder();
        appendRow(sb, report.headers());
        for (var row : report.rows()) {
            appendRow(sb, row);
        }
        // UTF-8 BOM so Excel (still the most likely consumer of a "download
        // CSV" button) detects the encoding instead of mangling non-ASCII
        // movie titles as Latin-1.
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(body, 0, result, bom.length, body.length);
        return result;
    }

    private static void appendRow(StringBuilder sb, java.util.List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(fields.get(i)));
        }
        sb.append(LINE_ENDING);
    }

    private static String escape(String field) {
        if (field == null) {
            return "";
        }
        boolean needsQuoting = field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r");
        if (!needsQuoting) {
            return field;
        }
        return "\"" + field.replace("\"", "\"\"") + "\"";
    }
}
