package com.controller;

import com.config.RequireRole;
import com.model.AuditLog;
import com.response.Result;
import com.service.AuditLogService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作审计日志控制器（管理员）
 */
@RestController
@RequestMapping("/audit")
@RequireRole("admin")
public class AuditLogController {

    @Autowired
    private AuditLogService auditLogService;

    /**
     * 分页查询审计日志，支持按用户类型、动作、关键字过滤。
     */
    @GetMapping("/list")
    public Result<List<AuditLog>> list(
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "15") Integer pageSize,
            HttpSession session) {
        if (!isAdmin(session)) return Result.error(401, "请先登录管理员账号");
        List<AuditLog> list = auditLogService.getList(userType, action, keyword, page, pageSize);
        Long total = auditLogService.getCount(userType, action, keyword);
        return Result.success(list, total);
    }

    /**
     * 审计概览：按动作统计 + 近14天趋势 + 总量。
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(HttpSession session) {
        if (!isAdmin(session)) return Result.error(401, "请先登录管理员账号");
        Map<String, Object> data = new HashMap<>();
        data.put("actionStat", auditLogService.getActionStat());
        data.put("dailyTrend", auditLogService.getDailyTrend(14));
        data.put("total", auditLogService.getCount(null, null, null));
        return Result.success(data);
    }

    /**
     * 导出审计日志为 CSV（含 UTF-8 BOM，Excel 直接打开不乱码）。
     */
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String keyword,
            HttpServletResponse response,
            HttpSession session) throws IOException {
        if (!isAdmin(session)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "请先登录管理员账号");
            return;
        }
        List<AuditLog> list = auditLogService.getList(userType, action, keyword, 1, 10000);
        response.setContentType("text/csv;charset=UTF-8");
        String fileName = URLEncoder.encode("操作审计日志.csv", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        StringBuilder sb = new StringBuilder();
        sb.append("\uFEFF"); // UTF-8 BOM
        sb.append("时间,用户类型,用户ID,用户名,动作,模块,对象,方法,IP,结果,耗时(ms)\n");
        for (AuditLog log : list) {
            sb.append(csv(log.getCreateTime() != null ? sdf.format(log.getCreateTime()) : ""))
              .append(',').append(csv(log.getUserType()))
              .append(',').append(csv(String.valueOf(log.getUserId() == null ? "" : log.getUserId())))
              .append(',').append(csv(log.getUserName()))
              .append(',').append(csv(log.getAction()))
              .append(',').append(csv(log.getModule()))
              .append(',').append(csv(log.getTarget()))
              .append(',').append(csv(log.getMethod()))
              .append(',').append(csv(log.getIp()))
              .append(',').append(csv(log.getStatus() != null && log.getStatus() == 1 ? "成功" : "失败"))
              .append(',').append(csv(String.valueOf(log.getCostMs() == null ? "" : log.getCostMs())))
              .append('\n');
        }
        try (OutputStream os = response.getOutputStream()) {
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
    }

    /**
     * 清理 N 天之前的日志（默认90天）。
     */
    @DeleteMapping("/cleanup")
    public Result<Integer> cleanup(@RequestParam(defaultValue = "90") Integer days, HttpSession session) {
        if (!isAdmin(session)) return Result.error(401, "请先登录管理员账号");
        int rows = auditLogService.cleanup(days);
        return Result.success("已清理 " + rows + " 条历史日志", rows);
    }

    private String csv(String v) {
        if (v == null) {
            return "";
        }
        String s = v.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ");
        if (s.contains(",") || s.contains("\"")) {
            return "\"" + s + "\"";
        }
        return s;
    }

    private boolean isAdmin(HttpSession session) {
        return session != null && session.getAttribute("adminid") != null;
    }
}
