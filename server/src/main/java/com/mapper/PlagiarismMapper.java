package com.mapper;

import com.model.PlagiarismReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 作业查重数据访问接口
 */
@Mapper
public interface PlagiarismMapper {

    /** 获取某作业全部学生提交的文本内容（在线作业的作答JSON） */
    List<Map<String, Object>> findSubmissionsForCheck(@Param("homeworkid") Integer homeworkid);

    int insertReport(PlagiarismReport report);

    List<PlagiarismReport> findByHomework(@Param("homeworkid") Integer homeworkid);

    List<PlagiarismReport> findByTeacher(@Param("teacherid") Integer teacherid);

    PlagiarismReport findById(@Param("id") Integer id);
}
