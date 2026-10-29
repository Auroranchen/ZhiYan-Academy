package com.model;

import lombok.Data;

/**
 * 课程班级关联实体类
 */
@Data
public class CourseClass {

    /**
     * 关联ID
     */
    private Integer id;

    /**
     * 课程ID
     */
    private Integer courseid;

    /**
     * 班级ID
     */
    private Integer classid;

    // 关联查询字段

    /**
     * 班级名称（关联查询）
     */
    private String classname;

    public CourseClass() {
    }

    public CourseClass(Integer courseid, Integer classid) {
        this.courseid = courseid;
        this.classid = classid;
    }

}
