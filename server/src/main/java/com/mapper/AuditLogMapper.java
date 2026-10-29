package com.mapper;

import com.model.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 操作审计日志数据访问接口
 */
@Mapper
public interface AuditLogMapper {

    int insert(AuditLog log);

    List<AuditLog> findList(@Param("userType") String userType,
                            @Param("action") String action,
                            @Param("keyword") String keyword,
                            @Param("offset") Integer offset,
                            @Param("limit") Integer limit);

    Long count(@Param("userType") String userType,
               @Param("action") String action,
               @Param("keyword") String keyword);

    /** 按动作统计数量 */
    List<Map<String, Object>> countByAction();

    /** 近N天每日操作量 */
    List<Map<String, Object>> dailyTrend(@Param("days") Integer days);

    int deleteBefore(@Param("days") Integer days);
}
