package com.dto.wechat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 微信绑定请求参数。
 */
public record WeChatBindRequest(
        @NotBlank(message = "微信登录凭证不能为空")
        @Size(max = 256, message = "微信登录凭证格式不正确")
        String code,
        @NotBlank(message = "学号不能为空")
        @Size(max = 30, message = "学号格式不正确")
        String studentno,
        @NotBlank(message = "密码不能为空")
        @Size(max = 100, message = "密码格式不正确")
        String password
) {
}
