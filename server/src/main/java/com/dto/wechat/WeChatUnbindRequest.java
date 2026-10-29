package com.dto.wechat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 微信解绑请求参数。
 */
public record WeChatUnbindRequest(
        @NotBlank(message = "请输入当前学生账号密码")
        @Size(max = 100, message = "密码格式不正确")
        String password
) {
}
