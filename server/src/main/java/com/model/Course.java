package com.model;

import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * 课程实体类
 */
@Data
public class Course {

    /**
     * 课程ID
     */
    private Integer courseid;

    /**
     * 课程名称
     */
    private String coursename;

    /**
     * 任课教师ID
     */
    private Integer teacherid;

    /**
     * 课时
     */
    private Integer hours;

    /**
     * 课程描述
     */
    private String description;

    /**
     * 学期
     */
    private String semester;

    /**
     * 创建时间
     */
    private Date createtime;

    // 关联查询字段

    /**
     * 教师姓名（关联查询）
     */
    private String teachername;

    /**
     * 开课班级ID列表（关联查询）
     */
    private List<Integer> classIds;

    /**
     * 开课班级名称列表（关联查询）
     */
    private List<String> classNames;

    /**
     * 开课班级名称（逗号分隔，用于列表显示）
     */
    private String classNamesStr;

    /**
     * 课程码
     */
    private String coursecode;

}
