package com.model;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 考试作答记录实体类
 */
@Data
public class ExamRecord {

    private Integer id;
    private Integer examid;
    private Integer studentid;
    private String answers;
    private BigDecimal score;
    private BigDecimal objectiveScore;
    private Integer status;
    private Date starttime;
    private Date submittime;
    private Integer usedSeconds;
    private Integer switchCount;
    private Integer autoSubmit;
    private String aiComment;

    /** 关联展示字段 */
    private String studentname;
    private String studentno;
    private String examtitle;
    private Integer examTotalScore;
}
