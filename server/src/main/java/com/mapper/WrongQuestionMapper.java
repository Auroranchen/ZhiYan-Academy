package com.mapper;

import com.model.WrongQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 学生错题本数据访问接口
 */
@Mapper
public interface WrongQuestionMapper {

    /**
     * 查询某学生所有可判分的在线作业提交（含题目JSON与作答JSON），用于归集错题。
     */
    List<Map<String, Object>> findGradableSubmissions(@Param("studentid") Integer studentid);

    /**
     * 插入或更新一条错题记录（按 学生+题目+来源 去重）。
     */
    int upsert(WrongQuestion wq);

    List<WrongQuestion> findByStudent(@Param("studentid") Integer studentid,
                                      @Param("courseid") Integer courseid,
                                      @Param("mastered") Integer mastered);

    Long countWrong(@Param("studentid") Integer studentid);

    Long countMastered(@Param("studentid") Integer studentid);

    WrongQuestion findById(@Param("id") Long id);

    int updateMastered(@Param("id") Long id, @Param("studentid") Integer studentid, @Param("mastered") Integer mastered);

    int delete(@Param("id") Long id, @Param("studentid") Integer studentid);
}
