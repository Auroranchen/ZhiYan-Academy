package com.controller;

import com.config.RequireRole;
import com.response.Result;
import com.service.AiInsightService;
import com.service.ResourceAccessService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 学情洞察控制器。
 * 提供：学生学习周报、教师班级学情周报、个性化智能练习、知识点思维导图。
 */
@RestController
@RequestMapping("/ai-insight")
public class AiInsightController {

    @Autowired
    private AiInsightService aiInsightService;

    @Autowired
    private ResourceAccessService resourceAccess;

    // ============== 学生学习周报 ==============

    /** 获取已缓存的学生周报（不消耗AI） */
    @GetMapping("/student-report/cached")
    public Result<Map<String, Object>> cachedStudentReport(HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(401, "请先登录");
        return Result.success(aiInsightService.getCachedStudentReport(studentid));
    }

    /** 学生有学习记录的课程列表（按课程生成周报用） */
    @GetMapping("/student-courses")
    public Result<java.util.List<Map<String, Object>>> studentCourses(HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(401, "请先登录");
        return Result.success(aiInsightService.studentCourses(studentid));
    }

    /** 生成（刷新）学生学习周报，可选按课程聚焦 */
    @PostMapping("/student-report")
    public Result<Map<String, Object>> studentReport(@RequestBody(required = false) Map<String, Object> body, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(401, "请先登录");
        Integer courseid = body != null ? toInt(body.get("courseid")) : null;
        return aiInsightService.studentReport(studentid, courseid);
    }

    // ============== 教师班级学情周报 ==============

    // 班级周报含学生姓名与成绩偏低名单，此前只校验「是教师」，
    // 任课教师之间可以互相查看对方课程的报告，这里补上课程归属校验。

    /** 获取已缓存的班级周报 */
    @GetMapping("/class-report/cached")
    @RequireRole({"teacher", "admin"})
    public Result<Map<String, Object>> cachedClassReport(@RequestParam Integer courseid, HttpSession session) {
        if (!resourceAccess.canManageCourse(courseid, session)) {
            return Result.error(403, "无权查看该课程的学情周报");
        }
        return Result.success(aiInsightService.getCachedClassReport(courseid));
    }

    /** 生成（刷新）班级学情周报 */
    @PostMapping("/class-report")
    @RequireRole({"teacher", "admin"})
    public Result<Map<String, Object>> classReport(@RequestBody Map<String, Object> body, HttpSession session) {
        Integer courseid = toInt(body.get("courseid"));
        if (!resourceAccess.canManageCourse(courseid, session)) {
            return Result.error(403, "无权生成该课程的学情周报");
        }
        return aiInsightService.classReport(courseid);
    }

    // ============== 个性化智能练习 ==============

    /** 学生有错题的课程列表（练习按课程筛选） */
    @GetMapping("/practice-courses")
    public Result<java.util.List<Map<String, Object>>> practiceCourses(HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(401, "请先登录");
        return Result.success(aiInsightService.practiceCourses(studentid));
    }

    /** 基于错题生成智能练习题 */
    @PostMapping("/smart-practice")
    public Result<Map<String, Object>> smartPractice(@RequestBody Map<String, Object> body, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(401, "请先登录");
        Integer courseid = toInt(body.get("courseid"));
        Integer count = toInt(body.get("count"));
        return aiInsightService.smartPractice(studentid, courseid, count == null ? 5 : count);
    }

    /** 完成练习后发放积分，并将答对的变式题对应错题归档（标记已掌握） */
    @PostMapping("/practice-done")
    public Result<Map<String, Object>> practiceDone(@RequestBody Map<String, Object> body, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(401, "请先登录");
        Integer correct = toInt(body.get("correct"));
        Integer total = toInt(body.get("total"));
        aiInsightService.rewardPractice(studentid, correct == null ? 0 : correct, total == null ? 0 : total);

        java.util.List<Integer> masteredIds = new java.util.ArrayList<>();
        Object raw = body.get("masteredWrongIds");
        if (raw instanceof java.util.List) {
            for (Object o : (java.util.List<?>) raw) {
                Integer id = toInt(o);
                if (id != null) masteredIds.add(id);
            }
        }
        int archived = aiInsightService.markPracticeMastered(studentid, masteredIds);
        Map<String, Object> result = new HashMap<>();
        result.put("archived", archived);
        return Result.success(result);
    }

    // ============== 知识点思维导图 ==============

    /** 生成知识点思维导图（学生、教师均可用） */
    @PostMapping("/mindmap")
    public Result<Map<String, Object>> mindmap(@RequestBody Map<String, Object> body, HttpSession session) {
        boolean logged = session.getAttribute("studentid") != null || session.getAttribute("teacherid") != null;
        if (!logged) return Result.error(401, "请先登录");
        String topic = body.get("topic") != null ? body.get("topic").toString() : null;
        return aiInsightService.mindmap(topic);
    }

    /** 思维导图节点的二级 AI 详解 */
    @PostMapping("/mindmap-detail")
    public Result<Map<String, Object>> mindmapDetail(@RequestBody Map<String, Object> body, HttpSession session) {
        boolean logged = session.getAttribute("studentid") != null || session.getAttribute("teacherid") != null;
        if (!logged) return Result.error(401, "请先登录");
        String topic = body.get("topic") != null ? body.get("topic").toString() : null;
        String node = body.get("node") != null ? body.get("node").toString() : null;
        String path = body.get("path") != null ? body.get("path").toString() : null;
        return aiInsightService.mindmapNodeDetail(topic, node, path);
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
