package com.model;

import lombok.Data;
import java.util.Date;

/**
 * 通知公告实体类
 */
@Data
public class Notice {

    /**
     * 公告ID
     */
    private Integer noticeid;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 创建时间
     */
    private Date createtime;

}
