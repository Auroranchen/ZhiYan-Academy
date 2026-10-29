package com.controller;

import com.config.RequireRole;
import com.model.CourseEvaluation;
import com.response.Result;
import com.service.CourseEvaluationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 课程评价控制器
 */
@RestController
@RequestMapping("/courseEvaluation")
public class CourseEvaluationController {

    @Autowired
    private CourseEvaluationService courseEvaluationService;

    /**
     * 根据课程ID获取评价列表
     */
    @GetMapping("/byCourse/{courseid}")
    public Result<List<CourseEvaluation>> getByCourseid(
            @PathVariable("courseid") Integer courseid,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        List<CourseEvaluation> list = courseEvaluationService.getByCourseid(courseid, page, pageSize);
        Long total = courseEvaluationService.getCountByCourseid(courseid);
        return Result.success(list, total);
    }

    /**
     * 获取评价详情
     */
    @GetMapping("/{id}")
    public Result<CourseEvaluation> getById(@PathVariable("id") Integer id) {
        CourseEvaluation evaluation = courseEvaluationService.getById(id);
        if (evaluation == null) {
            return Result.error("评价不存在");
        }
        return Result.success(evaluation);
    }

    /**
     * 获取当前学生对某课程的评价（studentid 以会话为准）
     */
    @GetMapping("/check")
    @RequireRole("student")
    public Result<CourseEvaluation> getByStudentAndCourse(
            @RequestParam(required = false) Integer studentid,
            @RequestParam Integer courseid,
            HttpSession session) {
        Integer sid = currentStudent(session);
        if (sid == null) return Result.error(401, "请先登录学生账号");
        CourseEvaluation evaluation = courseEvaluationService.getByStudentAndCourse(sid, courseid);
        return Result.success(evaluation);
    }

    /**
     * 获取当前学生的所有评价
     */
    @GetMapping("/byStudent/{studentid}")
    @RequireRole({"student", "teacher", "admin"})
    public Result<List<CourseEvaluation>> getByStudentid(@PathVariable("studentid") Integer studentid,
                                                         HttpSession session) {
        Integer sid = currentStudent(session);
        boolean privileged = session.getAttribute("teacherid") != null
                || session.getAttribute("adminid") != null;
        if (!privileged) {
            if (sid == null) return Result.error(401, "请先登录学生账号");
            if (!sid.equals(studentid)) return Result.error(403, "无权查看他人评价");
        }
        List<CourseEvaluation> list = courseEvaluationService.getByStudentid(studentid);
        return Result.success(list);
    }

    /**
     * 添加评价（studentid 强制取自会话）
     */
    @PostMapping("/add")
    @RequireRole("student")
    public Result<Void> add(@RequestBody CourseEvaluation evaluation, HttpSession session) {
        Integer sid = currentStudent(session);
        if (sid == null) return Result.error(401, "请先登录学生账号");
        evaluation.setStudentid(sid);
        return courseEvaluationService.add(evaluation);
    }

    /**
     * 更新评价（仅本人）
     */
    @PutMapping("/update")
    @RequireRole("student")
    public Result<Void> update(@RequestBody CourseEvaluation evaluation, HttpSession session) {
        Integer sid = currentStudent(session);
        if (sid == null) return Result.error(401, "请先登录学生账号");
        if (evaluation.getId() == null) return Result.error("评价ID不能为空");
        CourseEvaluation existing = courseEvaluationService.getById(evaluation.getId());
        if (existing == null) return Result.error("评价不存在");
        if (!sid.equals(existing.getStudentid())) return Result.error(403, "无权修改他人评价");
        evaluation.setStudentid(sid);
        return courseEvaluationService.update(evaluation);
    }

    /**
     * 删除评价（仅本人）
     */
    @DeleteMapping("/delete/{id}")
    @RequireRole("student")
    public Result<Void> delete(@PathVariable("id") Integer id, HttpSession session) {
        Integer sid = currentStudent(session);
        if (sid == null) return Result.error(401, "请先登录学生账号");
        CourseEvaluation existing = courseEvaluationService.getById(id);
        if (existing == null) return Result.error("评价不存在");
        if (!sid.equals(existing.getStudentid())) return Result.error(403, "无权删除他人评价");
        return courseEvaluationService.delete(id);
    }

    /**
     * 获取课程评价统计
     */
    @GetMapping("/stats/{courseid}")
    public Result<Map<String, Object>> getAverageScores(@PathVariable("courseid") Integer courseid) {
        Map<String, Object> stats = courseEvaluationService.getAverageScores(courseid);
        return Result.success(stats);
    }

    private Integer currentStudent(HttpSession session) {
        Object sid = session != null ? session.getAttribute("studentid") : null;
        return sid instanceof Integer ? (Integer) sid : null;
    }
}
