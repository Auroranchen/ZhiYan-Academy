package com.controller;

import com.config.RequireRole;
import com.model.PlagiarismReport;
import com.response.Result;
import com.service.PlagiarismService;
import com.service.ResourceAccessService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 作业查重控制器（教师端）。
 *
 * 此前整个控制器既没有角色限制也没有归属校验：任何登录用户都能对任意作业发起查重、
 * 触发 AI 原创性检测，并读到含学生姓名与相似度明细的报告。
 * 现统一要求教师或管理员身份，且必须是该作业所属课程的任课教师。
 */
@RestController
@RequestMapping("/plagiarism")
@RequireRole({"teacher", "admin"})
public class PlagiarismController {

    @Autowired
    private PlagiarismService plagiarismService;

    @Autowired
    private ResourceAccessService resourceAccess;

    /**
     * 发起查重。
     */
    @PostMapping("/check")
    public Result<PlagiarismReport> check(@RequestBody Map<String, Object> body, HttpSession session) {
        Integer homeworkid = toInt(body.get("homeworkid"));
        if (!resourceAccess.canManageHomework(homeworkid, session)) {
            return Result.error(403, "无权对该作业发起查重");
        }
        BigDecimal threshold = body.get("threshold") != null
                ? new BigDecimal(body.get("threshold").toString())
                : new BigDecimal("70");
        return plagiarismService.check(homeworkid, resourceAccess.teacherId(session), threshold);
    }

    @GetMapping("/byHomework/{homeworkid}")
    public Result<List<PlagiarismReport>> byHomework(@PathVariable Integer homeworkid, HttpSession session) {
        if (!resourceAccess.canManageHomework(homeworkid, session)) {
            return Result.error(403, "无权查看该作业的查重记录");
        }
        return Result.success(plagiarismService.listByHomework(homeworkid));
    }

    /**
     * AI 原创性 / 代写检测。属于高开销操作，同样只对本课教师开放。
     */
    @PostMapping("/ai-detect")
    public Result<List<Map<String, Object>>> aiDetect(@RequestBody Map<String, Object> body, HttpSession session) {
        Integer homeworkid = toInt(body.get("homeworkid"));
        if (!resourceAccess.canManageHomework(homeworkid, session)) {
            return Result.error(403, "无权对该作业发起检测");
        }
        return plagiarismService.aiDetect(homeworkid);
    }

    @GetMapping("/byTeacher")
    public Result<List<PlagiarismReport>> byTeacher(HttpSession session) {
        Integer teacherid = resourceAccess.teacherId(session);
        if (teacherid == null) {
            return Result.error(401, "请先登录教师账号");
        }
        return Result.success(plagiarismService.listByTeacher(teacherid));
    }

    @GetMapping("/{id}")
    public Result<PlagiarismReport> detail(@PathVariable Integer id, HttpSession session) {
        PlagiarismReport report = plagiarismService.getById(id);
        if (report == null) {
            return Result.error("报告不存在");
        }
        if (!resourceAccess.canManageHomework(report.getHomeworkid(), session)) {
            return Result.error(403, "无权查看该查重报告");
        }
        return Result.success(report);
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
