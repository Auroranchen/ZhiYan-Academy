package com.model;

import lombok.Data;
import java.util.Date;

/**
 * 站内消息实体类
 */
@Data
public class Message {

    private Integer id;
    private String title;
    private String content;
    private Integer sendertype;  // 1管理员 2教师
    private Integer senderid;
    private Integer receivertype;  // 1学生 2教师 3班级
    private Integer receiverid;
    private Integer isread;
    private Date readtime;
    private Date createtime;

    // 关联查询字段
    private String sendername;
    private String receivername;
}
