package com.model;

import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * 作业评论实体类
 */
@Data
public class HomeworkComment {

    private Integer id;
    private Integer homeworkid;
    private Integer studentid;
    private Integer teacherid;
    private Integer parentid;
    private String content;
    private Date createtime;

    // 关联查询字段
    private String studentname;
    private String teachername;
    private String homeworktitle;
    private List<HomeworkComment> replies;
}
