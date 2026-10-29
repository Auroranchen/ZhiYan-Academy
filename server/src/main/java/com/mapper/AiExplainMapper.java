package com.mapper;

import com.model.AiExplainLog;
import com.model.StudentFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiExplainMapper {
    
    // 查找缓存
    AiExplainLog findCache(@Param("homeworkId") Integer homeworkId, 
                          @Param("studentId") Integer studentId, 
                          @Param("questionId") String questionId,
                          @Param("version") String version);

    // 查找缓存（不含新字段，兼容旧表结构）
    AiExplainLog findCacheBasic(@Param("homeworkId") Integer homeworkId, 
                          @Param("studentId") Integer studentId, 
                          @Param("questionId") String questionId,
                          @Param("version") String version);

    // 插入日志
    int insertLog(AiExplainLog log);

    // 插入日志（不含新字段，兼容旧表结构）
    int insertLogBasic(AiExplainLog log);

    // 插入收藏
    int insertFavorite(StudentFavorite favorite);

    // 检查是否已收藏
    int checkFavorite(@Param("studentId") Integer studentId, 
                     @Param("questionId") String questionId);
                     
    // 获取收藏列表 (可选)
    List<StudentFavorite> getFavorites(@Param("studentId") Integer studentId);

    /** 按收藏主键删除（需校验归属） */
    int deleteFavorite(@Param("id") Integer id, @Param("studentId") Integer studentId);
}
