package com.mapper;

import com.model.ExamRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 考试作答记录数据访问接口
 */
@Mapper
public interface ExamRecordMapper {

    int insert(ExamRecord record);

    int update(ExamRecord record);

    ExamRecord findByExamAndStudent(@Param("examid") Integer examid, @Param("studentid") Integer studentid);

    ExamRecord findById(@Param("id") Integer id);

    List<ExamRecord> findByExam(@Param("examid") Integer examid, @Param("keyword") String keyword);

    Map<String, Object> statsByExam(@Param("examid") Integer examid);
}
