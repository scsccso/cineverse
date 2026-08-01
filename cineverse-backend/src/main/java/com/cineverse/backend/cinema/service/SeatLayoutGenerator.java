package com.cineverse.backend.cinema.service;

import com.cineverse.backend.cinema.entity.SeatType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Pure, side-effect-free seat layout generation. This is the one place a
 * coordinate-overlap bug could sneak in (a COUPLE seat physically spans 2
 * columns but is one DB row with a single starting column_number), so the
 * "no overlap" guarantee is by construction here, not checked afterwards —
 * see {@link SeatLayoutGeneratorTest}.
 */
@Component
public class SeatLayoutGenerator {

    public record SeatSpec(String rowLabel, int columnNumber, SeatType seatType) {
    }

    /**
     * @param coupleRowNumbers 1-indexed row numbers that should be laid out
     *                         as COUPLE seats (paired every 2 columns); any
     *                         row not listed is all STANDARD.
     */
    public List<SeatSpec> generate(int totalRows, int totalColumns, Set<Integer> coupleRowNumbers) {
        if (totalRows <= 0 || totalColumns <= 0) {
            throw new IllegalArgumentException("totalRows and totalColumns must both be positive");
        }

        List<SeatSpec> seats = new ArrayList<>();
        for (int rowNumber = 1; rowNumber <= totalRows; rowNumber++) {
            String rowLabel = rowLabelFor(rowNumber);
            if (coupleRowNumbers.contains(rowNumber)) {
                seats.addAll(generateCoupleRow(rowLabel, totalColumns));
            } else {
                seats.addAll(generateStandardRow(rowLabel, totalColumns));
            }
        }
        return seats;
    }

    private List<SeatSpec> generateStandardRow(String rowLabel, int totalColumns) {
        List<SeatSpec> row = new ArrayList<>();
        for (int column = 1; column <= totalColumns; column++) {
            row.add(new SeatSpec(rowLabel, column, SeatType.STANDARD));
        }
        return row;
    }

    private List<SeatSpec> generateCoupleRow(String rowLabel, int totalColumns) {
        List<SeatSpec> row = new ArrayList<>();
        int column = 1;
        while (column + 1 <= totalColumns) {
            row.add(new SeatSpec(rowLabel, column, SeatType.COUPLE));
            column += 2;
        }
        if (column == totalColumns) {
            // Odd column count: one seat is left over and can't pair up —
            // falls back to STANDARD rather than spanning past the grid edge.
            row.add(new SeatSpec(rowLabel, column, SeatType.STANDARD));
        }
        return row;
    }

    /** 1 -> A, 2 -> B, ..., 26 -> Z, 27 -> AA (spreadsheet-style, for halls with 27+ rows). */
    private String rowLabelFor(int rowNumber) {
        StringBuilder label = new StringBuilder();
        int n = rowNumber;
        while (n > 0) {
            int remainder = (n - 1) % 26;
            label.insert(0, (char) ('A' + remainder));
            n = (n - 1) / 26;
        }
        return label.toString();
    }
}
