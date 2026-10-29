package com.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI 学情报告缓存。
 *
 * 报告生成一次要调好几轮大模型，耗时几十秒，所以按「报告类型 + 关联对象」
 * 缓存最近一份，学生或教师再次打开时直接回显。
 */
@Repository
public class AiReportRepository {

    private static final Logger logger = LoggerFactory.getLogger(AiReportRepository.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    public AiReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param reportType 报告类型，如 student / class
     * @param refId      学生 id 或课程 id
     */
    public Optional<Map<String, Object>> findLatest(String reportType, Integer refId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT title, content, stats_json AS statsJson, create_time AS createTime "
                            + "FROM ai_report WHERE report_type=? AND ref_id=? ORDER BY id DESC LIMIT 1",
                    reportType, refId);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        } catch (Exception e) {
            logger.warn("读取 AI 报告缓存失败 type={} ref={}: {}", reportType, refId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 覆盖式保存：同一对象只留最近一份。
     * 删除与插入必须在同一事务里，否则并发生成时可能出现「删掉了旧的、新的又没写进去」。
     */
    @Transactional
    public void replaceLatest(String reportType, Integer refId, String title,
                              String content, String statsJson) {
        try {
            jdbcTemplate.update("DELETE FROM ai_report WHERE report_type=? AND ref_id=?", reportType, refId);
            jdbcTemplate.update(
                    "INSERT INTO ai_report(report_type, ref_id, title, content, stats_json, create_time) "
                            + "VALUES (?,?,?,?,?,?)",
                    reportType, refId, title, content, statsJson, LocalDateTime.now().format(TIMESTAMP));
        } catch (Exception e) {
            logger.warn("保存 AI 报告失败 type={} ref={}: {}", reportType, refId, e.getMessage());
        }
    }
}
