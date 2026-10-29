package com.mapper;

import com.model.LearningPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 学习路径计划数据访问接口
 */
@Mapper
public interface LearningPlanMapper {

    int insert(LearningPlan plan);

    List<LearningPlan> findByStudent(@Param("studentid") Integer studentid);

    LearningPlan findById(@Param("id") Integer id);

    LearningPlan findByIdForStudent(@Param("id") Integer id, @Param("studentid") Integer studentid);

    int archive(@Param("id") Integer id, @Param("studentid") Integer studentid);

    int delete(@Param("id") Integer id, @Param("studentid") Integer studentid);

    int updateProgress(@Param("id") Integer id, @Param("studentid") Integer studentid,
                       @Param("progressJson") String progressJson);
}
