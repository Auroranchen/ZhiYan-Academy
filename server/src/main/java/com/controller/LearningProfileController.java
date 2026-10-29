package com.controller;

import com.config.RequireRole;
import com.response.Result;
import com.service.LearningProfileService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对话式学习画像控制器。
 */
@RestController
@RequestMapping("/profile")
@RequireRole("student")
public class LearningProfileController {

    @Autowired
    private LearningProfileService profileService;

    /** 获取当前学生画像 */
    @GetMapping
    public Result<Map<String, Object>> get(HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(401, "请先登录学生账号");
        return Result.success(profileService.getProfile(studentid));
    }

    /** 对话式构建/更新画像 */
    @PostMapping("/chat")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> body, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(401, "请先登录学生账号");
        List<Map<String, Object>> history = new ArrayList<>();
        Object h = body.get("history");
        if (h instanceof List) {
            for (Object o : (List<Object>) h) if (o instanceof Map) history.add((Map<String, Object>) o);
        }
        return profileService.chat(studentid, history, (String) body.get("message"));
    }

    /** 重置画像 */
    @PostMapping("/reset")
    public Result<Void> reset(HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(401, "请先登录学生账号");
        return profileService.reset(studentid);
    }

    /** 学习效果评估 → 动态调整 */
    @PostMapping("/evaluate")
    public Result<Map<String, Object>> evaluate(@RequestBody(required = false) Map<String, Object> body, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(401, "请先登录学生账号");
        Integer courseid = null;
        if (body != null && body.get("courseid") != null) {
            Object c = body.get("courseid");
            if (c instanceof Number) courseid = ((Number) c).intValue();
            else try { courseid = Integer.parseInt(c.toString()); } catch (Exception ignored) {}
        }
        return profileService.evaluate(studentid, courseid);
    }

    /** 完成资源包练习后自动评分、更新画像、重排路径并推荐后续资源。 */
    @PostMapping("/resource-evaluate")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> evaluateResource(@RequestBody Map<String, Object> body, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(401, "请先登录学生账号");
        Integer resourceId = toInt(body.get("resourceId"));
        List<Object> answers = body.get("answers") instanceof List
                ? (List<Object>) body.get("answers") : new ArrayList<>();
        return profileService.evaluateResource(studentid, resourceId, answers);
    }

    /** 学习效果评估历史，用于展示掌握度变化。 */
    @GetMapping("/evaluations")
    public Result<List<Map<String, Object>>> evaluations(@RequestParam(required = false) Integer courseid,
                                                          HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(401, "请先登录学生账号");
        return Result.success(profileService.evaluationHistory(studentid, courseid));
    }

    /** 资源包练习作答历史（分页）。 */
    @GetMapping("/practice-attempts")
    public Result<List<Map<String, Object>>> practiceAttempts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(401, "请先登录学生账号");
        Map<String, Object> data = profileService.practiceAttemptHistory(studentid, page, pageSize);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        Long total = data.get("total") instanceof Number n ? n.longValue() : 0L;
        return Result.success(list, total);
    }

    private Integer toInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try { return value == null ? null : Integer.parseInt(value.toString()); }
        catch (Exception e) { return null; }
    }
}
