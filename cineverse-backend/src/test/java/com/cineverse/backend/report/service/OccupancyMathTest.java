package com.cineverse.backend.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OccupancyMathTest {

    @Test
    void zeroTotalSeatsReturnsZeroInsteadOfDividingByZero() {
        assertThat(OccupancyMath.rate(0, 0)).isEqualTo(0.0);
    }

    @Test
    void zeroBookedOfSomeTotalIsZero() {
        assertThat(OccupancyMath.rate(0, 55)).isEqualTo(0.0);
    }

    @Test
    void fullyBookedIsOne() {
        assertThat(OccupancyMath.rate(55, 55)).isEqualTo(1.0);
    }

    @Test
    void partiallyBookedComputesExactFraction() {
        assertThat(OccupancyMath.rate(3, 55)).isEqualTo(3.0 / 55.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void resultIsRoundedTo4DecimalPlacesToAvoidBinaryFloatingPointNoise() {
        // 1/3 in raw double math has a long non-terminating binary
        // representation — this asserts the rounding actually happens
        // rather than leaking something like 0.33333333333333331.
        assertThat(OccupancyMath.rate(1, 3)).isEqualTo(0.3333);
    }
}
