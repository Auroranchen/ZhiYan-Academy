package com.model;

import lombok.Data;
import java.util.Date;

/**
 * 课程评价实体类
 */
@Data
public class CourseEvaluation {

    private Integer id;
    private Integer courseid;
    private Integer studentid;
    private Integer teachingscore;
    private Integer contentscore;
    private Integer difficultyscore;
    private Integer overallscore;
    private String comment;
    private Integer anonymous;
    private Date createtime;

    // 关联查询字段
    private String coursename;
    private String studentname;
    private String teachername;
}
