package com.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 作业互评数据访问接口。
 * 涉及 peer_review_task（每个作业的互评配置）与 peer_review（互评任务/结果）两张表。
 */
@Mapper
public interface PeerReviewMapper {

    /** 互评配置 */
    Map<String, Object> getTask(@Param("homeworkid") Integer homeworkid);

    int upsertTask(@Param("homeworkid") Integer homeworkid,
                   @Param("teacherid") Integer teacherid,
                   @Param("reviewersPer") Integer reviewersPer,
                   @Param("maxScore") Integer maxScore,
                   @Param("anonymous") Integer anonymous);

    int updateTaskStatus(@Param("homeworkid") Integer homeworkid, @Param("status") Integer status);

    /** 某作业的有效提交（已提交/已批改） */
    List<Map<String, Object>> listSubmissions(@Param("homeworkid") Integer homeworkid);

    /** 清空某作业的互评分配（重新分配前调用） */
    int deleteAssignments(@Param("homeworkid") Integer homeworkid);

    int insertAssignment(@Param("homeworkid") Integer homeworkid,
                         @Param("reviewerid") Integer reviewerid,
                         @Param("submitid") Integer submitid,
                         @Param("revieweeid") Integer revieweeid);

    /** 学生待评 / 全部任务 */
    List<Map<String, Object>> listByReviewer(@Param("reviewerid") Integer reviewerid,
                                             @Param("status") Integer status);

    /** 单条互评任务详情（含被评作业内容） */
    Map<String, Object> getReview(@Param("id") Integer id);

    int submitReview(@Param("id") Integer id,
                     @Param("reviewerid") Integer reviewerid,
                     @Param("score") java.math.BigDecimal score,
                     @Param("comment") String comment);

    /** 我收到的互评（已完成的对我作业的评价） */
    List<Map<String, Object>> listReceived(@Param("revieweeid") Integer revieweeid);

    /** 教师汇总：每个被评学生的互评均分与条数 */
    List<Map<String, Object>> summaryByHomework(@Param("homeworkid") Integer homeworkid);

    /** 进度统计：总任务数、已完成数 */
    Map<String, Object> progress(@Param("homeworkid") Integer homeworkid);
}
