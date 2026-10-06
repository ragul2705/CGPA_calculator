CREATE TABLE IF NOT EXISTS students (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    roll       VARCHAR(30)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS courses (
    code     VARCHAR(20)      PRIMARY KEY,
    title    VARCHAR(150)     NOT NULL,
    credits  TINYINT UNSIGNED NOT NULL,
    semester TINYINT UNSIGNED NOT NULL,
    INDEX idx_courses_semester (semester)
);

CREATE TABLE IF NOT EXISTS grade_scale (
    grade  VARCHAR(3)   PRIMARY KEY,
    points DECIMAL(3,1) NOT NULL
);

-- One row per calculation. Rows are never updated except to fill in the CGPA,
-- so every past result stays available for audit.
CREATE TABLE IF NOT EXISTS results (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id    INT          NOT NULL,
    semester      TINYINT UNSIGNED NOT NULL,
    input_mode    VARCHAR(6)   NOT NULL,
    total_credits INT          NOT NULL,
    sgpa          DECIMAL(4,2) NOT NULL,
    cgpa          DECIMAL(4,2) NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_results_student FOREIGN KEY (student_id) REFERENCES students (id),
    INDEX idx_results_student_sem (student_id, semester)
);

-- The exact inputs behind each result: course, marks/grade entered, grade and points applied.
CREATE TABLE IF NOT EXISTS result_items (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    result_id   BIGINT       NOT NULL,
    course_code VARCHAR(20)  NOT NULL,
    credits     TINYINT UNSIGNED NOT NULL,
    input_value VARCHAR(10)  NOT NULL,
    grade       VARCHAR(3)   NOT NULL,
    grade_point DECIMAL(3,1) NOT NULL,
    CONSTRAINT fk_items_result FOREIGN KEY (result_id)   REFERENCES results (id) ON DELETE CASCADE,
    CONSTRAINT fk_items_course FOREIGN KEY (course_code) REFERENCES courses (code),
    CONSTRAINT fk_items_grade  FOREIGN KEY (grade)       REFERENCES grade_scale (grade)
);
