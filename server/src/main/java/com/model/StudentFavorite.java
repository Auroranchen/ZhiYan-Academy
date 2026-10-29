package com.model;

import lombok.Data;
import java.util.Date;

@Data
public class StudentFavorite {
    private Integer id;
    private Integer studentId;
    private Integer homeworkId;
    private String questionId;
    private String questionContent;
    private String explainContent;
    private Date createTime;
}
