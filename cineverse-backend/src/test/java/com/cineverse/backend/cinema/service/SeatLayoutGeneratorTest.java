package com.cineverse.backend.cinema.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cineverse.backend.cinema.entity.SeatType;
import com.cineverse.backend.cinema.service.SeatLayoutGenerator.SeatSpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SeatLayoutGeneratorTest {

    private final SeatLayoutGenerator generator = new SeatLayoutGenerator();

    @Test
    void standardRowHasOneSeatPerColumnAllStandard() {
        List<SeatSpec> seats = generator.generate(1, 10, Set.of());

        assertThat(seats).hasSize(10);
        assertThat(seats).allMatch(s -> s.seatType() == SeatType.STANDARD);
        assertThat(seats).extracting(SeatSpec::columnNumber)
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    void coupleRowWithEvenColumnsPairsUpEveryTwoColumns() {
        List<SeatSpec> seats = generator.generate(1, 10, Set.of(1));

        assertThat(seats).hasSize(5);
        assertThat(seats).allMatch(s -> s.seatType() == SeatType.COUPLE);
        assertThat(seats).extracting(SeatSpec::columnNumber)
                .containsExactlyInAnyOrder(1, 3, 5, 7, 9);
    }

    @Test
    void coupleRowWithOddColumnsLeavesLastSeatAsStandardInsteadOfOverflowing() {
        List<SeatSpec> seats = generator.generate(1, 11, Set.of(1));

        // 5 couple pairs (cols 1,3,5,7,9) + 1 leftover standard seat at column 11.
        assertThat(seats).hasSize(6);
        long coupleCount = seats.stream().filter(s -> s.seatType() == SeatType.COUPLE).count();
        long standardCount = seats.stream().filter(s -> s.seatType() == SeatType.STANDARD).count();
        assertThat(coupleCount).isEqualTo(5);
        assertThat(standardCount).isEqualTo(1);
        SeatSpec leftover = seats.stream().filter(s -> s.seatType() == SeatType.STANDARD).findFirst().orElseThrow();
        assertThat(leftover.columnNumber()).isEqualTo(11);
        // No seat's physical footprint ever exceeds the 11-column grid.
        assertThat(seats).allMatch(s -> physicalWidth(s) - 1 + s.columnNumber() <= 11);
    }

    @Test
    void noTwoSeatsInTheSameRowEverOccupyOverlappingPhysicalColumns() {
        // The scenario most likely to hide an overlap bug: a wide hall with
        // multiple couple rows mixed with standard rows.
        List<SeatSpec> seats = generator.generate(6, 14, Set.of(3, 6));

        Map<String, List<SeatSpec>> byRow = seats.stream().collect(Collectors.groupingBy(SeatSpec::rowLabel));

        for (Map.Entry<String, List<SeatSpec>> entry : byRow.entrySet()) {
            Set<Integer> occupiedColumns = new HashSet<>();
            for (SeatSpec seat : entry.getValue()) {
                for (int col = seat.columnNumber(); col < seat.columnNumber() + physicalWidth(seat); col++) {
                    boolean firstTimeSeeingThisColumn = occupiedColumns.add(col);
                    assertThat(firstTimeSeeingThisColumn)
                            .as("row %s: column %d claimed by more than one seat", entry.getKey(), col)
                            .isTrue();
                }
            }
        }
    }

    @Test
    void rowLabelsFollowSpreadsheetStyleBeyond26Rows() {
        List<SeatSpec> seats = generator.generate(28, 1, Set.of());
        List<String> rowLabelsInOrder = new ArrayList<>(seats.stream().map(SeatSpec::rowLabel).toList());

        assertThat(rowLabelsInOrder.get(0)).isEqualTo("A");
        assertThat(rowLabelsInOrder.get(25)).isEqualTo("Z");
        assertThat(rowLabelsInOrder.get(26)).isEqualTo("AA");
        assertThat(rowLabelsInOrder.get(27)).isEqualTo("AB");
    }

    @Test
    void lastRowConventionMatchesSeedPolicyExample() {
        // Mirrors the actual seed data policy: 6 rows, last row (F) is couple.
        List<SeatSpec> seats = generator.generate(6, 10, Set.of(6));

        List<SeatSpec> rowF = seats.stream().filter(s -> s.rowLabel().equals("F")).toList();
        assertThat(rowF).allMatch(s -> s.seatType() == SeatType.COUPLE);
        List<SeatSpec> otherRows = seats.stream().filter(s -> !s.rowLabel().equals("F")).toList();
        assertThat(otherRows).allMatch(s -> s.seatType() == SeatType.STANDARD);
    }

    @Test
    void rejectsNonPositiveDimensions() {
        assertThatThrownBy(() -> generator.generate(0, 10, Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate(10, 0, Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    private static int physicalWidth(SeatSpec seat) {
        return seat.seatType() == SeatType.COUPLE ? 2 : 1;
    }
}
