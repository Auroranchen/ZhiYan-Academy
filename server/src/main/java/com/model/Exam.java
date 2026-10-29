package com.model;

import lombok.Data;
import java.util.Date;

/**
 * 在线考试实体类
 */
@Data
public class Exam {

    private Integer examid;
    private String title;
    private Integer courseid;
    private Integer teacherid;
    private String description;
    private String content;
    private Integer duration;
    private Integer totalScore;
    private Date starttime;
    private Date endtime;
    private Integer status;
    private Integer antiCheat;
    private Date createtime;

    /** 关联展示字段 */
    private String coursename;
    private String teachername;
    private Integer questionCount;
    private Integer recordStatus;
    private Integer submittedCount;
}
