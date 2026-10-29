package com.mapper;

import com.model.StudentAttendance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 学生考勤数据访问接口
 */
@Mapper
public interface StudentAttendanceMapper {

    List<StudentAttendance> findList(@Param("courseid") Integer courseid,
            @Param("classid") Integer classid,
            @Param("attenddate") Date attenddate,
            @Param("courseTeacherId") Integer courseTeacherId,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit);

    Long count(@Param("courseid") Integer courseid,
            @Param("classid") Integer classid,
            @Param("attenddate") Date attenddate,
            @Param("courseTeacherId") Integer courseTeacherId);

    StudentAttendance findById(@Param("id") Integer id);

    StudentAttendance findByUnique(@Param("courseid") Integer courseid,
            @Param("classid") Integer classid,
            @Param("studentid") Integer studentid,
            @Param("attenddate") Date attenddate);

    /** 一次查出某课程+班级+日期下的全部考勤记录（避免循环逐条查询的 N+1 问题） */
    List<StudentAttendance> findByCourseClassDate(@Param("courseid") Integer courseid,
            @Param("classid") Integer classid,
            @Param("attenddate") Date attenddate);

    List<StudentAttendance> findByStudentid(@Param("studentid") Integer studentid,
            @Param("courseid") Integer courseid);

    int insert(StudentAttendance attendance);

    int batchInsert(@Param("list") List<StudentAttendance> list);

    int update(StudentAttendance attendance);

    int updateCheckin(StudentAttendance attendance);

    int delete(@Param("id") Integer id);

    // 统计
    Long countByStatus(@Param("courseid") Integer courseid,
            @Param("classid") Integer classid,
            @Param("status") Integer status);
}
