package com.service;

import com.mapper.AuditLogMapper;
import com.model.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 操作审计日志业务层。
 * 写入采用异步线程池，避免审计影响主请求性能。
 */
@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    @Autowired
    private AuditLogMapper auditLogMapper;

    /**
     * 异步记录一条审计日志（任何异常都被吞掉，绝不影响业务）。
     */
    @Async
    public void saveAsync(AuditLog log) {
        try {
            if (log.getCreateTime() == null) {
                log.setCreateTime(new Date());
            }
            auditLogMapper.insert(log);
        } catch (Exception e) {
            logger.warn("写入审计日志失败: {}", e.getMessage());
        }
    }

    public List<AuditLog> getList(String userType, String action, String keyword, Integer page, Integer pageSize) {
        int offset = (Math.max(page, 1) - 1) * pageSize;
        return auditLogMapper.findList(emptyToNull(userType), emptyToNull(action), emptyToNull(keyword), offset, pageSize);
    }

    public Long getCount(String userType, String action, String keyword) {
        return auditLogMapper.count(emptyToNull(userType), emptyToNull(action), emptyToNull(keyword));
    }

    public List<Map<String, Object>> getActionStat() {
        return auditLogMapper.countByAction();
    }

    public List<Map<String, Object>> getDailyTrend(int days) {
        return auditLogMapper.dailyTrend(days);
    }

    public int cleanup(int days) {
        return auditLogMapper.deleteBefore(days);
    }

    private String emptyToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }
}
