package com.mapper;

import com.model.CourseEvaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 课程评价数据访问接口
 */
@Mapper
public interface CourseEvaluationMapper {

    List<CourseEvaluation> findByCourseid(@Param("courseid") Integer courseid,
                                          @Param("offset") Integer offset,
                                          @Param("limit") Integer limit);

    Long countByCourseid(@Param("courseid") Integer courseid);

    CourseEvaluation findById(@Param("id") Integer id);

    CourseEvaluation findByStudentAndCourse(@Param("studentid") Integer studentid,
                                            @Param("courseid") Integer courseid);

    List<CourseEvaluation> findByStudentid(@Param("studentid") Integer studentid);

    int insert(CourseEvaluation evaluation);

    int update(CourseEvaluation evaluation);

    int delete(@Param("id") Integer id);

    // 统计平均分
    Map<String, Object> getAverageScores(@Param("courseid") Integer courseid);
}
