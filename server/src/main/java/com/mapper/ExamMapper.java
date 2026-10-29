package com.mapper;

import com.model.Exam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 在线考试数据访问接口
 */
@Mapper
public interface ExamMapper {

    int insert(Exam exam);

    int update(Exam exam);

    int delete(@Param("examid") Integer examid);

    Exam findById(@Param("examid") Integer examid);

    List<Exam> findByTeacher(@Param("teacherid") Integer teacherid,
                             @Param("keyword") String keyword,
                             @Param("offset") Integer offset,
                             @Param("limit") Integer limit);

    Long countByTeacher(@Param("teacherid") Integer teacherid,
                        @Param("keyword") String keyword);

    /** 学生可参加的考试（按其班级关联课程过滤），含本人作答状态 */
    List<Exam> findAvailableForStudent(@Param("studentid") Integer studentid);

    int countSubmitted(@Param("examid") Integer examid);

    int updateStatus(@Param("examid") Integer examid, @Param("status") Integer status);

    /** 查询某考试未提交的学生列表 */
    List<java.util.Map<String, Object>> findUnsubmittedStudents(@Param("examid") Integer examid);
}
