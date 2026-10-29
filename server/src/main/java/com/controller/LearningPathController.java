package com.controller;

import com.model.LearningPlan;
import com.response.Result;
import com.service.LearningPathService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 学习路径推荐控制器（学生端）
 */
@RestController
@RequestMapping("/learnpath")
public class LearningPathController {

    @Autowired
    private LearningPathService learningPathService;

    @PostMapping("/generate")
    public Result<LearningPlan> generate(HttpSession session) {
        Integer studentid = currentStudent(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        return learningPathService.generate(studentid);
    }

    @GetMapping("/list")
    public Result<List<LearningPlan>> list(HttpSession session) {
        Integer studentid = currentStudent(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(learningPathService.list(studentid));
    }

    @GetMapping("/{id}")
    public Result<LearningPlan> detail(@PathVariable Integer id, HttpSession session) {
        Integer studentid = currentStudent(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        LearningPlan plan = learningPathService.getById(id, studentid);
        return plan != null ? Result.success(plan) : Result.error("计划不存在");
    }

    /** 保存任务完成进度（服务端持久化，跨设备同步） */
    @PostMapping("/progress/{id}")
    public Result<Void> progress(@PathVariable Integer id,
                                 @RequestBody java.util.Map<String, Object> body,
                                 HttpSession session) {
        Integer studentid = currentStudent(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        Object progress = body != null ? body.get("progress") : null;
        String json;
        try {
            json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(progress == null ? java.util.Map.of() : progress);
        } catch (Exception e) {
            return Result.error("进度数据格式错误");
        }
        return learningPathService.updateProgress(studentid, id, json);
    }

    @PostMapping("/archive/{id}")
    public Result<Void> archive(@PathVariable Integer id, HttpSession session) {
        Integer studentid = currentStudent(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        return learningPathService.archive(id, studentid) ? Result.successMsg("已归档") : Result.error("操作失败");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id, HttpSession session) {
        Integer studentid = currentStudent(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        return learningPathService.delete(id, studentid) ? Result.successMsg("已删除") : Result.error("删除失败");
    }

    private Integer currentStudent(HttpSession session) {
        Object sid = session.getAttribute("studentid");
        return sid != null ? (Integer) sid : null;
    }
}
