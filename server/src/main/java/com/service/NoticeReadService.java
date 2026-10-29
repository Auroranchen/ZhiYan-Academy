package com.service;

import com.mapper.NoticeReadMapper;
import com.model.NoticeRead;
import com.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 阅读状态业务逻辑层
 */
@Service
public class NoticeReadService {

    @Autowired
    private NoticeReadMapper noticeReadMapper;

    /**
     * 标记公告已读（学生）
     */
    public Result<Void> markNoticeReadByStudent(Integer noticeid, Integer studentid) {
        if (noticeid == null || studentid == null) {
            return Result.error("参数不完整");
        }

        // 检查是否已读
        NoticeRead existing = noticeReadMapper.findNoticeRead(noticeid, studentid, null);
        if (existing != null) {
            return Result.success(); // 已读过
        }

        NoticeRead noticeRead = new NoticeRead();
        noticeRead.setNoticeid(noticeid);
        noticeRead.setStudentid(studentid);
        noticeRead.setReadtime(new Date());

        int rows = noticeReadMapper.insertNoticeRead(noticeRead);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("标记失败");
    }

    /**
     * 标记公告已读（教师）
     */
    public Result<Void> markNoticeReadByTeacher(Integer noticeid, Integer teacherid) {
        if (noticeid == null || teacherid == null) {
            return Result.error("参数不完整");
        }

        NoticeRead existing = noticeReadMapper.findNoticeRead(noticeid, null, teacherid);
        if (existing != null) {
            return Result.success();
        }

        NoticeRead noticeRead = new NoticeRead();
        noticeRead.setNoticeid(noticeid);
        noticeRead.setTeacherid(teacherid);
        noticeRead.setReadtime(new Date());

        int rows = noticeReadMapper.insertNoticeRead(noticeRead);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("标记失败");
    }

    /**
     * 标记作业已读
     */
    public Result<Void> markHomeworkRead(Integer homeworkid, Integer studentid) {
        if (homeworkid == null || studentid == null) {
            return Result.error("参数不完整");
        }

        NoticeRead existing = noticeReadMapper.findHomeworkRead(homeworkid, studentid);
        if (existing != null) {
            return Result.success();
        }

        NoticeRead noticeRead = new NoticeRead();
        noticeRead.setHomeworkid(homeworkid);
        noticeRead.setStudentid(studentid);
        noticeRead.setReadtime(new Date());

        int rows = noticeReadMapper.insertHomeworkRead(noticeRead);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("标记失败");
    }

    /**
     * 检查公告是否已读
     */
    public boolean isNoticeRead(Integer noticeid, Integer studentid, Integer teacherid) {
        NoticeRead existing = noticeReadMapper.findNoticeRead(noticeid, studentid, teacherid);
        return existing != null;
    }

    /**
     * 检查作业是否已读
     */
    public boolean isHomeworkRead(Integer homeworkid, Integer studentid) {
        NoticeRead existing = noticeReadMapper.findHomeworkRead(homeworkid, studentid);
        return existing != null;
    }

    /** 当前学生已读公告 ID */
    public List<Integer> listNoticeIdsByStudent(Integer studentid) {
        if (studentid == null) return java.util.Collections.emptyList();
        List<Integer> ids = noticeReadMapper.listNoticeIdsByStudent(studentid);
        return ids != null ? ids : java.util.Collections.emptyList();
    }

    /**
     * 获取公告阅读统计
     */
    public Map<String, Object> getNoticeReadStats(Integer noticeid) {
        Long studentCount = noticeReadMapper.countNoticeReadByStudents(noticeid);
        Long teacherCount = noticeReadMapper.countNoticeReadByTeachers(noticeid);

        Map<String, Object> stats = new HashMap<>();
        stats.put("studentReadCount", studentCount != null ? studentCount : 0L);
        stats.put("teacherReadCount", teacherCount != null ? teacherCount : 0L);
        return stats;
    }

    /**
     * 获取作业阅读统计
     */
    public Map<String, Object> getHomeworkReadStats(Integer homeworkid) {
        Long readCount = noticeReadMapper.countHomeworkRead(homeworkid);
        Long totalCount = noticeReadMapper.countHomeworkTotalStudents(homeworkid);

        long read = readCount != null ? readCount : 0L;
        long total = totalCount != null ? totalCount : 0L;
        long unread = total - read;

        Map<String, Object> stats = new HashMap<>();
        stats.put("readCount", read);
        stats.put("unreadCount", unread < 0 ? 0L : unread);
        stats.put("totalCount", total);
        return stats;
    }

    /**
     * 获取未读公告的学生列表
     */
    public List<NoticeRead> getUnreadStudents(Integer noticeid, Integer classid) {
        return noticeReadMapper.findUnreadStudents(noticeid, classid);
    }

    /**
     * 获取未读作业的学生列表
     */
    public List<NoticeRead> getUnreadHomeworkStudents(Integer homeworkid, Integer classid) {
        return noticeReadMapper.findUnreadHomeworkStudents(homeworkid, classid);
    }

    /**
     * 获取作业所有学生的阅读状态
     */
    public List<Map<String, Object>> getAllHomeworkReadStatus(Integer homeworkid, Integer classid) {
        return noticeReadMapper.findAllHomeworkReadStatus(homeworkid, classid);
    }
}
