package com.model;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 学生作业实体类
 */
@Data
public class StudentHomework {

    /**
     * 学生作业ID
     */
    private Integer id;

    /**
     * 作业ID
     */
    private Integer homeworkid;

    /**
     * 学生ID
     */
    private Integer studentid;

    /**
     * 作业内容（在线答题）
     */
    private String content;

    /**
     * 文件路径（文档上传）
     */
    private String filepath;

    /**
     * 成绩
     */
    private BigDecimal score;

    /**
     * 评语
     */
    private String comment;

    /**
     * 状态：0未提交 1已提交 2已批改 3已打回
     */
    private Integer status;

    /**
     * 提交时间
     */
    private Date submittime;

    /**
     * 批改时间
     */
    private Date gradetime;

    // 关联查询字段

    /**
     * 学号（关联查询）
     */
    private String studentno;

    /**
     * 学生姓名（关联查询）
     */
    private String studentname;

    /**
     * 作业标题（关联查询）
     */
    private String homeworktitle;

    /**
     * 课程ID（关联查询）
     */
    private Integer courseid;

    /**
     * 课程名称（关联查询）
     */
    private String coursename;

    /**
     * 作业类型（关联查询）
     */
    private Integer homeworktype;

    /**
     * 批改教师（关联查询）
     */
    private String teachername;

    /**
     * 截止时间（关联查询）
     */
    private Date deadline;

    /**
     * 作业内容/试题（关联查询，包含正确答案）
     */
    private String homeworkcontent;

    private String semester; // 学期

    /**
     * 班级ID（关联查询）
     */
    private Integer classid;

    /**
     * 班级名称（关联查询）
     */
    private String classname;

    /**
     * AI评分状态：PENDING/GRADING/COMPLETED/FAILED
     */
    private String gradingStatus;

}
