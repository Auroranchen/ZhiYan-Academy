package com.service;

import com.mapper.AttendSessionMapper;
import com.mapper.StudentAttendanceMapper;
import com.mapper.StudentMapper;
import com.model.AttendSession;
import com.model.Student;
import com.model.StudentAttendance;
import com.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AttendSessionService {

    @Autowired
    private AttendSessionMapper attendSessionMapper;

    @Autowired
    private StudentAttendanceMapper studentAttendanceMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private MessageService messageService;

    @Autowired
    private com.config.CheckinWebSocketHandler checkinWsHandler;

    /**
     * 教师发起签到
     */
    public Result<AttendSession> create(AttendSession session) {
        if (session.getCourseid() == null || session.getClassid() == null || session.getTeacherid() == null) {
            return Result.error("参数不完整");
        }
        if (session.getDuration() == null || session.getDuration() < 1) {
            session.setDuration(10);
        }
        if (session.getLateMinutes() == null || session.getLateMinutes() < 0) {
            session.setLateMinutes(5);
        }
        if (session.getType() == null) {
            session.setType(1);
        }

        if (session.getType() == 1
                && (session.getCheckinCode() == null || session.getCheckinCode().isEmpty())) {
            session.setCheckinCode(generateCode());
        }
        if (session.getType() == 2) {
            session.setCheckinCode("AUTO-" + System.currentTimeMillis() % 100000);
        }
        if (session.getType() == 3) {
            // 手势签到：checkinCode 为九宫格点序列（如 "0481"），至少 4 个点
            if (session.getCheckinCode() == null || session.getCheckinCode().length() < 4) {
                return Result.error("请设置至少连接 4 个点的签到手势");
            }
        }

        Date now = new Date();
        session.setStartTime(now);
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.MINUTE, session.getDuration());
        session.setEndTime(cal.getTime());
        session.setStatus(1);
        session.setCreateTime(now);

        attendSessionMapper.insert(session);
        AttendSession created = attendSessionMapper.findById(session.getId());

        // 给班级学生异步推送签到提醒（不阻塞发起流程）
        String wayText = checkinRemark(created.getType());
        String title = "课堂签到提醒";
        String content = "老师发起了《" + (created.getCoursename() != null ? created.getCoursename() : "课程")
                + "》的签到（" + wayText + "），请在 " + created.getDuration()
                + " 分钟内完成签到。";
        messageService.sendToClassAsync(title, content, 2, created.getTeacherid(), created.getClassid());

        // 通过 WebSocket 实时推送"签到已发起"事件
        checkinWsHandler.broadcast(created.getClassid(), "session_created", created.getId(),
                created.getCoursename());

        return Result.success(created);
    }

    private String generateCode() {
        Random random = new Random();
        // 避免与当前进行中的签到码冲突，最多重试 20 次
        for (int i = 0; i < 20; i++) {
            String code = String.valueOf(1000 + random.nextInt(9000));
            if (attendSessionMapper.findByCode(code, 1) == null) {
                return code;
            }
        }
        // 兜底：附加时间戳后缀确保唯一
        return String.valueOf(1000 + random.nextInt(9000)) + (System.currentTimeMillis() % 100);
    }

    private String checkinRemark(Integer type) {
        if (type == null) return "课堂签到";
        switch (type) {
            case 2: return "一键签到";
            case 3: return "手势签到";
            default: return "签到码签到";
        }
    }

    /**
     * 学生签到
     */
    @Transactional
    public Result<Map<String, Object>> checkin(String code, Integer studentid) {
        attendSessionMapper.closeExpired();

        Student student = studentMapper.findById(studentid);
        if (student == null) {
            return Result.error("学生不存在");
        }
        if (student.getClassid() == null) {
            return Result.error("您尚未分配班级，无法签到");
        }

        // 按班级精确匹配活跃签到，避免不同班级签到码相同时匹配错误
        AttendSession session = attendSessionMapper.findActiveByCodeAndClass(code, student.getClassid());
        if (session == null) {
            return Result.error("签到码无效或签到已结束");
        }

        Date now = new Date();
        if (now.after(session.getEndTime())) {
            return Result.error("签到已超时");
        }

        java.sql.Date attendDate = java.sql.Date.valueOf(
                new java.text.SimpleDateFormat("yyyy-MM-dd").format(session.getStartTime()));
        StudentAttendance existing = studentAttendanceMapper.findByUnique(
                session.getCourseid(), session.getClassid(), studentid, attendDate);
        // 已签到（属于本次签到且非缺勤）则不允许重复
        if (existing != null && existing.getSessionId() != null
                && existing.getSessionId().equals(session.getId())
                && existing.getStatus() != null && existing.getStatus() != 4) {
            return Result.error("您已签到，请勿重复操作");
        }

        long diffMs = now.getTime() - session.getStartTime().getTime();
        long diffMinutes = diffMs / (1000 * 60);
        int status = diffMinutes <= session.getLateMinutes() ? 1 : 2; // 1=出勤, 2=迟到
        String remark = checkinRemark(session.getType());

        if (existing != null) {
            existing.setStatus(status);
            existing.setRemark(remark);
            existing.setSessionId(session.getId());
            existing.setCreatetime(now);
            studentAttendanceMapper.updateCheckin(existing);
        } else {
            StudentAttendance attendance = new StudentAttendance();
            attendance.setCourseid(session.getCourseid());
            attendance.setClassid(session.getClassid());
            attendance.setStudentid(studentid);
            attendance.setAttenddate(attendDate);
            attendance.setStatus(status);
            attendance.setRemark(remark);
            attendance.setTeacherid(session.getTeacherid());
            attendance.setCreatetime(now);
            attendance.setSessionId(session.getId());
            studentAttendanceMapper.insert(attendance);
        }

        // 实时通知教师端刷新名单
        checkinWsHandler.broadcast(session.getClassid(), "checkin", session.getId(), session.getCoursename());

        Map<String, Object> result = new HashMap<>();
        result.put("status", status);
        result.put("statusText", status == 1 ? "签到成功" : "签到成功(迟到)");
        result.put("coursename", session.getCoursename());
        result.put("time", now);
        return Result.success(result);
    }

    /**
     * 教师手动关闭签到，并将未签到学生标记为缺勤
     */
    @Transactional
    public Result<Void> closeSession(Integer sessionId, Integer teacherid) {
        AttendSession session = attendSessionMapper.findById(sessionId);
        if (session == null) return Result.error("签到不存在");
        if (!session.getTeacherid().equals(teacherid)) return Result.error("无权操作");

        attendSessionMapper.updateStatus(sessionId, 2);
        markAbsentStudents(session);
        checkinWsHandler.broadcast(session.getClassid(), "closed", sessionId, session.getCoursename());
        return Result.success();
    }

    /**
     * 将未签到学生标记为缺勤
     */
    private void markAbsentStudents(AttendSession session) {
        List<Student> students = studentMapper.findByClassid(session.getClassid());
        java.sql.Date attendDate = java.sql.Date.valueOf(
                new java.text.SimpleDateFormat("yyyy-MM-dd").format(session.getStartTime()));

        // 一次查出已有考勤记录，避免逐个学生查询的 N+1
        Set<Integer> existingIds = new HashSet<>();
        for (StudentAttendance att : studentAttendanceMapper.findByCourseClassDate(
                session.getCourseid(), session.getClassid(), attendDate)) {
            existingIds.add(att.getStudentid());
        }

        Date now = new Date();
        List<StudentAttendance> absences = new ArrayList<>();
        for (Student s : students) {
            if (!existingIds.contains(s.getStudentid())) {
                StudentAttendance absence = new StudentAttendance();
                absence.setCourseid(session.getCourseid());
                absence.setClassid(session.getClassid());
                absence.setStudentid(s.getStudentid());
                absence.setAttenddate(attendDate);
                absence.setStatus(4); // 缺勤
                absence.setRemark("未签到");
                absence.setTeacherid(session.getTeacherid());
                absence.setCreatetime(now);
                absence.setSessionId(session.getId());
                absences.add(absence);
            }
        }
        // 一次批量插入，避免逐条 insert
        if (!absences.isEmpty()) {
            studentAttendanceMapper.batchInsert(absences);
        }
    }

    public List<AttendSession> getByTeacher(Integer teacherid) {
        attendSessionMapper.closeExpired();
        return attendSessionMapper.findByTeacher(teacherid);
    }

    public List<AttendSession> getActiveByClass(Integer classid) {
        attendSessionMapper.closeExpired();
        List<AttendSession> list = attendSessionMapper.findActiveByClass(classid);
        if (list == null) return List.of();
        // 手势签到码对学生端不可见，避免泄露图案
        for (AttendSession s : list) {
            if (s.getType() != null && s.getType() == 3) {
                s.setCheckinCode(null);
            }
        }
        return list;
    }

    public AttendSession getById(Integer id) {
        return attendSessionMapper.findById(id);
    }

    /**
     * 教师代签（帮学生签到）
     */
    @Transactional
    public Result<Void> proxyCheckin(Integer sessionId, Integer studentid, Integer teacherid) {
        AttendSession session = attendSessionMapper.findById(sessionId);
        if (session == null) return Result.error("签到不存在");
        if (!session.getTeacherid().equals(teacherid)) return Result.error("无权操作");

        Student student = studentMapper.findById(studentid);
        if (student == null) return Result.error("学生不存在");

        java.sql.Date attendDate = java.sql.Date.valueOf(
                new java.text.SimpleDateFormat("yyyy-MM-dd").format(session.getStartTime()));

        StudentAttendance existing = studentAttendanceMapper.findByUnique(
                session.getCourseid(), session.getClassid(), studentid, attendDate);

        if (existing != null && existing.getSessionId() != null
                && existing.getSessionId().equals(sessionId)
                && existing.getStatus() != null && existing.getStatus() != 4) {
            return Result.error("该学生已签到");
        }

        Date now = new Date();
        if (existing != null) {
            existing.setStatus(1);
            existing.setRemark("教师代签");
            existing.setSessionId(session.getId());
            existing.setCreatetime(now);
            studentAttendanceMapper.updateCheckin(existing);
        } else {
            StudentAttendance attendance = new StudentAttendance();
            attendance.setCourseid(session.getCourseid());
            attendance.setClassid(session.getClassid());
            attendance.setStudentid(studentid);
            attendance.setAttenddate(attendDate);
            attendance.setStatus(1);
            attendance.setRemark("教师代签");
            attendance.setTeacherid(session.getTeacherid());
            attendance.setCreatetime(now);
            attendance.setSessionId(session.getId());
            studentAttendanceMapper.insert(attendance);
        }

        checkinWsHandler.broadcast(session.getClassid(), "checkin", session.getId(), session.getCoursename());
        return Result.success();
    }

    /**
     * 获取签到详情（哪些学生已签到、未签到）
     */
    public Result<Map<String, Object>> getSessionDetail(Integer sessionId) {
        AttendSession session = attendSessionMapper.findById(sessionId);
        if (session == null) return Result.error("签到不存在");

        List<Student> allStudents = studentMapper.findByClassid(session.getClassid());
        java.sql.Date attendDate = java.sql.Date.valueOf(
                new java.text.SimpleDateFormat("yyyy-MM-dd").format(session.getStartTime()));

        // 一次查出全班考勤，内存内按学生分组，避免 N+1（轮询场景下尤其重要）
        Map<Integer, StudentAttendance> attMap = new HashMap<>();
        for (StudentAttendance att : studentAttendanceMapper.findByCourseClassDate(
                session.getCourseid(), session.getClassid(), attendDate)) {
            attMap.put(att.getStudentid(), att);
        }

        List<Map<String, Object>> checkedList = new ArrayList<>();
        List<Map<String, Object>> uncheckedList = new ArrayList<>();

        for (Student s : allStudents) {
            StudentAttendance att = attMap.get(s.getStudentid());
            Map<String, Object> info = new HashMap<>();
            info.put("studentid", s.getStudentid());
            info.put("studentno", s.getStudentno());
            info.put("studentname", s.getStudentname());
            info.put("avatar", s.getAvatar());

            boolean isChecked = att != null && att.getSessionId() != null
                    && att.getSessionId().equals(sessionId)
                    && att.getStatus() != null && att.getStatus() != 4;
            if (isChecked) {
                info.put("status", att.getStatus());
                info.put("time", att.getCreatetime());
                info.put("proxy", "教师代签".equals(att.getRemark()));
                checkedList.add(info);
            } else {
                uncheckedList.add(info);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("session", session);
        data.put("checkedList", checkedList);
        data.put("uncheckedList", uncheckedList);
        return Result.success(data);
    }
}
