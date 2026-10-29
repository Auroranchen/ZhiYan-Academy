package com.model;

import lombok.Data;
import java.util.Date;

/**
 * 操作审计日志实体类
 */
@Data
public class AuditLog {

    private Long id;
    /** 用户类型：admin/teacher/student/anon */
    private String userType;
    private Integer userId;
    private String userName;
    /** 动作：LOGIN/LOGOUT/CREATE/UPDATE/DELETE/EXPORT/GRADE/OTHER */
    private String action;
    private String module;
    private String target;
    private String method;
    private String uri;
    private String ip;
    private String detail;
    /** 结果：1成功 0失败 */
    private Integer status;
    private Integer costMs;
    private Date createTime;
}
