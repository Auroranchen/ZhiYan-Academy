package com.model;

import lombok.Data;
import java.util.Date;

/**
 * AI 学习路径计划实体类
 */
@Data
public class LearningPlan {

    private Integer id;
    private Integer studentid;
    private Integer courseid;
    private String title;
    private String summary;
    private String content;
    private Integer status;
    /** 任务完成进度 JSON（服务端持久化，跨设备同步） */
    private String progressJson;
    private Date createTime;

    /** 关联展示字段 */
    private String coursename;
}
