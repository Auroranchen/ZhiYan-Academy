package com.controller;

import com.response.Result;
import com.service.SliderCaptchaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 滑动拼图验证码控制器。
 */
@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    @Autowired
    private SliderCaptchaService sliderCaptchaService;

    /** 获取一道滑动拼图验证 */
    @GetMapping("/slider")
    public Result<Map<String, Object>> slider() {
        return Result.success(sliderCaptchaService.generate());
    }

    /** 校验滑动结果，成功返回一次性令牌 */
    @PostMapping("/verify")
    public Result<Map<String, Object>> verify(@RequestBody Map<String, Object> body) {
        String captchaId = body.get("captchaId") != null ? body.get("captchaId").toString() : null;
        if (captchaId == null || captchaId.isEmpty()) {
            return Result.error("缺少验证标识");
        }
        int x;
        try {
            x = (int) Math.round(Double.parseDouble(String.valueOf(body.get("x"))));
        } catch (Exception e) {
            return Result.error("滑动位置无效");
        }
        Map<String, Object> result = sliderCaptchaService.verify(captchaId, x);
        return Result.success(result);
    }
}
