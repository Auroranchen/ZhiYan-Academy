package com.controller;

import com.config.RequireRole;
import com.model.StudentAttendance;
import com.response.Result;
import com.service.StudentAttendanceService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/attendance")
public class StudentAttendanceController {

    @Autowired
    private StudentAttendanceService studentAttendanceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/list")
    @RequireRole({"teacher", "admin"})
    public Result<List<StudentAttendance>> list(
            @RequestParam(required = false) Integer courseid,
            @RequestParam(required = false) Integer classid,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date attenddate,
            @RequestParam(required = false) Integer courseTeacherId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {
        Integer sessionTeacherId = (Integer) session.getAttribute("teacherid");
        if (sessionTeacherId != null) courseTeacherId = sessionTeacherId;
        List<StudentAttendance> list = studentAttendanceService.getList(
                courseid, classid, attenddate, courseTeacherId, page, pageSize);
        Long total = studentAttendanceService.getCount(courseid, classid, attenddate, courseTeacherId);
        return Result.success(list, total);
    }

    @GetMapping("/{id}")
    public Result<StudentAttendance> getById(@PathVariable("id") Integer id, HttpSession session) {
        StudentAttendance attendance = studentAttendanceService.getById(id);
        if (attendance == null) return Result.error("考勤记录不存在");
        if (!canView(attendance, session)) return Result.error(403, "无权查看该考勤记录");
        return Result.success(attendance);
    }

    @GetMapping("/byStudent/{studentid}")
    @RequireRole({"student", "teacher", "admin"})
    public Result<List<StudentAttendance>> getByStudentid(
            @PathVariable("studentid") Integer studentid,
            @RequestParam(required = false) Integer courseid,
            HttpSession session) {
        Integer sessionStudentId = (Integer) session.getAttribute("studentid");
        Integer teacherId = (Integer) session.getAttribute("teacherid");
        boolean admin = session.getAttribute("adminid") != null;
        if (sessionStudentId != null) {
            if (!sessionStudentId.equals(studentid)) {
                return Result.error(403, "无权查看其他学生的考勤");
            }
            return Result.success(studentAttendanceService.getByStudentid(studentid, courseid));
        }
        if (teacherId != null) {
            if (courseid == null || !teacherOwnsCourse(courseid, teacherId)) {
                return Result.error(403, "无权查看该课程考勤");
            }
            return Result.success(studentAttendanceService.getByStudentid(studentid, courseid));
        }
        if (admin) {
            return Result.success(studentAttendanceService.getByStudentid(studentid, courseid));
        }
        return Result.error(401, "请先登录");
    }

    @PostMapping("/add")
    @RequireRole({"teacher", "admin"})
    public Result<Void> add(@RequestBody StudentAttendance attendance, HttpSession session) {
        Integer teacherId = (Integer) session.getAttribute("teacherid");
        if (teacherId != null) {
            if (!teacherOwnsCourse(attendance.getCourseid(), teacherId)) {
                return Result.error(403, "无权维护该课程考勤");
            }
            attendance.setTeacherid(teacherId);
        }
        return studentAttendanceService.add(attendance);
    }

    @PostMapping("/batchAdd")
    @RequireRole({"teacher", "admin"})
    public Result<Void> batchAdd(@RequestBody Map<String, Object> params, HttpSession session) {
        Integer courseid = toInteger(params.get("courseid"));
        Integer classid = toInteger(params.get("classid"));
        Integer teacherid = (Integer) session.getAttribute("teacherid");
        if (teacherid != null && !teacherOwnsCourse(courseid, teacherid)) {
            return Result.error(403, "无权维护该课程考勤");
        }
        if (teacherid == null) teacherid = toInteger(params.get("teacherid"));

        Date attenddate = null;
        Object rawDate = params.get("attenddate");
        if (rawDate != null && !String.valueOf(rawDate).isBlank()) {
            try {
                attenddate = java.sql.Date.valueOf(String.valueOf(rawDate));
            } catch (IllegalArgumentException e) {
                return Result.error("日期格式错误");
            }
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) params.get("records");
        return studentAttendanceService.batchAdd(courseid, classid, attenddate, teacherid, records);
    }

    @PutMapping("/update")
    @RequireRole({"teacher", "admin"})
    public Result<Void> update(@RequestBody StudentAttendance attendance, HttpSession session) {
        StudentAttendance existing = studentAttendanceService.getById(attendance.getId());
        if (existing == null) return Result.error("考勤记录不存在");
        if (!canManage(existing, session)) return Result.error(403, "无权修改该考勤记录");
        return studentAttendanceService.update(attendance);
    }

    @DeleteMapping("/delete/{id}")
    @RequireRole({"teacher", "admin"})
    public Result<Void> delete(@PathVariable("id") Integer id, HttpSession session) {
        StudentAttendance existing = studentAttendanceService.getById(id);
        if (existing == null) return Result.error("考勤记录不存在");
        if (!canManage(existing, session)) return Result.error(403, "无权删除该考勤记录");
        return studentAttendanceService.delete(id);
    }

    @GetMapping("/statistics")
    @RequireRole({"teacher", "admin"})
    public Result<Map<String, Long>> getStatistics(
            @RequestParam(required = false) Integer courseid,
            @RequestParam(required = false) Integer classid,
            HttpSession session) {
        Integer teacherId = (Integer) session.getAttribute("teacherid");
        if (teacherId != null && (courseid == null || !teacherOwnsCourse(courseid, teacherId))) {
            return Result.error(403, "无权查看该课程考勤统计");
        }
        return Result.success(studentAttendanceService.getStatistics(courseid, classid));
    }

    private boolean canView(StudentAttendance attendance, HttpSession session) {
        if (attendance == null || session == null) return false;
        if (session.getAttribute("adminid") != null) return true;
        Integer studentId = (Integer) session.getAttribute("studentid");
        if (studentId != null) return studentId.equals(attendance.getStudentid());
        return canManage(attendance, session);
    }

    private boolean canManage(StudentAttendance attendance, HttpSession session) {
        if (attendance == null || session == null) return false;
        if (session.getAttribute("adminid") != null) return true;
        Integer teacherId = (Integer) session.getAttribute("teacherid");
        return teacherId != null && teacherOwnsCourse(attendance.getCourseid(), teacherId);
    }

    private boolean teacherOwnsCourse(Integer courseId, Integer teacherId) {
        if (courseId == null || teacherId == null) return false;
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM course WHERE courseid=? AND teacherid=?",
                    Integer.class, courseId, teacherId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return null;
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
