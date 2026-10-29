package com.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 学习画像闭环涉及的四张表的数据访问。
 *
 * student_profile（画像）、learning_effect_evaluation（评估流水）、
 * resource_practice_attempt（练习作答）、learning_plan（动态路径）
 * 原先在 LearningProfileService 里被二十余处内联 SQL 读写，
 * 同一条 upsert 语句甚至抄了三遍。这里作为唯一的 SQL 出口。
 */
@Repository
public class LearningProfileRepository {

    private static final Logger logger = LoggerFactory.getLogger(LearningProfileRepository.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int HISTORY_LIMIT = 30;
    private static final int RECOMMEND_CANDIDATE_LIMIT = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /** 动态调整路径每次只保留最新一条为启用状态，靠标题前缀识别 */
    private static final String ADAPTIVE_PLAN_TITLE = "动态调整路径";

    private static final String UPSERT_PROFILE_SQL =
            "INSERT INTO student_profile(student_id, profile_json, summary, update_time) VALUES (?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE profile_json=VALUES(profile_json), "
                    + "summary=VALUES(summary), update_time=VALUES(update_time)";

    private final JdbcTemplate jdbcTemplate;

    public LearningProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /* ==================== student_profile ==================== */

    public Optional<Map<String, Object>> findProfile(Integer studentid) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT profile_json AS profileJson, summary, update_time AS updateTime "
                            + "FROM student_profile WHERE student_id=?", studentid);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        } catch (Exception e) {
            logger.warn("读取学习画像失败 student={}: {}", studentid, e.getMessage());
            return Optional.empty();
        }
    }

    /** @return 是否写入成功；调用方据此判断要不要提示"结果未保存" */
    public boolean saveProfile(Integer studentid, String profileJson, String summary) {
        try {
            jdbcTemplate.update(UPSERT_PROFILE_SQL, studentid, profileJson, summary,
                    LocalDateTime.now().format(TIMESTAMP));
            return true;
        } catch (Exception e) {
            logger.warn("保存学习画像失败 student={}: {}", studentid, e.getMessage());
            return false;
        }
    }

    public boolean deleteProfile(Integer studentid) {
        try {
            jdbcTemplate.update("DELETE FROM student_profile WHERE student_id=?", studentid);
            return true;
        } catch (Exception e) {
            logger.warn("重置学习画像失败 student={}: {}", studentid, e.getMessage());
            return false;
        }
    }

    /* ==================== learning_effect_evaluation ==================== */

    /** 一次评估的落库入参 */
    public record NewEvaluation(Integer studentid, Integer courseid, Integer resourceId, String sourceType,
                                double preMastery, double postMastery, Integer correctCount, Integer totalCount,
                                String focusPointsJson, String resultJson, String recommendationJson) {
    }

    public Optional<Integer> insertEvaluation(NewEvaluation evaluation) {
        try {
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO learning_effect_evaluation(student_id,course_id,resource_id,source_type,"
                                + "pre_mastery,post_mastery,correct_count,total_count,focus_points,result_json,"
                                + "recommendation_json,create_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, evaluation.studentid());
                setNullableInt(ps, 2, evaluation.courseid());
                setNullableInt(ps, 3, evaluation.resourceId());
                ps.setString(4, evaluation.sourceType());
                ps.setDouble(5, evaluation.preMastery());
                ps.setDouble(6, evaluation.postMastery());
                setNullableInt(ps, 7, evaluation.correctCount());
                setNullableInt(ps, 8, evaluation.totalCount());
                ps.setString(9, evaluation.focusPointsJson());
                ps.setString(10, evaluation.resultJson());
                ps.setString(11, evaluation.recommendationJson());
                ps.setString(12, LocalDateTime.now().format(TIMESTAMP));
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            return key == null ? Optional.empty() : Optional.of(key.intValue());
        } catch (Exception e) {
            logger.warn("保存学习效果评估失败 student={}: {}", evaluation.studentid(), e.getMessage());
            return Optional.empty();
        }
    }

    /** courseid 为空时查全部课程 */
    public List<Map<String, Object>> listEvaluations(Integer studentid, Integer courseid) {
        String sql = "SELECT id, course_id AS courseId, resource_id AS resourceId, source_type AS sourceType, "
                + "pre_mastery AS preMastery, post_mastery AS mastery, correct_count AS correctCount, "
                + "total_count AS totalCount, focus_points AS focusPoints, create_time AS createTime "
                + "FROM learning_effect_evaluation WHERE student_id=? "
                + (courseid != null ? "AND course_id=? " : "")
                + "ORDER BY id DESC LIMIT " + HISTORY_LIMIT;
        try {
            return courseid != null
                    ? jdbcTemplate.queryForList(sql, studentid, courseid)
                    : jdbcTemplate.queryForList(sql, studentid);
        } catch (Exception e) {
            logger.warn("读取评估历史失败 student={}: {}", studentid, e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 最近一次评估的掌握度，作为下一次评估的对照基线 */
    public double latestMastery(Integer studentid, Integer courseid) {
        String sql = "SELECT post_mastery FROM learning_effect_evaluation WHERE student_id=? "
                + (courseid != null ? "AND course_id=? " : "") + "ORDER BY id DESC LIMIT 1";
        try {
            Number value = courseid != null
                    ? jdbcTemplate.queryForObject(sql, Number.class, studentid, courseid)
                    : jdbcTemplate.queryForObject(sql, Number.class, studentid);
            return value == null ? 0 : value.doubleValue();
        } catch (Exception e) {
            // 没有历史记录属正常情况，从 0 起算
            return 0;
        }
    }

    /* ==================== resource_practice_attempt ==================== */

    public record NewAttempt(Integer studentid, Integer resourceId, Integer courseid, String answersJson,
                             double score, int correctCount, int totalCount, String detailJson) {
    }

    public boolean insertAttempt(NewAttempt attempt) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO resource_practice_attempt(student_id,resource_id,course_id,answers_json,score,"
                            + "correct_count,total_count,detail_json,create_time) VALUES (?,?,?,?,?,?,?,?,?)",
                    attempt.studentid(), attempt.resourceId(), attempt.courseid(), attempt.answersJson(),
                    attempt.score(), attempt.correctCount(), attempt.totalCount(), attempt.detailJson(),
                    LocalDateTime.now().format(TIMESTAMP));
            return true;
        } catch (Exception e) {
            logger.warn("保存资源包练习记录失败 student={}: {}", attempt.studentid(), e.getMessage());
            return false;
        }
    }

    public long countAttempts(Integer studentid) {
        try {
            Long total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM resource_practice_attempt WHERE student_id=?", Long.class, studentid);
            return total == null ? 0L : total;
        } catch (Exception e) {
            logger.warn("统计练习记录失败 student={}: {}", studentid, e.getMessage());
            return 0L;
        }
    }

    /** 关联资源标题一并带出，前端不必再查一次 */
    public List<Map<String, Object>> listAttempts(Integer studentid, int pageSize, int offset) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT a.id, a.student_id AS studentId, a.resource_id AS resourceId, "
                            + "a.course_id AS courseId, a.score, a.correct_count AS correctCount, "
                            + "a.total_count AS totalCount, a.create_time AS createTime, "
                            + "r.title AS resourceTitle, r.course_name AS courseName "
                            + "FROM resource_practice_attempt a "
                            + "LEFT JOIN learning_resource r ON r.id = a.resource_id "
                            + "WHERE a.student_id=? ORDER BY a.id DESC LIMIT ? OFFSET ?",
                    studentid, Math.min(pageSize, MAX_PAGE_SIZE), offset);
        } catch (Exception e) {
            logger.warn("查询练习作答历史失败 student={}: {}", studentid, e.getMessage());
            return new ArrayList<>();
        }
    }

    /* ==================== learning_plan ==================== */

    /**
     * 写入一条动态调整路径，并把该学生之前的动态路径置为归档。
     * 两步都失败时返回空，调用方按"未生成新路径"处理。
     */
    public Optional<Integer> replaceAdaptivePlan(Integer studentid, Integer courseid,
                                                 String title, String summary, String contentJson) {
        try {
            jdbcTemplate.update(
                    "UPDATE learning_plan SET status=0 WHERE studentid=? AND status=1 AND title LIKE ?",
                    studentid, ADAPTIVE_PLAN_TITLE + "%");

            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO learning_plan(studentid,courseid,title,summary,content,status,create_time) "
                                + "VALUES (?,?,?,?,?,1,NOW())", Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, studentid);
                setNullableInt(ps, 2, courseid);
                ps.setString(3, title);
                ps.setString(4, summary);
                ps.setString(5, contentJson);
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            return key == null ? Optional.empty() : Optional.of(key.intValue());
        } catch (Exception e) {
            logger.warn("保存动态学习路径失败 student={}: {}", studentid, e.getMessage());
            return Optional.empty();
        }
    }

    /** 动态路径的标题带日期，便于学生区分不同批次 */
    public static String adaptivePlanTitle() {
        return ADAPTIVE_PLAN_TITLE + " · " + LocalDate.now();
    }

    /* ==================== 推荐候选 ==================== */

    /**
     * 同课程下该学生的其他资源包，供推荐算法按薄弱点匹配。
     * packJson 一并带出以便在内存里做关键词命中统计。
     */
    public List<Map<String, Object>> listRecommendCandidates(Integer studentid, Integer courseid,
                                                             Integer excludeResourceId) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT id, title, course_name AS courseName, create_time AS createTime, "
                            + "pack_json AS packJson FROM learning_resource "
                            + "WHERE owner_type='student' AND owner_id=? AND course_id=? AND id<>? "
                            + "ORDER BY id DESC LIMIT " + RECOMMEND_CANDIDATE_LIMIT,
                    studentid, courseid, excludeResourceId);
        } catch (Exception e) {
            logger.warn("读取推荐候选资源失败 student={}: {}", studentid, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) ps.setInt(index, value);
        else ps.setNull(index, Types.INTEGER);
    }
}
