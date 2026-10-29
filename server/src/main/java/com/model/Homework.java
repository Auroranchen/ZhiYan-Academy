package com.model;

import lombok.Data;
import lombok.ToString;

import java.util.Date;

/**
 * 作业实体类
 */
@Data
public class Homework {

    /**
     * 作业ID
     */
    private Integer homeworkid;

    /**
     * 作业标题
     */
    private String title;

    /**
     * 作业内容/要求
     */
    private String content;

    /**
     * 所属课程ID
     */
    private Integer courseid;

    /**
     * 发布教师ID
     */
    private Integer teacherid;

    /**
     * 作业类型：1在线答题 2文档上传
     */
    private Integer type;

    /**
     * 截止时间
     */
    private Date deadline;

    /**
     * 创建时间
     */
    private Date createtime;

    // 关联查询字段

    /**
     * 课程名称（关联查询）
     */
    private String coursename;

    /**
     * 教师姓名（关联查询）
     */
    private String teachername;

    private String semester; // 学期

    /**
     * 已提交数量（统计）
     */
    private Integer submitCount;

    /**
     * 应提交总数（统计）
     */
    private Integer totalCount;

    /**
     * 开课班级名称串（关联查询）
     */
    private String classNamesStr;

    /**
     * 附件路径（Word/PDF文件）
     */
    private String attachment;

}
