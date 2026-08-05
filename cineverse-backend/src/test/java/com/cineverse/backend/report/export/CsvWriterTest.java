package com.cineverse.backend.report.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

    @Test
    void writesHeaderAndRowsCommaSeparated() {
        TabularReport report = new TabularReport(
                "Title", List.of("A", "B"), List.of(List.of("1", "2"), List.of("3", "4")));

        String csv = stripBom(CsvWriter.write(report));

        assertThat(csv).isEqualTo("A,B\r\n1,2\r\n3,4\r\n");
    }

    @Test
    void quotesFieldsContainingCommasQuotesOrNewlines() {
        TabularReport report = new TabularReport(
                "Title", List.of("Movie"), List.of(List.of("Fast, Furious"), List.of("She said \"hi\"")));

        String csv = stripBom(CsvWriter.write(report));

        assertThat(csv).contains("\"Fast, Furious\"");
        assertThat(csv).contains("\"She said \"\"hi\"\"\"");
    }

    @Test
    void plainFieldsAreNotQuoted() {
        TabularReport report = new TabularReport("Title", List.of("Movie"), List.of(List.of("Interstellar")));

        String csv = stripBom(CsvWriter.write(report));

        assertThat(csv).isEqualTo("Movie\r\nInterstellar\r\n");
    }

    @Test
    void outputStartsWithUtf8BomForExcelCompatibility() {
        TabularReport report = new TabularReport("Title", List.of("A"), List.of());

        byte[] bytes = CsvWriter.write(report);

        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
    }

    private String stripBom(byte[] bytes) {
        String withBom = new String(bytes, StandardCharsets.UTF_8);
        return withBom.startsWith("﻿") ? withBom.substring(1) : withBom;
    }
}
