package com.controller;

import com.config.RequireRole;
import com.model.Exam;
import com.model.ExamRecord;
import com.response.Result;
import com.service.ExamService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线考试控制器（教师建考 + 学生作答）
 */
@RestController
@RequestMapping("/exam")
public class ExamController {

    @Autowired
    private ExamService examService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---------------- 教师端 ----------------

    @PostMapping("/create")
    @RequireRole("teacher")
    public Result<Exam> create(@RequestBody Exam exam, HttpSession session) {
        Integer teacherid = (Integer) session.getAttribute("teacherid");
        if (teacherid == null) {
            return Result.error(401, "请先登录教师账号");
        }
        exam.setTeacherid(teacherid);
        return examService.create(exam);
    }

    @PutMapping("/update")
    @RequireRole({"teacher", "admin"})
    public Result<Exam> update(@RequestBody Exam exam, HttpSession session) {
        if (exam.getExamid() == null || !canManageExam(exam.getExamid(), session)) {
            return Result.error(403, "无权修改该考试");
        }
        Exam existing = examService.getById(exam.getExamid());
        if (existing != null) exam.setTeacherid(existing.getTeacherid());
        return examService.update(exam);
    }

    @DeleteMapping("/{examid}")
    @RequireRole({"teacher", "admin"})
    public Result<Void> delete(@PathVariable Integer examid, HttpSession session) {
        if (!canManageExam(examid, session)) return Result.error(403, "无权删除该考试");
        return examService.delete(examid);
    }

    @PostMapping("/status/{examid}")
    @RequireRole({"teacher", "admin"})
    public Result<Void> updateStatus(@PathVariable Integer examid, @RequestParam Integer status,
                                     HttpSession session) {
        if (!canManageExam(examid, session)) return Result.error(403, "无权修改该考试");
        return examService.updateStatus(examid, status);
    }

    @GetMapping("/{examid}")
    @RequireRole({"teacher", "admin"})
    public Result<Exam> getById(@PathVariable Integer examid, HttpSession session) {
        if (!canManageExam(examid, session)) return Result.error(403, "无权查看该考试");
        Exam exam = examService.getById(examid);
        return exam != null ? Result.success(exam) : Result.error("考试不存在");
    }

    @GetMapping("/teacher/list")
    @RequireRole("teacher")
    public Result<List<Exam>> teacherList(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {
        Integer teacherid = (Integer) session.getAttribute("teacherid");
        if (teacherid == null) {
            return Result.error(401, "请先登录教师账号");
        }
        List<Exam> list = examService.listByTeacher(teacherid, keyword, page, pageSize);
        Long total = examService.countByTeacher(teacherid, keyword);
        return Result.success(list, total);
    }

    @GetMapping("/results/{examid}")
    public Result<Map<String, Object>> results(@PathVariable Integer examid,
                                               @RequestParam(required = false) String keyword,
                                               HttpSession session) {
        if (!canManageExam(examid, session)) return Result.error(403, "无权查看考试结果");
        List<ExamRecord> records = examService.examResults(examid, keyword);
        Map<String, Object> stats = examService.examStats(examid);
        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("stats", stats);
        data.put("exam", examService.getById(examid));
        return Result.success(data);
    }

    /**
     * 教师评阅主观题：更新总分并保存评语
     */
    @PostMapping("/review/{recordId}")
    public Result<Void> reviewRecord(@PathVariable Integer recordId,
                                     @RequestBody Map<String, Object> body,
                                     HttpSession session) {
        if (!canManageRecord(recordId, session)) return Result.error(403, "无权评阅该答卷");
        Double score = body.get("score") != null ? Double.parseDouble(body.get("score").toString()) : null;
        String comment = body.get("comment") != null ? body.get("comment").toString() : null;
        if (score == null) return Result.error("请填写总分");
        return examService.reviewRecord(recordId, score, comment);
    }

    /**
     * AI 辅助评阅单题（程序题/代码解释题/简答题）
     */
    @PostMapping("/ai-review")
    @RequireRole("teacher")
    public Result<String> aiReview(@RequestBody Map<String, String> params) {
        return examService.aiReviewQuestion(params);
    }

    @GetMapping("/unsubmitted/{examid}")
    public Result<List<Map<String, Object>>> unsubmitted(@PathVariable Integer examid, HttpSession session) {
        if (!canManageExam(examid, session)) return Result.error(403, "无权查看该考试");
        List<Map<String, Object>> list = examService.getUnsubmittedStudents(examid);
        return Result.success(list);
    }

    // ---------------- 学生端 ----------------

    @GetMapping("/student/list")
    @RequireRole("student")
    public Result<List<Exam>> studentList(HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(examService.studentExams(studentid));
    }

    @PostMapping("/start/{examid}")
    @RequireRole("student")
    public Result<Map<String, Object>> start(@PathVariable Integer examid, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        return examService.startExam(examid, studentid);
    }

    @PostMapping("/save/{examid}")
    @RequireRole("student")
    public Result<Void> save(@PathVariable Integer examid,
                             @RequestBody Map<String, Object> body,
                             HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        String answers = body.get("answers") != null ? body.get("answers").toString() : null;
        Integer switchCount = toInt(body.get("switchCount"));
        return examService.saveProgress(examid, studentid, answers, switchCount);
    }

    @PostMapping("/submit/{examid}")
    @RequireRole("student")
    public Result<ExamRecord> submit(@PathVariable Integer examid,
                                     @RequestBody Map<String, Object> body,
                                     HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        String answers = body.get("answers") != null ? body.get("answers").toString() : null;
        Integer switchCount = toInt(body.get("switchCount"));
        Integer usedSeconds = toInt(body.get("usedSeconds"));
        Integer autoSubmit = toInt(body.get("autoSubmit"));
        return examService.submitExam(examid, studentid, answers, switchCount, usedSeconds, autoSubmit);
    }

    @GetMapping("/record/{examid}")
    @RequireRole("student")
    public Result<ExamRecord> record(@PathVariable Integer examid, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        ExamRecord record = examService.getRecord(examid, studentid);
        return record != null ? Result.success(record) : Result.error("暂无作答记录");
    }

    /**
     * 学生回顾已交卷考试：返回试卷内容 + 作答记录（仅本人已交卷可见）
     */
    @GetMapping("/student/review/{examid}")
    @RequireRole("student")
    public Result<Map<String, Object>> studentReview(@PathVariable Integer examid, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        ExamRecord record = examService.getRecord(examid, studentid);
        if (record == null || record.getStatus() == null || record.getStatus() < 1) {
            return Result.error("仅可回顾已交卷的考试");
        }
        Exam exam = examService.getById(examid);
        if (exam == null) {
            return Result.error("考试不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("exam", exam);
        data.put("record", record);
        return Result.success(data);
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

    private boolean canManageExam(Integer examId, HttpSession session) {
        if (session == null || examId == null) return false;
        if (session.getAttribute("adminid") != null) return true;
        Integer teacherId = (Integer) session.getAttribute("teacherid");
        if (teacherId == null) return false;
        Exam exam = examService.getById(examId);
        return exam != null && teacherId.equals(exam.getTeacherid());
    }

    private boolean canManageRecord(Integer recordId, HttpSession session) {
        if (session == null || recordId == null) return false;
        if (session.getAttribute("adminid") != null) return true;
        Integer teacherId = (Integer) session.getAttribute("teacherid");
        if (teacherId == null) return false;
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM exam_record r JOIN exam e ON e.examid=r.examid "
                            + "WHERE r.id=? AND e.teacherid=?",
                    Integer.class, recordId, teacherId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
