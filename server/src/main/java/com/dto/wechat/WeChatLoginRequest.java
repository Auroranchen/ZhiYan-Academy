package com.dto.wechat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 微信登录请求参数。
 */
public record WeChatLoginRequest(
        @NotBlank(message = "微信登录凭证不能为空")
        @Size(max = 256, message = "微信登录凭证格式不正确")
        String code
) {
}
