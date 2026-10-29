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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 多智能体流水线运行记录。
 *
 * 一次运行会跨越多个线程与请求：启动在 HTTP 线程、执行在线程池、
 * SSE 订阅又是另一条连接，中间还要不断回写步骤快照。
 * 把状态流转的 SQL 收在这里，避免各处对 status 取值的理解出现分歧。
 */
@Repository
public class PipelineRunRepository {

    private static final Logger logger = LoggerFactory.getLogger(PipelineRunRepository.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** error 字段长度有限，超长的异常信息要截断后再入库 */
    private static final int MAX_ERROR_LENGTH = 290;

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_ERROR = "error";

    private final JdbcTemplate jdbcTemplate;

    public PipelineRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 应用重启后残留的 running 记录已经没有线程在跑，标记为中断。
     * 否则用户会一直看到「进行中」，且因为并发限制无法重新发起。
     */
    public void markInterruptedRunsAsError() {
        try {
            jdbcTemplate.update(
                    "UPDATE pipeline_run SET status=?, error=? WHERE status=?",
                    STATUS_ERROR, "服务重启导致中断", STATUS_RUNNING);
        } catch (Exception e) {
            logger.warn("清理中断的流水线失败: {}", e.getMessage());
        }
    }

    /** 同一用户同时只允许一条运行中的流水线 */
    public Optional<Integer> findRunningId(String ownerType, Integer ownerId) {
        try {
            List<Integer> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM pipeline_run WHERE owner_type=? AND owner_id=? AND status=? "
                            + "ORDER BY id DESC LIMIT 1",
                    Integer.class, ownerType, ownerId, STATUS_RUNNING);
            return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
        } catch (Exception e) {
            logger.warn("查询运行中的流水线失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** @return 新运行的 id；创建失败时为空 */
    public Optional<Integer> create(String ownerType, Integer ownerId, Integer courseId, int quizCount) {
        try {
            String now = LocalDateTime.now().format(TIMESTAMP);
            // 用 KeyHolder 取自增主键：LAST_INSERT_ID() 依赖同一连接，连接池下不可靠
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO pipeline_run(owner_type, owner_id, course_id, quiz_count, status, "
                                + "steps_json, create_time, update_time) VALUES (?,?,?,?,?,'{}',?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, ownerType);
                ps.setInt(2, ownerId);
                if (courseId != null) ps.setInt(3, courseId); else ps.setNull(3, Types.INTEGER);
                ps.setInt(4, quizCount);
                ps.setString(5, STATUS_RUNNING);
                ps.setString(6, now);
                ps.setString(7, now);
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            return key == null ? Optional.empty() : Optional.of(key.intValue());
        } catch (Exception e) {
            logger.warn("创建流水线失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 按归属查询，查不到即无权访问 */
    public Optional<Map<String, Object>> findOwned(Integer runId, String ownerType, Integer ownerId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, status, steps_json AS stepsJson, resource_id AS resourceId, error "
                            + "FROM pipeline_run WHERE id=? AND owner_type=? AND owner_id=?",
                    runId, ownerType, ownerId);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        } catch (Exception e) {
            logger.warn("查询流水线 {} 失败: {}", runId, e.getMessage());
            return Optional.empty();
        }
    }

    /** 页面加载时用它恢复上次的运行状态 */
    public Optional<Map<String, Object>> findLatest(String ownerType, Integer ownerId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, status, course_id AS courseId, quiz_count AS quizCount, "
                            + "resource_id AS resourceId, create_time AS createTime FROM pipeline_run "
                            + "WHERE owner_type=? AND owner_id=? ORDER BY id DESC LIMIT 1",
                    ownerType, ownerId);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        } catch (Exception e) {
            logger.warn("查询最近流水线失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 找出并标记**卡死**的运行。
     *
     * <p>判定依据是 {@code update_time}：每完成一步都会回写并刷新它，因此一条还活着的
     * 运行其 {@code update_time} 一定在持续推进。停在一个时间点不动的，就是已经没有
     * 线程在推进它了——典型成因是某次 AI 调用彻底挂住，重试也耗尽了。
     *
     * @return 被标记的运行 id；调用方据此结束对应的 SSE 流，否则前端会永远转圈
     */
    public List<Integer> markStaleRunsAsError(long staleTimeoutMs) {
        // 阈值给 0 或负数时按 1 秒处理：宁可判定激进些，也不要把「立刻判死」当成合法输入
        long seconds = Math.max(1, staleTimeoutMs / 1000);
        String cutoff = LocalDateTime.now().minusSeconds(seconds).format(TIMESTAMP);

        List<Integer> staleIds;
        try {
            staleIds = jdbcTemplate.queryForList(
                    "SELECT id FROM pipeline_run WHERE status=? "
                            + "AND COALESCE(update_time, create_time) < ?",
                    Integer.class, STATUS_RUNNING, cutoff);
        } catch (Exception e) {
            // 看门狗是定时任务：一次查询失败不该让后续扫描也不再发生
            logger.warn("查询卡死的流水线失败: {}", e.getMessage());
            return List.of();
        }

        for (Integer runId : staleIds) {
            finish(runId, STATUS_ERROR, null, "运行超时未推进，已自动终止");
        }
        if (!staleIds.isEmpty()) {
            logger.warn("看门狗终止了 {} 条卡死的流水线: {}", staleIds.size(), staleIds);
        }
        return staleIds;
    }

    /** 每完成一步就回写快照，断线重连时用于回放 */
    public void saveSteps(Integer runId, String stepsJson) {
        try {
            jdbcTemplate.update("UPDATE pipeline_run SET steps_json=?, update_time=? WHERE id=?",
                    stepsJson, LocalDateTime.now().format(TIMESTAMP), runId);
        } catch (Exception e) {
            logger.warn("保存流水线 {} 步骤失败: {}", runId, e.getMessage());
        }
    }

    public void finish(Integer runId, String status, Integer resourceId, String error) {
        try {
            String trimmed = error != null && error.length() > MAX_ERROR_LENGTH
                    ? error.substring(0, MAX_ERROR_LENGTH) : error;
            jdbcTemplate.update(
                    "UPDATE pipeline_run SET status=?, resource_id=?, error=?, update_time=? WHERE id=?",
                    status, resourceId, trimmed, LocalDateTime.now().format(TIMESTAMP), runId);
        } catch (Exception e) {
            logger.warn("更新流水线 {} 状态失败: {}", runId, e.getMessage());
        }
    }
}
