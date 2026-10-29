package com.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 学期新增/编辑请求。 */
public record AcademicTermRequest(
        @NotBlank(message = "学期编码不能为空")
        @Pattern(regexp = "^\\d{4}-\\d{4}-[1-3]$", message = "学期编码格式应为 2025-2026-1")
        String termCode,

        @NotBlank(message = "学期名称不能为空")
        @Size(max = 80, message = "学期名称不能超过80个字符")
        String termName,

        @NotNull(message = "开始日期不能为空")
        LocalDate startDate,

        @NotNull(message = "结束日期不能为空")
        LocalDate endDate
) {
    @AssertTrue(message = "结束日期不能早于开始日期")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
