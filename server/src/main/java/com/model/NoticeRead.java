package com.model;

import lombok.Data;
import java.util.Date;

/**
 * 阅读状态实体类
 */
@Data
public class NoticeRead {

    private Integer id;
    private Integer noticeid;
    private Integer homeworkid;
    private Integer studentid;
    private Integer teacherid;
    private Date readtime;

    // 关联查询字段
    private String noticetitle;
    private String homeworktitle;
    private String studentname;
    private String teachername;
}
