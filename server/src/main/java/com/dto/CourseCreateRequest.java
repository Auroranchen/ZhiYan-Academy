package com.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 创建课程请求。教师身份由登录 Session 决定，管理员可通过 teacherid 指定任课教师。 */
public record CourseCreateRequest(
        @NotBlank(message = "课程名称不能为空")
        @Size(max = 100, message = "课程名称不能超过100个字符")
        String coursename,

        @Positive(message = "任课教师ID必须为正整数")
        Integer teacherid,

        @NotNull(message = "课时不能为空")
        @Min(value = 1, message = "课时不能少于1")
        @Max(value = 1000, message = "课时不能超过1000")
        Integer hours,

        @Size(max = 5000, message = "课程描述不能超过5000个字符")
        String description,

        @NotBlank(message = "学期不能为空")
        @Pattern(regexp = "^\\d{4}-\\d{4}-[1-3]$", message = "学期格式应为 2025-2026-1")
        String semester,

        @Size(max = 100, message = "单门课程最多关联100个班级")
        List<@NotNull(message = "班级ID不能为空") @Positive(message = "班级ID必须为正整数") Integer> classIds
) {
}
