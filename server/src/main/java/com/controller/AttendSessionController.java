package com.controller;

import com.config.RequireRole;
import com.model.AttendSession;
import com.response.Result;
import com.service.AttendSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/attend-session")
public class AttendSessionController {

    @Autowired
    private AttendSessionService attendSessionService;

    private Integer getTeacherid(HttpSession session) {
        return session == null ? null : (Integer) session.getAttribute("teacherid");
    }

    private Integer getStudentid(HttpSession session) {
        return session == null ? null : (Integer) session.getAttribute("studentid");
    }

    private Integer getClassid(HttpSession session) {
        return session == null ? null : (Integer) session.getAttribute("classid");
    }

    @PostMapping("/create")
    @RequireRole("teacher")
    public Result<AttendSession> create(@RequestBody AttendSession session,
                                        @RequestParam(required = false) Integer teacherid,
                                        HttpSession httpSession) {
        Integer tid = getTeacherid(httpSession);
        if (tid == null) return Result.error("请先登录");
        session.setTeacherid(tid);
        return attendSessionService.create(session);
    }

    @PostMapping("/checkin")
    @RequireRole("student")
    public Result<Map<String, Object>> checkin(@RequestBody Map<String, Object> params,
                                                HttpSession httpSession) {
        Integer studentid = getStudentid(httpSession);
        if (studentid == null) return Result.error("请先登录");
        String code = (String) params.get("code");
        if (code == null || code.trim().isEmpty()) return Result.error("请输入签到码");
        return attendSessionService.checkin(code.trim(), studentid);
    }

    @PostMapping("/close/{id}")
    @RequireRole("teacher")
    public Result<Void> close(@PathVariable Integer id,
                               @RequestParam(required = false) Integer teacherid,
                               HttpSession httpSession) {
        Integer tid = getTeacherid(httpSession);
        if (tid == null) return Result.error("请先登录");
        return attendSessionService.closeSession(id, tid);
    }

    @GetMapping("/teacher/list")
    @RequireRole("teacher")
    public Result<List<AttendSession>> teacherList(@RequestParam(required = false) Integer teacherid,
                                                    HttpSession httpSession) {
        Integer tid = getTeacherid(httpSession);
        if (tid == null) return Result.error("请先登录");
        return Result.success(attendSessionService.getByTeacher(tid));
    }

    @GetMapping("/student/active")
    @RequireRole("student")
    public Result<List<AttendSession>> studentActive(@RequestParam(required = false) Integer classid,
                                                      HttpSession httpSession) {
        Integer cid = getClassid(httpSession);
        if (cid == null) return Result.error("请先登录");
        return Result.success(attendSessionService.getActiveByClass(cid));
    }

    @PostMapping("/proxy-checkin")
    @RequireRole("teacher")
    public Result<Void> proxyCheckin(@RequestBody Map<String, Object> params,
                                      HttpSession httpSession) {
        Integer tid = getTeacherid(httpSession);
        if (tid == null) return Result.error("请先登录");

        Object sidObj = params.get("studentid");
        Integer studentid = sidObj instanceof Number ? ((Number) sidObj).intValue() : null;
        if (studentid == null) return Result.error("缺少学生ID");

        Object sessObj = params.get("sessionId");
        Integer sessionId = sessObj instanceof Number ? ((Number) sessObj).intValue() : null;
        if (sessionId == null) return Result.error("缺少签到ID");

        return attendSessionService.proxyCheckin(sessionId, studentid, tid);
    }

    @GetMapping("/detail/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Integer id, HttpSession httpSession) {
        AttendSession attendSession = attendSessionService.getById(id);
        if (attendSession == null) return Result.error("签到不存在");
        boolean admin = httpSession.getAttribute("adminid") != null;
        Integer teacherId = getTeacherid(httpSession);
        Integer classId = getClassid(httpSession);
        boolean teacherOwns = teacherId != null && teacherId.equals(attendSession.getTeacherid());
        boolean studentInClass = getStudentid(httpSession) != null
                && classId != null && classId.equals(attendSession.getClassid());
        if (!admin && !teacherOwns && !studentInClass) {
            return Result.error(403, "无权查看该签到");
        }
        return attendSessionService.getSessionDetail(id);
    }
}
