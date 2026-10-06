# SGPA and CGPA Calculator

A full-stack web app that automates GPA computation across semesters. Java (Spring Boot) backend,
plain JavaScript front end, MySQL for persistent storage. Course list and grade scale match the
original Tkinter/SQLite version (semesters 1-8, 64 courses).

## Run it

1. Install JDK 17+, Maven, and MySQL 8.
2. Set your database credentials (defaults: user `root`, empty password, database `cgpa_db`):

   ```
   export DB_USER=root
   export DB_PASSWORD=your_password
   # optional: export DB_URL="jdbc:mysql://localhost:3306/cgpa_db?createDatabaseIfNotExist=true&serverTimezone=UTC"
   ```
3. Start the app. Tables and seed data are created automatically on first run:

   ```
   mvn spring-boot:run
   ```
4. Open http://localhost:8080. Run the unit tests with `mvn test`.

## How results are calculated

- Each course's mark or grade becomes a grade point (O=10, A+=9, A=8, B+=7, B=6, C=5, U/SA/WD=0).
- Marks bands: 91+ O, 81+ A+, 71+ A, 61+ B+, 56+ B, 50+ C, below 50 U.
- SGPA = sum(grade point x credits) / sum(credits) over the courses you entered, rounded half-up to 2 decimals.
- CGPA = credit-weighted average of the **latest** SGPA for every semester saved under that roll number.
  Recalculating a semester replaces its earlier SGPA in the CGPA, but the old result stays in history.

## Database schema (see `src/main/resources/schema.sql`)

| Table | Purpose |
|---|---|
| `students` | One row per roll number |
| `courses` | Course code, title, credits, semester |
| `grade_scale` | Grade letter to grade point mapping |
| `results` | One row per calculation: SGPA, CGPA, credits, input mode, timestamp |
| `result_items` | The exact inputs behind each result: course, value entered, grade, points |

Because every calculation and its per-course inputs are stored and never overwritten, any SGPA/CGPA can be traced back and re-verified.

## REST API

| Method | Path | Description |
|---|---|---|
| GET | `/api/courses?semester=n` | Courses for a semester |
| GET | `/api/grades` | Grade scale |
| POST | `/api/calculate` | `{name, roll, semester, mode: "marks"\|"grades", entries: [{code, value}]}` returns SGPA, CGPA, breakdown |
| GET | `/api/history?roll=` | Saved results, newest first |
| GET | `/api/history/{id}` | One result with its per-course inputs |

## Differences from the Python version

- The old version computed CGPA from every saved row using the full credit total of each semester, so recalculating a semester
  counted it twice and skipped-course credits were ignored. This version uses one latest result per semester and the credits actually entered.
- Marks outside 0-100 are rejected. Roll numbers are stored upper-case so `21it001` and `21IT001` are the same student.
- Existing rows in the old `cgpa.db` (SQLite) are not migrated.
