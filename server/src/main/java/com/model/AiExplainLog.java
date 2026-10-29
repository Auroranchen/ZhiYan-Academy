package com.model;

import lombok.Data;
import java.util.Date;

@Data
public class AiExplainLog {
    private Long id;
    private Integer homeworkId;
    private Integer studentId;
    private String questionId;
    private String keyPoints;
    private String errorAnalysis;
    private String steps;
    private String expansion;
    private String referenceCode;
    private String commonMistakes;
    private String version;
    private Date createTime;
    private Integer duration;
    private Integer isCacheHit;
}
