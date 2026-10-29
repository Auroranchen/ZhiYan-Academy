package com.mapper;

import com.model.NoticeRead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 阅读状态数据访问接口
 */
@Mapper
public interface NoticeReadMapper {

    // 标记已读
    int insertNoticeRead(NoticeRead noticeRead);

    int insertHomeworkRead(NoticeRead noticeRead);

    // 查询是否已读
    NoticeRead findNoticeRead(@Param("noticeid") Integer noticeid,
            @Param("studentid") Integer studentid,
            @Param("teacherid") Integer teacherid);

    NoticeRead findHomeworkRead(@Param("homeworkid") Integer homeworkid,
            @Param("studentid") Integer studentid);

    // 统计公告已读人数
    Long countNoticeReadByStudents(@Param("noticeid") Integer noticeid);

    Long countNoticeReadByTeachers(@Param("noticeid") Integer noticeid);

    // 统计作业已读人数
    Long countHomeworkRead(@Param("homeworkid") Integer homeworkid);

    // 获取未读学生列表
    List<NoticeRead> findUnreadStudents(@Param("noticeid") Integer noticeid,
            @Param("classid") Integer classid);

    // 获取未读作业学生列表
    List<NoticeRead> findUnreadHomeworkStudents(@Param("homeworkid") Integer homeworkid,
            @Param("classid") Integer classid);

    // 获取作业所有学生的阅读状态
    List<java.util.Map<String, Object>> findAllHomeworkReadStatus(@Param("homeworkid") Integer homeworkid,
            @Param("classid") Integer classid);

    // 统计作业关联的总学生数
    Long countHomeworkTotalStudents(@Param("homeworkid") Integer homeworkid);

    /** 学生已读公告 ID 列表 */
    List<Integer> listNoticeIdsByStudent(@Param("studentid") Integer studentid);
}
