package com.model;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 作业查重报告实体类
 */
@Data
public class PlagiarismReport {

    private Integer id;
    private Integer homeworkid;
    private Integer teacherid;
    private Integer pairCount;
    private Integer flaggedCount;
    private BigDecimal maxSimilarity;
    private BigDecimal threshold;
    private String resultJson;
    private Date createTime;

    /** 关联展示字段 */
    private String homeworktitle;
    private String coursename;
}
