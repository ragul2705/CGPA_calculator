package com.cgpa.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Pure grade arithmetic with no database or Spring dependencies, so it is easy to unit test. */
public final class GpaCalculator {
    private GpaCalculator() {}

    /** A value (grade point or SGPA) and the credits that weight it. */
    public record Weighted(int credits, BigDecimal value) {}

    /** Converts marks (0-100) to a grade letter using the same bands as the original app. */
    public static String gradeForMarks(double marks) {
        if (marks >= 91) return "O";
        if (marks >= 81) return "A+";
        if (marks >= 71) return "A";
        if (marks >= 61) return "B+";
        if (marks >= 56) return "B";
        if (marks >= 50) return "C";
        return "U";
    }

    /** Credit-weighted average, rounded half-up to 2 decimals. Used for both SGPA and CGPA. */
    public static BigDecimal weightedAverage(List<Weighted> items) {
        long totalCredits = 0;
        BigDecimal weightedSum = BigDecimal.ZERO;
        for (Weighted w : items) {
            totalCredits += w.credits();
            weightedSum = weightedSum.add(w.value().multiply(BigDecimal.valueOf(w.credits())));
        }
        if (totalCredits == 0) {
            throw new IllegalArgumentException("No credits to average");
        }
        return weightedSum.divide(BigDecimal.valueOf(totalCredits), 2, RoundingMode.HALF_UP);
    }
}
