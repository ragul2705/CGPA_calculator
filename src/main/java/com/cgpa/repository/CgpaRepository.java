package com.cgpa.repository;

import com.cgpa.model.Models.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class CgpaRepository {
    private final JdbcTemplate jdbc;

    public CgpaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- reference data ----

    public List<Course> findCourses(int semester) {
        return jdbc.query(
                "SELECT code, title, credits, semester FROM courses WHERE semester = ? ORDER BY code",
                (rs, i) -> new Course(rs.getString("code"), rs.getString("title"),
                        rs.getInt("credits"), rs.getInt("semester")),
                semester);
    }

    public List<GradePoint> findGradeScale() {
        return jdbc.query("SELECT grade, points FROM grade_scale ORDER BY points DESC, grade",
                (rs, i) -> new GradePoint(rs.getString("grade"), rs.getBigDecimal("points")));
    }

    // ---- writes ----

    public long upsertStudent(String roll, String name) {
        jdbc.update("INSERT INTO students (roll, name) VALUES (?, ?) ON DUPLICATE KEY UPDATE name = ?",
                roll, name, name);
        Long id = jdbc.queryForObject("SELECT id FROM students WHERE roll = ?", Long.class, roll);
        if (id == null) throw new IllegalStateException("Student row missing after upsert");
        return id;
    }

    public long insertResult(long studentId, int semester, String mode, int totalCredits, BigDecimal sgpa) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO results (student_id, semester, input_mode, total_credits, sgpa) VALUES (?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, studentId);
            ps.setInt(2, semester);
            ps.setString(3, mode);
            ps.setInt(4, totalCredits);
            ps.setBigDecimal(5, sgpa);
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("No generated key for result");
        return key.longValue();
    }

    public void insertItems(long resultId, List<ItemResult> items) {
        jdbc.batchUpdate(
                "INSERT INTO result_items (result_id, course_code, credits, input_value, grade, grade_point) "
                        + "VALUES (?,?,?,?,?,?)",
                items, items.size(),
                (ps, it) -> {
                    ps.setLong(1, resultId);
                    ps.setString(2, it.code());
                    ps.setInt(3, it.credits());
                    ps.setString(4, it.input());
                    ps.setString(5, it.grade());
                    ps.setBigDecimal(6, it.gradePoint());
                });
    }

    public void updateCgpa(long resultId, BigDecimal cgpa) {
        jdbc.update("UPDATE results SET cgpa = ? WHERE id = ?", cgpa, resultId);
    }

    // ---- reads ----

    /** The most recent result for each semester of a student (re-calculating a semester replaces the old one). */
    public List<SemesterSummary> latestPerSemester(long studentId) {
        return jdbc.query(
                "SELECT r.semester, r.sgpa, r.total_credits FROM results r "
                        + "WHERE r.student_id = ? AND r.id = ("
                        + "  SELECT MAX(r2.id) FROM results r2 "
                        + "  WHERE r2.student_id = r.student_id AND r2.semester = r.semester) "
                        + "ORDER BY r.semester",
                (rs, i) -> new SemesterSummary(rs.getInt("semester"), rs.getBigDecimal("sgpa"),
                        rs.getInt("total_credits")),
                studentId);
    }

    private static final String HISTORY_SELECT =
            "SELECT r.id, s.name, s.roll, r.semester, r.input_mode, r.total_credits, r.sgpa, r.cgpa, r.created_at "
                    + "FROM results r JOIN students s ON s.id = r.student_id ";

    private static final RowMapper<HistoryRow> HISTORY_MAPPER = (rs, i) -> new HistoryRow(
            rs.getLong("id"), rs.getString("name"), rs.getString("roll"), rs.getInt("semester"),
            rs.getString("input_mode"), rs.getInt("total_credits"), rs.getBigDecimal("sgpa"),
            rs.getBigDecimal("cgpa"), rs.getTimestamp("created_at").toLocalDateTime());

    public List<HistoryRow> findHistory(String rollOrNull, int limit) {
        if (rollOrNull == null) {
            return jdbc.query(HISTORY_SELECT + "ORDER BY r.id DESC LIMIT ?", HISTORY_MAPPER, limit);
        }
        return jdbc.query(HISTORY_SELECT + "WHERE s.roll = ? ORDER BY r.id DESC LIMIT ?",
                HISTORY_MAPPER, rollOrNull, limit);
    }

    public Optional<HistoryRow> findHistoryRow(long id) {
        List<HistoryRow> rows = jdbc.query(HISTORY_SELECT + "WHERE r.id = ?", HISTORY_MAPPER, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<ItemResult> findItems(long resultId) {
        return jdbc.query(
                "SELECT i.course_code, c.title, i.credits, i.input_value, i.grade, i.grade_point "
                        + "FROM result_items i JOIN courses c ON c.code = i.course_code "
                        + "WHERE i.result_id = ? ORDER BY i.course_code",
                (rs, i) -> new ItemResult(rs.getString("course_code"), rs.getString("title"),
                        rs.getInt("credits"), rs.getString("input_value"), rs.getString("grade"),
                        rs.getBigDecimal("grade_point")),
                resultId);
    }
}
