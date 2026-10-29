-- 学年学期主数据（现有数据库升级脚本，可重复执行）
CREATE TABLE IF NOT EXISTS academic_term (
    term_id INT NOT NULL AUTO_INCREMENT COMMENT '学期ID',
    term_code VARCHAR(20) NOT NULL COMMENT '学期编码，如2025-2026-2',
    term_name VARCHAR(80) NOT NULL COMMENT '显示名称',
    start_date DATE NULL COMMENT '开始日期',
    end_date DATE NULL COMMENT '结束日期',
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED' COMMENT 'PLANNED/ACTIVE/ARCHIVED',
    is_current TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否当前学期',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (term_id),
    UNIQUE KEY uk_academic_term_code (term_code),
    KEY idx_academic_term_state (is_current, status),
    KEY idx_academic_term_dates (start_date, end_date),
    CONSTRAINT chk_academic_term_status CHECK (status IN ('PLANNED', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_academic_term_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学年学期主数据';

-- 将历史课程中的学期编码回填为正式主数据。
INSERT IGNORE INTO academic_term
    (term_code, term_name, start_date, end_date, status, is_current)
SELECT DISTINCT
    TRIM(c.semester),
    CONCAT(SUBSTRING(TRIM(c.semester), 1, 9), '学年第', RIGHT(TRIM(c.semester), 1), '学期'),
    CASE WHEN RIGHT(TRIM(c.semester), 1) = '1'
         THEN STR_TO_DATE(CONCAT(LEFT(TRIM(c.semester), 4), '-09-01'), '%Y-%m-%d')
         ELSE STR_TO_DATE(CONCAT(CAST(LEFT(TRIM(c.semester), 4) AS UNSIGNED) + 1, '-02-01'), '%Y-%m-%d') END,
    CASE WHEN RIGHT(TRIM(c.semester), 1) = '1'
         THEN STR_TO_DATE(CONCAT(CAST(LEFT(TRIM(c.semester), 4) AS UNSIGNED) + 1, '-01-31'), '%Y-%m-%d')
         ELSE STR_TO_DATE(CONCAT(CAST(LEFT(TRIM(c.semester), 4) AS UNSIGNED) + 1, '-08-31'), '%Y-%m-%d') END,
    'PLANNED',
    0
FROM course c
WHERE c.semester REGEXP '^[0-9]{4}-[0-9]{4}-[1-3]$';

-- 确保全新数据库也存在当前学期，并且只能有一个当前学期。
SET @academic_start_year = CASE WHEN MONTH(CURDATE()) >= 9 THEN YEAR(CURDATE()) ELSE YEAR(CURDATE()) - 1 END;
SET @academic_term_no = CASE WHEN MONTH(CURDATE()) >= 9 OR MONTH(CURDATE()) = 1 THEN 1 ELSE 2 END;
SET @academic_term_code = CONCAT(@academic_start_year, '-', @academic_start_year + 1, '-', @academic_term_no);

INSERT INTO academic_term
    (term_code, term_name, start_date, end_date, status, is_current)
VALUES
    (@academic_term_code,
     CONCAT(@academic_start_year, '-', @academic_start_year + 1, '学年第', @academic_term_no, '学期'),
     CASE WHEN @academic_term_no = 1
          THEN STR_TO_DATE(CONCAT(@academic_start_year, '-09-01'), '%Y-%m-%d')
          ELSE STR_TO_DATE(CONCAT(@academic_start_year + 1, '-02-01'), '%Y-%m-%d') END,
     CASE WHEN @academic_term_no = 1
          THEN STR_TO_DATE(CONCAT(@academic_start_year + 1, '-01-31'), '%Y-%m-%d')
          ELSE STR_TO_DATE(CONCAT(@academic_start_year + 1, '-08-31'), '%Y-%m-%d') END,
     'ACTIVE', 1)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

UPDATE academic_term
SET is_current = CASE WHEN term_code = @academic_term_code THEN 1 ELSE 0 END,
    status = CASE
        WHEN term_code = @academic_term_code THEN 'ACTIVE'
        WHEN end_date < CURDATE() THEN 'ARCHIVED'
        ELSE 'PLANNED'
    END;
