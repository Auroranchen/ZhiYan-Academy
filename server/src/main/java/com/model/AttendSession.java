package com.model;

import lombok.Data;
import java.util.Date;

@Data
public class AttendSession {
    private Integer id;
    private Integer courseid;
    private Integer classid;
    private Integer teacherid;
    private String checkinCode;
    private String title;
    private Integer type;       // 1=签到码, 2=一键签到
    private Integer lateMinutes;
    private Integer duration;
    private Integer status;     // 1=进行中, 2=已结束
    private Date startTime;
    private Date endTime;
    private Date createTime;

    // 关联字段
    private String coursename;
    private String classname;
    private String teachername;
    private Integer totalStudents;
    private Integer checkedCount;
}
