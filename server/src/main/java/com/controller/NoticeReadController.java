package com.controller;

import com.config.RequireRole;
import com.model.NoticeRead;
import com.response.Result;
import com.service.NoticeReadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.Map;

/**
 * 阅读状态控制器
 */
@RestController
@RequestMapping("/noticeRead")
public class NoticeReadController {

    @Autowired
    private NoticeReadService noticeReadService;

    /**
     * 标记公告已读（学生）
     */
    @PostMapping("/notice/student")
    @RequireRole("student")
    public Result<Void> markNoticeReadByStudent(@RequestBody Map<String, Integer> params, HttpSession session) {
        Integer noticeid = params.get("noticeid");
        Integer studentid = (Integer) session.getAttribute("studentid");
        return noticeReadService.markNoticeReadByStudent(noticeid, studentid);
    }

    /**
     * 标记公告已读（教师）
     */
    @PostMapping("/notice/teacher")
    @RequireRole("teacher")
    public Result<Void> markNoticeReadByTeacher(@RequestBody Map<String, Integer> params, HttpSession session) {
        Integer noticeid = params.get("noticeid");
        Integer teacherid = (Integer) session.getAttribute("teacherid");
        return noticeReadService.markNoticeReadByTeacher(noticeid, teacherid);
    }

    /**
     * 标记作业已读
     */
    @PostMapping("/homework")
    @RequireRole("student")
    public Result<Void> markHomeworkRead(@RequestBody Map<String, Integer> params, HttpSession session) {
        Integer homeworkid = params.get("homeworkid");
        Integer studentid = (Integer) session.getAttribute("studentid");
        return noticeReadService.markHomeworkRead(homeworkid, studentid);
    }

    /**
     * 检查公告是否已读
     */
    @GetMapping("/notice/check")
    public Result<Boolean> isNoticeRead(
            @RequestParam Integer noticeid,
            @RequestParam(required = false) Integer studentid,
            @RequestParam(required = false) Integer teacherid,
            HttpSession session) {
        studentid = (Integer) session.getAttribute("studentid");
        teacherid = (Integer) session.getAttribute("teacherid");
        boolean isRead = noticeReadService.isNoticeRead(noticeid, studentid, teacherid);
        return Result.success(isRead);
    }

    /**
     * 当前学生已读公告 ID 列表
     */
    @GetMapping("/notice/readIds")
    @RequireRole("student")
    public Result<List<Integer>> myReadNoticeIds(HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(401, "请先登录学生账号");
        return Result.success(noticeReadService.listNoticeIdsByStudent(studentid));
    }

    /**
     * 检查作业是否已读
     */
    @GetMapping("/homework/check")
    public Result<Boolean> isHomeworkRead(
            @RequestParam Integer homeworkid,
            @RequestParam Integer studentid,
            HttpSession session) {
        studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(403, "仅学生可查询作业阅读状态");
        boolean isRead = noticeReadService.isHomeworkRead(homeworkid, studentid);
        return Result.success(isRead);
    }

    /**
     * 获取公告阅读统计
     */
    @GetMapping("/notice/stats/{noticeid}")
    @RequireRole({"admin", "teacher"})
    public Result<Map<String, Object>> getNoticeReadStats(@PathVariable("noticeid") Integer noticeid) {
        Map<String, Object> stats = noticeReadService.getNoticeReadStats(noticeid);
        return Result.success(stats);
    }

    /**
     * 获取作业阅读统计
     */
    @GetMapping("/homework/stats/{homeworkid}")
    @RequireRole({"admin", "teacher"})
    public Result<Map<String, Object>> getHomeworkReadStats(@PathVariable("homeworkid") Integer homeworkid) {
        Map<String, Object> stats = noticeReadService.getHomeworkReadStats(homeworkid);
        return Result.success(stats);
    }

    /**
     * 获取未读公告的学生列表
     */
    @GetMapping("/notice/unread/{noticeid}")
    @RequireRole({"admin", "teacher"})
    public Result<List<NoticeRead>> getUnreadStudents(
            @PathVariable("noticeid") Integer noticeid,
            @RequestParam(required = false) Integer classid) {
        List<NoticeRead> list = noticeReadService.getUnreadStudents(noticeid, classid);
        return Result.success(list);
    }

    /**
     * 获取未读作业的学生列表
     */
    @GetMapping("/homework/unread/{homeworkid}")
    @RequireRole({"admin", "teacher"})
    public Result<List<NoticeRead>> getUnreadHomeworkStudents(
            @PathVariable("homeworkid") Integer homeworkid,
            @RequestParam(required = false) Integer classid) {
        List<NoticeRead> list = noticeReadService.getUnreadHomeworkStudents(homeworkid, classid);
        return Result.success(list);
    }

    /**
     * 获取作业所有学生的阅读状态
     */
    @GetMapping("/homework/allStatus/{homeworkid}")
    @RequireRole({"admin", "teacher"})
    public Result<List<Map<String, Object>>> getAllHomeworkReadStatus(
            @PathVariable("homeworkid") Integer homeworkid,
            @RequestParam(required = false) Integer classid) {
        List<Map<String, Object>> list = noticeReadService.getAllHomeworkReadStatus(homeworkid, classid);
        return Result.success(list);
    }
}
