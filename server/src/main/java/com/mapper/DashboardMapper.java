package com.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 数据可视化大屏聚合查询接口。
 * 全部为只读聚合统计，基于现有业务表实时计算。
 */
@Mapper
public interface DashboardMapper {

    long countStudents();

    long countTeachers();

    long countCourses();

    long countHomeworks();

    long countSubmissions();

    long countGraded();

    Double avgScore();

    /** 各课程作业数量 TopN */
    List<Map<String, Object>> homeworkByCourse(@Param("limit") Integer limit);

    /** 成绩区间分布 */
    List<Map<String, Object>> scoreDistribution();

    /** 近N天提交趋势 */
    List<Map<String, Object>> submitTrend(@Param("days") Integer days);

    /** 各课程平均分 TopN */
    List<Map<String, Object>> avgScoreByCourse(@Param("limit") Integer limit);

    /** 各专业学生人数 */
    List<Map<String, Object>> studentsByMajor();

    /** AI 讲解使用统计 */
    Map<String, Object> aiUsage();

    /** 教师课程任务量 TopN（按课程数） */
    List<Map<String, Object>> teacherWorkload(@Param("limit") Integer limit);
}
