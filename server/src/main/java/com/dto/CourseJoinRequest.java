package com.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 学生通过课程码加入课程请求。 */
public record CourseJoinRequest(
        @NotBlank(message = "课程码不能为空")
        @Size(max = 20, message = "课程码不能超过20个字符")
        String coursecode
) {
}
