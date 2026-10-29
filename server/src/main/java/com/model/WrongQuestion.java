package com.model;

import lombok.Data;
import java.util.Date;

/**
 * 学生错题本条目实体类
 */
@Data
public class WrongQuestion {

    private Long id;
    private Integer studentid;
    private Integer courseid;
    private Integer sourceid;
    private String source;
    private String questionId;
    private String questionType;
    private String questionTitle;
    private String optionsJson;
    private String correctAnswer;
    private String studentAnswer;
    private Integer wrongCount;
    private Integer mastered;
    private Date lastWrongTime;
    private Date createTime;

    /** 关联展示字段 */
    private String coursename;
}
