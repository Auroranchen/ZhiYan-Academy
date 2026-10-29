package com.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public record EnrollApplicationRequest(
        @NotBlank(message = "请填写学校 / 机构名称")
        @Size(max = 200, message = "学校名称不能超过200个字符")
        String school,
        @NotBlank(message = "请填写联系人")
        @Size(max = 100, message = "联系人不能超过100个字符")
        String name,
        @Size(max = 100, message = "职务不能超过100个字符")
        String role,
        @NotBlank(message = "请填写手机号")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入有效的手机号")
        String phone,
        @Size(max = 1000, message = "需求说明不能超过1000个字符")
        String remark,
        @Size(max = 255, message = "附件路径无效")
        String attachment,
        @Size(max = 255, message = "附件名称过长")
        String attachmentName,
        @NotBlank(message = "请先完成滑动验证")
        String captchaToken
) {}
