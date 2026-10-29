package com.model;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学年学期主数据。课程通过 semester 字段保存 termCode，以兼容现有数据。
 */
@Data
public class AcademicTerm {
    private Integer termId;
    private String termCode;
    private String termName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Boolean current;
    private Integer courseCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
