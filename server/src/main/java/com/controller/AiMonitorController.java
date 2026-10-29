package com.controller;

import com.config.RequireRole;
import com.response.Result;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 调用监控（管理端）。
 * 基于 ai_call_log 聚合各智能体/AI 场景的调用量、成功率、平均耗时与近 24 小时趋势，
 * 用于回答「系统如何保障 AI 生成质量与响应效率」。
 */
@RestController
@RequestMapping("/monitor")
@RequireRole("admin")
public class AiMonitorController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/ai-stats")
    public Result<Map<String, Object>> aiStats(HttpSession session) {
        if (session.getAttribute("adminid") == null) {
            return Result.error(401, "请先登录管理员账号");
        }

        Map<String, Object> out = new HashMap<>();
        out.put("total", queryInt("SELECT COUNT(*) FROM ai_call_log"));
        out.put("success", queryInt("SELECT COUNT(*) FROM ai_call_log WHERE success=1"));
        out.put("avgElapsedMs", queryInt("SELECT ROUND(AVG(elapsed_ms)) FROM ai_call_log WHERE success=1"));
        out.put("last24h", queryInt("SELECT COUNT(*) FROM ai_call_log WHERE create_time >= NOW() - INTERVAL 1 DAY"));
        out.put("totalTokens", queryLong("SELECT COALESCE(SUM(total_tokens),0) FROM ai_call_log"));
        out.put("last24hTokens", queryLong(
                "SELECT COALESCE(SUM(total_tokens),0) FROM ai_call_log WHERE create_time >= NOW() - INTERVAL 1 DAY"));

        out.put("byScene", queryList(
            "SELECT scene, COUNT(*) AS calls, SUM(success) AS success, " +
            "ROUND(AVG(CASE WHEN success=1 THEN elapsed_ms END)) AS avgMs, " +
            "MAX(model) AS model, COALESCE(SUM(total_tokens),0) AS totalTokens, " +
            "ROUND(AVG(CASE WHEN success=1 AND total_tokens>0 THEN total_tokens END)) AS avgTokens, " +
            "MAX(create_time) AS lastCall " +
            "FROM ai_call_log GROUP BY scene ORDER BY calls DESC"));

        out.put("trend24h", queryList(
            "SELECT DATE_FORMAT(create_time, '%H:00') AS hour, COUNT(*) AS calls, SUM(success) AS success " +
            "FROM ai_call_log WHERE create_time >= NOW() - INTERVAL 1 DAY " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d %H') ORDER BY MIN(create_time)"));

        out.put("recentFails", queryList(
            "SELECT scene, model, error, elapsed_ms AS elapsedMs, create_time AS createTime " +
            "FROM ai_call_log WHERE success=0 ORDER BY id DESC LIMIT 10"));

        out.put("feedback", queryMap(
            "SELECT COUNT(*) AS total, SUM(CASE WHEN rating=1 THEN 1 ELSE 0 END) AS helpful, " +
            "SUM(CASE WHEN rating=-1 THEN 1 ELSE 0 END) AS unhelpful, " +
            "ROUND(100.0*SUM(CASE WHEN rating=1 THEN 1 ELSE 0 END)/NULLIF(COUNT(*),0),1) AS helpfulRate " +
            "FROM ai_feedback"));
        out.put("feedbackReasons", queryList(
            "SELECT reason, COUNT(*) AS count FROM ai_feedback " +
            "WHERE rating=-1 AND reason IS NOT NULL AND reason<>'' " +
            "GROUP BY reason ORDER BY count DESC LIMIT 8"));

        return Result.success(out);
    }

    private Integer queryInt(String sql) {
        try {
            Integer v = jdbcTemplate.queryForObject(sql, Integer.class);
            return v == null ? 0 : v;
        } catch (Exception e) {
            return 0;
        }
    }

    private Long queryLong(String sql) {
        try {
            Long v = jdbcTemplate.queryForObject(sql, Long.class);
            return v == null ? 0L : v;
        } catch (Exception e) {
            return 0L;
        }
    }

    private List<Map<String, Object>> queryList(String sql) {
        try {
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private Map<String, Object> queryMap(String sql) {
        try {
            return jdbcTemplate.queryForMap(sql);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
