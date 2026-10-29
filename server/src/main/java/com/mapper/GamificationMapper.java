package com.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 积分 / 徽章 / 打卡 数据访问接口。
 * 涉及 student_points、points_log、badge_def、student_badge 四张表。
 */
@Mapper
public interface GamificationMapper {

    Map<String, Object> getPoints(@Param("studentid") Integer studentid);

    /** 累加积分（不存在则初始化） */
    int addPoints(@Param("studentid") Integer studentid, @Param("delta") Integer delta);

    int updateLevel(@Param("studentid") Integer studentid, @Param("level") Integer level);

    int updateCheckin(@Param("studentid") Integer studentid,
                      @Param("date") String date,
                      @Param("continuousDays") Integer continuousDays);

    int insertLog(@Param("studentid") Integer studentid,
                  @Param("points") Integer points,
                  @Param("type") String type,
                  @Param("reason") String reason);

    List<Map<String, Object>> recentLogs(@Param("studentid") Integer studentid, @Param("limit") Integer limit);

    List<Map<String, Object>> listBadgeDefs();

    List<Map<String, Object>> listStudentBadges(@Param("studentid") Integer studentid);

    int awardBadge(@Param("studentid") Integer studentid, @Param("code") String code);

    long countHomeworkSubmissions(@Param("studentid") Integer studentid);

    /** 排行榜：可按班级过滤 */
    List<Map<String, Object>> leaderboard(@Param("classid") Integer classid, @Param("limit") Integer limit);

    /** 某学生在全校的积分排名 */
    Long rankOf(@Param("studentid") Integer studentid);
}
