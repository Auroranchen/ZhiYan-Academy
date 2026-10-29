package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 企业数据库基线：接管原先散落在 Service @PostConstruct 中的 DDL，
 * 并为核心教学数据补充关系完整性约束。
 */
public class V1__EnterpriseSchemaBaseline extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        // core_feature_tables 必须在 addCoreConstraints 之前执行，
        // 否则空库上这几张表还不存在，外键会被 tableExists 判断跳过。
        runScript(connection, "db/core_feature_tables.sql");
        runScript(connection, "db/feature_expansion.sql");
        runScript(connection, "db/learning_effect_rag.sql");
        runScript(connection, "db/academic_term.sql");
        createRuntimeTables(connection);
        upgradeLegacyColumns(connection);
        repairLegacyData(connection);
        addCoreConstraints(connection);
    }

    private void runScript(Connection connection, String path) {
        ScriptUtils.executeSqlScript(connection,
                new EncodedResource(new ClassPathResource(path), StandardCharsets.UTF_8));
    }

    private void createRuntimeTables(Connection connection) throws SQLException {
        execute(connection, """
                CREATE TABLE IF NOT EXISTS ai_feedback (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  user_role VARCHAR(16) NOT NULL, user_id INT NOT NULL,
                  message_key VARCHAR(64) NOT NULL, scene VARCHAR(64) NOT NULL,
                  rating TINYINT NOT NULL, reason VARCHAR(200),
                  question_preview VARCHAR(500), answer_preview VARCHAR(500),
                  create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
                  UNIQUE KEY uk_user_message(user_role,user_id,message_key),
                  KEY idx_scene_time(scene,create_time), KEY idx_rating_time(rating,create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI回答质量反馈'
                """);
        execute(connection, """
                CREATE TABLE IF NOT EXISTS ai_call_log (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY, scene VARCHAR(64) NOT NULL,
                  success TINYINT NOT NULL, elapsed_ms INT NOT NULL, model VARCHAR(100),
                  prompt_tokens INT DEFAULT 0, completion_tokens INT DEFAULT 0, total_tokens INT DEFAULT 0,
                  error VARCHAR(300), create_time DATETIME NOT NULL,
                  KEY idx_scene_time(scene,create_time), KEY idx_time(create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用监控日志'
                """);
        execute(connection, """
                CREATE TABLE IF NOT EXISTS ai_report (
                  id INT AUTO_INCREMENT PRIMARY KEY, report_type VARCHAR(20) NOT NULL,
                  ref_id INT NOT NULL, title VARCHAR(200), content MEDIUMTEXT,
                  stats_json MEDIUMTEXT, create_time DATETIME,
                  KEY idx_type_ref(report_type,ref_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI学情报告缓存'
                """);
        execute(connection, """
                CREATE TABLE IF NOT EXISTS enroll_application (
                  id INT AUTO_INCREMENT PRIMARY KEY, school VARCHAR(200) NOT NULL,
                  contact_name VARCHAR(100) NOT NULL, contact_role VARCHAR(100),
                  phone VARCHAR(30) NOT NULL, remark VARCHAR(1000), attachment VARCHAR(255),
                  attachment_name VARCHAR(255), status INT DEFAULT 0, create_time DATETIME,
                  KEY idx_enroll_status_time(status,create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='高校入驻申请'
                """);
        execute(connection, """
                CREATE TABLE IF NOT EXISTS learning_resource (
                  id INT AUTO_INCREMENT PRIMARY KEY, owner_type VARCHAR(16) NOT NULL,
                  owner_id INT NOT NULL, scope_type VARCHAR(16) NOT NULL, course_id INT,
                  course_name VARCHAR(120), title VARCHAR(200), pack_json MEDIUMTEXT,
                  source_id INT, create_time DATETIME,
                  KEY idx_owner(owner_type,owner_id), KEY idx_scope(scope_type,course_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个性化学习资源包'
                """);
        execute(connection, """
                CREATE TABLE IF NOT EXISTS student_profile (
                  student_id INT PRIMARY KEY, profile_json MEDIUMTEXT,
                  summary VARCHAR(500), update_time DATETIME
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话式动态学习画像'
                """);
        execute(connection, """
                CREATE TABLE IF NOT EXISTS pipeline_run (
                  id INT AUTO_INCREMENT PRIMARY KEY, owner_type VARCHAR(16) NOT NULL,
                  owner_id INT NOT NULL, course_id INT, quiz_count INT, status VARCHAR(16) NOT NULL,
                  steps_json MEDIUMTEXT, resource_id INT, error VARCHAR(300),
                  create_time DATETIME, update_time DATETIME,
                  KEY idx_owner(owner_type,owner_id,id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多智能体流水线运行记录'
                """);
        execute(connection, """
                CREATE TABLE IF NOT EXISTS peer_review_task (
                  homeworkid INT PRIMARY KEY, teacherid INT, reviewers_per INT DEFAULT 2,
                  max_score INT DEFAULT 100, anonymous TINYINT DEFAULT 1,
                  status TINYINT DEFAULT 1, create_time DATETIME
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业互评配置'
                """);
        execute(connection, """
                CREATE TABLE IF NOT EXISTS peer_review (
                  id INT AUTO_INCREMENT PRIMARY KEY, homeworkid INT NOT NULL,
                  reviewerid INT NOT NULL, submitid INT NOT NULL, revieweeid INT NOT NULL,
                  score DECIMAL(5,1), comment TEXT, status TINYINT DEFAULT 0,
                  assign_time DATETIME, submit_time DATETIME,
                  UNIQUE KEY uk_reviewer_submit(reviewerid,submitid),
                  KEY idx_homeworkid(homeworkid), KEY idx_reviewerid(reviewerid), KEY idx_revieweeid(revieweeid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业互评任务/结果'
                """);
        // 列名必须与 AttendSessionMapper.xml 一致：checkin_code / title / type /
        // late_minutes / duration / create_time。此前这里建的是 session_code / session_name，
        // 与映射对不上，全新部署的库一用签到功能就会报 Unknown column。
        execute(connection, """
                CREATE TABLE IF NOT EXISTS attend_session (
                  id INT NOT NULL AUTO_INCREMENT,
                  courseid INT NOT NULL COMMENT '课程ID',
                  classid INT NOT NULL COMMENT '班级ID',
                  teacherid INT NOT NULL COMMENT '发起教师ID',
                  checkin_code VARCHAR(10) NOT NULL COMMENT '签到码',
                  title VARCHAR(100) DEFAULT NULL COMMENT '签到标题',
                  type TINYINT NOT NULL DEFAULT 1 COMMENT '1=签到码 2=一键签到 3=手势',
                  late_minutes INT NOT NULL DEFAULT 5 COMMENT '迟到阈值(分钟)',
                  duration INT NOT NULL DEFAULT 10 COMMENT '签到持续时长(分钟)',
                  status TINYINT NOT NULL DEFAULT 1 COMMENT '1=进行中 2=已结束',
                  start_time DATETIME NOT NULL, end_time DATETIME NOT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY(id),
                  KEY idx_attend_session_owner(courseid,classid,status),
                  KEY idx_attend_session_time(start_time,end_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课堂签到会话'
                """);
    }

    private void upgradeLegacyColumns(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "studenthomework", "grading_status",
                "VARCHAR(20) DEFAULT NULL COMMENT 'AI评分状态'");
        addColumnIfMissing(connection, "student_attendance", "session_id",
                "INT NULL COMMENT '关联签到会话ID'");
        addColumnIfMissing(connection, "enroll_application", "attachment", "VARCHAR(255)");
        addColumnIfMissing(connection, "enroll_application", "attachment_name", "VARCHAR(255)");
        addColumnIfMissing(connection, "learning_resource", "source_id", "INT");
        addColumnIfMissing(connection, "ai_call_log", "model", "VARCHAR(100)");
        addColumnIfMissing(connection, "ai_call_log", "prompt_tokens", "INT DEFAULT 0");
        addColumnIfMissing(connection, "ai_call_log", "completion_tokens", "INT DEFAULT 0");
        addColumnIfMissing(connection, "ai_call_log", "total_tokens", "INT DEFAULT 0");
    }

    private void repairLegacyData(Connection connection) throws SQLException {
        if (tableExists(connection, "course")) {
            execute(connection, """
                    UPDATE course c
                    SET c.coursecode = CONCAT('C', LPAD(c.courseid, 10, '0'))
                    WHERE c.coursecode IS NULL OR TRIM(c.coursecode) = ''
                    """);
        }
        if (tableExists(connection, "courseclass")) {
            execute(connection, """
                    DELETE newer FROM courseclass newer
                    JOIN courseclass older
                      ON newer.courseid=older.courseid AND newer.classid=older.classid AND newer.id>older.id
                    """);
            addIndexIfMissing(connection, "courseclass", "uk_courseclass", true, "courseid,classid");
        }
    }

    private void addCoreConstraints(Connection connection) throws SQLException {
        addForeignKey(connection, "course", "fk_course_teacher", "teacherid", "teacher", "teacherid", "RESTRICT");
        addForeignKey(connection, "courseclass", "fk_courseclass_course", "courseid", "course", "courseid", "CASCADE");
        addForeignKey(connection, "courseclass", "fk_courseclass_class", "classid", "classinfo", "classid", "CASCADE");
        addForeignKey(connection, "student", "fk_student_class", "classid", "classinfo", "classid", "RESTRICT");
        addForeignKey(connection, "student", "fk_student_major", "majorid", "major", "majorid", "RESTRICT");
        addForeignKey(connection, "homework", "fk_homework_course", "courseid", "course", "courseid", "RESTRICT");
        addForeignKey(connection, "homework", "fk_homework_teacher", "teacherid", "teacher", "teacherid", "RESTRICT");
        addForeignKey(connection, "studenthomework", "fk_submission_homework", "homeworkid", "homework", "homeworkid", "CASCADE");
        addForeignKey(connection, "studenthomework", "fk_submission_student", "studentid", "student", "studentid", "RESTRICT");
        addForeignKey(connection, "course_resource", "fk_resource_course", "courseid", "course", "courseid", "CASCADE");
        addForeignKey(connection, "student_attendance", "fk_attendance_course", "courseid", "course", "courseid", "CASCADE");
        addForeignKey(connection, "student_attendance", "fk_attendance_student", "studentid", "student", "studentid", "CASCADE");
        addForeignKey(connection, "exam", "fk_exam_course", "courseid", "course", "courseid", "RESTRICT");
        addForeignKey(connection, "exam_record", "fk_exam_record_exam", "examid", "exam", "examid", "CASCADE");
        addForeignKey(connection, "exam_record", "fk_exam_record_student", "studentid", "student", "studentid", "CASCADE");
        addForeignKey(connection, "student_profile", "fk_profile_student", "student_id", "student", "studentid", "CASCADE");
    }

    private void addColumnIfMissing(Connection c, String table, String column, String definition) throws SQLException {
        if (tableExists(c, table) && !columnExists(c, table, column)) {
            execute(c, "ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
        }
    }

    private void addIndexIfMissing(Connection c, String table, String index, boolean unique, String columns) throws SQLException {
        if (!indexExists(c, table, index)) {
            execute(c, "ALTER TABLE `" + table + "` ADD " + (unique ? "UNIQUE " : "")
                    + "INDEX `" + index + "` (" + columns + ")");
        }
    }

    private void addForeignKey(Connection c, String table, String name, String column,
                               String refTable, String refColumn, String onDelete) throws SQLException {
        if (!tableExists(c, table) || !tableExists(c, refTable)
                || !columnExists(c, table, column) || foreignKeyExists(c, table, name)) return;
        execute(c, "ALTER TABLE `" + table + "` ADD CONSTRAINT `" + name + "` FOREIGN KEY (`"
                + column + "`) REFERENCES `" + refTable + "` (`" + refColumn + "`) ON DELETE " + onDelete);
    }

    private boolean tableExists(Connection c, String table) throws SQLException {
        return count(c, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?", table) > 0;
    }

    private boolean columnExists(Connection c, String table, String column) throws SQLException {
        return count(c, "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?", table, column) > 0;
    }

    private boolean indexExists(Connection c, String table, String index) throws SQLException {
        return count(c, "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=?", table, index) > 0;
    }

    private boolean foreignKeyExists(Connection c, String table, String name) throws SQLException {
        return count(c, "SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME=?", table, name) > 0;
    }

    private int count(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
