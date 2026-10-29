package com.model;

import lombok.Data;
import java.util.Date;

/**
 * 学生考勤实体类
 */
@Data
public class StudentAttendance {

    private Integer id;
    private Integer courseid;
    private Integer classid;
    private Integer studentid;
    private Date attenddate;
    private Integer status;  // 1出勤 2迟到 3早退 4缺勤 5请假
    private String remark;
    private Date createtime;
    private Integer teacherid;
    private Integer sessionId;  // 关联的签到会话ID（来自手机签到时）

    // 关联查询字段
    private String coursename;
    private String classname;
    private String studentname;
    private String studentno;
    private String teachername;
}
