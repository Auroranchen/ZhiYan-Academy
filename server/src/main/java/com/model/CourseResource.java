package com.model;

import lombok.Data;
import java.util.Date;

/**
 * 课程资源实体类
 */
@Data
public class CourseResource {

    private Integer id;
    private Integer courseid;
    private Integer teacherid;
    private String title;
    private String description;
    private String filetype;
    private String filepath;
    private String filename;
    private Long filesize;
    private Integer downloadcount;
    private Date createtime;

    // 关联查询字段
    private String coursename;
    private String teachername;
}
