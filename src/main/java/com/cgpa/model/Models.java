package com.cgpa.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response shapes shared by the repository, service and controller. */
public final class Models {
    private Models() {}

    public record Course(String code, String title, int credits, int semester) {}

    public record GradePoint(String grade, BigDecimal points) {}

    /** One line of input: a course code plus a mark (0-100) or a grade letter. */
    public record Entry(String code, String value) {}

    public record CalculationRequest(String name, String roll, Integer semester, String mode, List<Entry> entries) {}

    public record ItemResult(String code, String title, int credits, String input,
                             String grade, BigDecimal gradePoint) {}

    public record SemesterSummary(int semester, BigDecimal sgpa, int credits) {}

    public record CalculationResponse(long id, String name, String roll, int semester,
                                      BigDecimal sgpa, BigDecimal cgpa,
                                      int semesterCredits, int totalCredits,
                                      List<ItemResult> items, List<SemesterSummary> semesters) {}

    public record HistoryRow(long id, String name, String roll, int semester, String mode,
                             int credits, BigDecimal sgpa, BigDecimal cgpa, LocalDateTime createdAt) {}

    public record HistoryDetail(HistoryRow summary, List<ItemResult> items) {}
}
