package com.cgpa.service;

import com.cgpa.model.Models.*;
import com.cgpa.repository.CgpaRepository;
import com.cgpa.service.GpaCalculator.Weighted;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class CalculationService {
    private final CgpaRepository repo;

    public CalculationService(CgpaRepository repo) {
        this.repo = repo;
    }

    public List<Course> courses(int semester) {
        return repo.findCourses(semester);
    }

    public List<GradePoint> gradeScale() {
        return repo.findGradeScale();
    }

    public List<HistoryRow> history(String roll, int limit) {
        String r = (roll == null || roll.isBlank()) ? null : roll.trim().toUpperCase();
        return repo.findHistory(r, Math.min(Math.max(limit, 1), 500));
    }

    public HistoryDetail historyDetail(long id) {
        HistoryRow row = repo.findHistoryRow(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Result " + id + " was not found"));
        return new HistoryDetail(row, repo.findItems(id));
    }

    @Transactional
    public CalculationResponse calculate(CalculationRequest req) {
        // ---- validate ----
        String name = req.name() == null ? "" : req.name().trim();
        String roll = req.roll() == null ? "" : req.roll().trim().toUpperCase();
        if (name.isEmpty() || roll.isEmpty()) {
            throw bad("Enter both a name and a roll number");
        }
        if (name.length() > 100 || roll.length() > 30) {
            throw bad("Name can be up to 100 characters and roll number up to 30");
        }
        if (req.semester() == null || req.semester() < 1 || req.semester() > 8) {
            throw bad("Semester must be between 1 and 8");
        }
        int semester = req.semester();
        boolean marksMode;
        if ("marks".equals(req.mode())) marksMode = true;
        else if ("grades".equals(req.mode())) marksMode = false;
        else throw bad("Input mode must be 'marks' or 'grades'");
        if (req.entries() == null) {
            throw bad("No course entries were sent");
        }

        Map<String, Course> courseByCode = new HashMap<>();
        for (Course c : repo.findCourses(semester)) courseByCode.put(c.code(), c);
        if (courseByCode.isEmpty()) {
            throw bad("No courses are set up for semester " + semester);
        }

        Map<String, BigDecimal> scale = new HashMap<>();
        for (GradePoint g : repo.findGradeScale()) scale.put(g.grade(), g.points());

        // ---- resolve each entry to a grade and grade point ----
        List<ItemResult> items = new ArrayList<>();
        List<Weighted> forSgpa = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Entry e : req.entries()) {
            String value = e.value() == null ? "" : e.value().trim();
            if (value.isEmpty()) continue; // blank rows are skipped, like the original app

            Course course = courseByCode.get(e.code());
            if (course == null) {
                throw bad("Course " + e.code() + " does not belong to semester " + semester);
            }
            if (!seen.add(course.code())) {
                throw bad("Course " + course.code() + " appears more than once");
            }

            String grade;
            if (marksMode) {
                double marks;
                try {
                    marks = Double.parseDouble(value);
                } catch (NumberFormatException ex) {
                    throw bad("Marks for " + course.code() + " must be a number");
                }
                if (!(marks >= 0 && marks <= 100)) {
                    throw bad("Marks for " + course.code() + " must be between 0 and 100");
                }
                grade = GpaCalculator.gradeForMarks(marks);
            } else {
                grade = value.toUpperCase();
                if (!scale.containsKey(grade)) {
                    throw bad("'" + value + "' is not a valid grade for " + course.code());
                }
            }
            BigDecimal points = scale.get(grade);
            items.add(new ItemResult(course.code(), course.title(), course.credits(), value, grade, points));
            forSgpa.add(new Weighted(course.credits(), points));
        }
        if (items.isEmpty()) {
            throw bad("Enter a " + (marksMode ? "mark" : "grade") + " for at least one course");
        }

        // ---- compute and store ----
        BigDecimal sgpa = GpaCalculator.weightedAverage(forSgpa);
        int semesterCredits = items.stream().mapToInt(ItemResult::credits).sum();

        long studentId = repo.upsertStudent(roll, name);
        long resultId = repo.insertResult(studentId, semester, req.mode(), semesterCredits, sgpa);
        repo.insertItems(resultId, items);

        // CGPA = credit-weighted average of the latest SGPA for every semester on record
        List<SemesterSummary> semesters = repo.latestPerSemester(studentId);
        List<Weighted> forCgpa = new ArrayList<>();
        int totalCredits = 0;
        for (SemesterSummary s : semesters) {
            forCgpa.add(new Weighted(s.credits(), s.sgpa()));
            totalCredits += s.credits();
        }
        BigDecimal cgpa = GpaCalculator.weightedAverage(forCgpa);
        repo.updateCgpa(resultId, cgpa);

        return new CalculationResponse(resultId, name, roll, semester, sgpa, cgpa,
                semesterCredits, totalCredits, items, semesters);
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
