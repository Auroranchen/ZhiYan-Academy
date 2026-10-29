package com.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 个性化学习资源包的数据访问。
 *
 * 这张表的读写原先直接写在 LearningAgentService 里，和智能体的提示词编排混在一起，
 * 建表语句、字段别名散落在八处。抽成仓储后，SQL 只出现在这一个文件。
 *
 * 所有权限判定都靠 SQL 里的 owner_type + owner_id 条件完成：
 * 查不到就是不存在或无权访问，调用方不需要再单独校验归属。
 */
@Repository
public class LearningResourceRepository {

    private static final Logger logger = LoggerFactory.getLogger(LearningResourceRepository.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 资源库列表只做浏览用，取最近的即可 */
    private static final int LIBRARY_LIMIT = 100;

    private static final String INSERT_SQL =
            "INSERT INTO learning_resource"
                    + "(owner_type, owner_id, scope_type, course_id, course_name, title, pack_json, source_id, create_time) "
                    + "VALUES (?,?,?,?,?,?,?,?,?)";

    private final JdbcTemplate jdbcTemplate;

    public LearningResourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 新建资源包时的入参 */
    public record NewResource(String ownerType, Integer ownerId, String scopeType,
                              Integer courseId, String courseName, String title,
                              String packJson, Integer sourceId) {
    }

    /**
     * 插入一条资源包。
     *
     * @return 自增主键；失败时返回空
     */
    public Optional<Integer> insert(NewResource resource) {
        try {
            // 用 KeyHolder 取自增主键：LAST_INSERT_ID() 依赖同一连接，
            // 连接池下可能读到其他会话的值
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            String now = LocalDateTime.now().format(TIMESTAMP);

            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, resource.ownerType());
                ps.setInt(2, resource.ownerId());
                ps.setString(3, resource.scopeType());
                setNullableInt(ps, 4, resource.courseId());
                ps.setString(5, resource.courseName());
                ps.setString(6, resource.title());
                ps.setString(7, resource.packJson());
                setNullableInt(ps, 8, resource.sourceId());
                ps.setString(9, now);
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            return key == null ? Optional.empty() : Optional.of(key.intValue());
        } catch (Exception e) {
            logger.warn("保存资源包失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 某人资源库的概要列表，按时间倒序 */
    public List<Map<String, Object>> listByOwner(String ownerType, Integer ownerId) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT id, scope_type AS scopeType, course_name AS courseName, title, "
                            + "create_time AS createTime FROM learning_resource "
                            + "WHERE owner_type=? AND owner_id=? ORDER BY id DESC LIMIT " + LIBRARY_LIMIT,
                    ownerType, ownerId);
        } catch (Exception e) {
            logger.warn("读取资源库失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 资源包详情；不属于该 owner 时返回空 */
    public Optional<Map<String, Object>> findOwned(String ownerType, Integer ownerId, Integer id) {
        return queryOne(
                "SELECT title, pack_json AS packJson, create_time AS createTime FROM learning_resource "
                        + "WHERE id=? AND owner_type=? AND owner_id=?",
                id, ownerType, ownerId);
    }

    /** 教师推送前先取出源资源包，同时完成归属校验 */
    public Optional<Map<String, Object>> findTeacherResource(Integer teacherid, Integer id) {
        return queryOne(
                "SELECT course_id AS courseId, course_name AS courseName, title, pack_json AS packJson "
                        + "FROM learning_resource WHERE id=? AND owner_type='teacher' AND owner_id=?",
                id, teacherid);
    }

    /** 课程下全部学生，用于批量推送 */
    public List<Integer> findStudentIdsByCourse(Integer courseId) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT studentid FROM student "
                            + "WHERE classid IN (SELECT classid FROM courseclass WHERE courseid=?)",
                    Integer.class, courseId);
        } catch (Exception e) {
            logger.warn("读取课程学生失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 同一来源只保留最新一份，避免教师重复推送后学生资源库堆积 */
    public void deletePushedCopy(Integer studentId, Integer sourceId) {
        try {
            jdbcTemplate.update(
                    "DELETE FROM learning_resource WHERE owner_type='student' AND owner_id=? AND source_id=?",
                    studentId, sourceId);
        } catch (Exception e) {
            logger.warn("清理旧推送副本失败: {}", e.getMessage());
        }
    }

    /** @return 是否真的删掉了；未删到说明不存在或不属于该 owner */
    public boolean deleteOwned(String ownerType, Integer ownerId, Integer id) {
        try {
            return jdbcTemplate.update(
                    "DELETE FROM learning_resource WHERE id=? AND owner_type=? AND owner_id=?",
                    id, ownerType, ownerId) > 0;
        } catch (Exception e) {
            logger.warn("删除资源包失败: {}", e.getMessage());
            return false;
        }
    }

    private Optional<Map<String, Object>> queryOne(String sql, Object... args) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        } catch (Exception e) {
            logger.warn("查询资源包失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws java.sql.SQLException {
        if (value != null) ps.setInt(index, value);
        else ps.setNull(index, Types.INTEGER);
    }
}
