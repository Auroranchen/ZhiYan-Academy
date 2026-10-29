package com.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 资源归属校验。
 *
 * 「已登录」由 SessionAuthInterceptor 保证，「角色对不对」由 @RequireRole 保证，
 * 但这两层都回答不了「这个人能不能碰这一条数据」。该判断此前散落在各 Controller 的
 * 私有方法里，写法不一致，还有多个接口干脆漏掉了，导致任意登录用户可读他人数据。
 *
 * 统一约定：
 *   canManageXxx —— 能否修改/删除，仅管理员与该资源所属教师
 *   canViewXxx   —— 能否查看，在管理权限之上再放开相关学生
 * 所有方法对 null 入参与查询异常一律返回 false，失败时拒绝而不是放行。
 */
@Service
public class ResourceAccessService {

    private final JdbcTemplate jdbcTemplate;

    public ResourceAccessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /* ==================== 会话身份 ==================== */

    public boolean isAdmin(HttpSession session) {
        return session != null && session.getAttribute("adminid") != null;
    }

    public Integer teacherId(HttpSession session) {
        return session == null ? null : (Integer) session.getAttribute("teacherid");
    }

    public Integer studentId(HttpSession session) {
        return session == null ? null : (Integer) session.getAttribute("studentid");
    }

    /* ==================== 课程 ==================== */

    /** 管理员，或该课程的任课教师 */
    public boolean canManageCourse(Integer courseId, HttpSession session) {
        if (courseId == null) return false;
        if (isAdmin(session)) return true;

        Integer teacherId = teacherId(session);
        if (teacherId == null) return false;
        return exists("SELECT COUNT(*) FROM course WHERE courseid=? AND teacherid=?", courseId, teacherId);
    }

    /** 在管理权限之外，再放开该课程所授班级的学生 */
    public boolean canViewCourse(Integer courseId, HttpSession session) {
        if (canManageCourse(courseId, session)) return true;

        Integer studentId = studentId(session);
        if (studentId == null || courseId == null) return false;
        return exists(
                "SELECT COUNT(*) FROM courseclass cc "
                        + "JOIN student s ON s.classid = cc.classid "
                        + "WHERE cc.courseid=? AND s.studentid=?",
                courseId, studentId);
    }

    /* ==================== 作业 ==================== */

    /** 管理员，或作业所属课程的任课教师 */
    public boolean canManageHomework(Integer homeworkId, HttpSession session) {
        if (homeworkId == null) return false;
        if (isAdmin(session)) return true;

        Integer teacherId = teacherId(session);
        if (teacherId == null) return false;
        return exists(
                "SELECT COUNT(*) FROM homework h JOIN course c ON c.courseid = h.courseid "
                        + "WHERE h.homeworkid=? AND c.teacherid=?",
                homeworkId, teacherId);
    }

    /** 在管理权限之外，再放开被布置到该作业的班级学生 */
    public boolean canViewHomework(Integer homeworkId, HttpSession session) {
        if (canManageHomework(homeworkId, session)) return true;

        Integer studentId = studentId(session);
        if (studentId == null || homeworkId == null) return false;
        return exists(
                "SELECT COUNT(*) FROM homework h "
                        + "JOIN courseclass cc ON cc.courseid = h.courseid "
                        + "JOIN student s ON s.classid = cc.classid "
                        + "WHERE h.homeworkid=? AND s.studentid=?",
                homeworkId, studentId);
    }

    /* ==================== 作业提交 ==================== */

    /** 管理员，或该提交所属课程的任课教师 */
    public boolean canManageSubmission(Integer submissionId, HttpSession session) {
        if (submissionId == null) return false;
        if (isAdmin(session)) return true;

        Integer teacherId = teacherId(session);
        if (teacherId == null) return false;
        return exists(
                "SELECT COUNT(*) FROM studenthomework sh "
                        + "JOIN homework h ON h.homeworkid = sh.homeworkid "
                        + "JOIN course c ON c.courseid = h.courseid "
                        + "WHERE sh.id=? AND c.teacherid=?",
                submissionId, teacherId);
    }

    /** 在管理权限之外，放开提交者本人，以及被分配到该提交的互评审阅人 */
    public boolean canViewSubmission(Integer submissionId, HttpSession session) {
        if (canManageSubmission(submissionId, session)) return true;

        Integer studentId = studentId(session);
        if (studentId == null || submissionId == null) return false;

        if (exists("SELECT COUNT(*) FROM studenthomework WHERE id=? AND studentid=?", submissionId, studentId)) {
            return true;
        }
        return exists("SELECT COUNT(*) FROM peer_review WHERE submitid=? AND reviewerid=?", submissionId, studentId);
    }

    /** 提交者本人（用于重试 AI 评分这类只该由本人或教师触发的动作） */
    public boolean isSubmissionOwner(Integer submissionId, HttpSession session) {
        Integer studentId = studentId(session);
        if (studentId == null || submissionId == null) return false;
        return exists("SELECT COUNT(*) FROM studenthomework WHERE id=? AND studentid=?", submissionId, studentId);
    }

    private boolean exists(String sql, Object... args) {
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
            return count != null && count > 0;
        } catch (Exception e) {
            // 查不出来就当没权限，避免异常被当成放行
            return false;
        }
    }
}
