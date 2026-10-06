package com.cgpa.service;

import com.cgpa.service.GpaCalculator.Weighted;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GpaCalculatorTest {

    @Test
    void marksMapToTheSameBandsAsTheOriginalApp() {
        assertEquals("O", GpaCalculator.gradeForMarks(91));
        assertEquals("A+", GpaCalculator.gradeForMarks(90.9));
        assertEquals("A", GpaCalculator.gradeForMarks(71));
        assertEquals("B+", GpaCalculator.gradeForMarks(61));
        assertEquals("B", GpaCalculator.gradeForMarks(56));
        assertEquals("C", GpaCalculator.gradeForMarks(50));
        assertEquals("U", GpaCalculator.gradeForMarks(49.9));
    }

    @Test
    void sgpaIsCreditWeighted() {
        // (10*4 + 8*3 + 6*1) / 8 = 8.75
        BigDecimal sgpa = GpaCalculator.weightedAverage(List.of(
                new Weighted(4, new BigDecimal("10.0")),
                new Weighted(3, new BigDecimal("8.0")),
                new Weighted(1, new BigDecimal("6.0"))));
        assertEquals(new BigDecimal("8.75"), sgpa);
    }

    @Test
    void roundsHalfUpToTwoDecimals() {
        // (9*1 + 8*2) / 3 = 8.333...
        assertEquals(new BigDecimal("8.33"), GpaCalculator.weightedAverage(List.of(
                new Weighted(1, new BigDecimal("9.0")), new Weighted(2, new BigDecimal("8.0")))));
    }

    @Test
    void emptyInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> GpaCalculator.weightedAverage(List.of()));
    }
}
