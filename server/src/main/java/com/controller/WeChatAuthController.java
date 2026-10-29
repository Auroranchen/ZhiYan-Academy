package com.controller;

import com.config.RequireRole;
import com.dto.wechat.WeChatBindRequest;
import com.dto.wechat.WeChatLoginRequest;
import com.dto.wechat.WeChatUnbindRequest;
import com.exception.WeChatAuthException;
import com.model.Student;
import com.response.Result;
import com.service.LoginSecurityService;
import com.service.StudentSessionService;
import com.service.WeChatAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/wechat/auth")
public class WeChatAuthController {
    private static final int CODE_LOCKED = 423;
    private static final int CODE_TOO_MANY = 429;

    private final WeChatAuthService weChatAuthService;
    private final StudentSessionService studentSessionService;
    private final LoginSecurityService loginSecurity;

    public WeChatAuthController(WeChatAuthService weChatAuthService,
                                StudentSessionService studentSessionService,
                                LoginSecurityService loginSecurity) {
        this.weChatAuthService = weChatAuthService;
        this.studentSessionService = studentSessionService;
        this.loginSecurity = loginSecurity;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody WeChatLoginRequest body,
                                             HttpServletRequest request,
                                             HttpSession session) {
        guardIp(request);
        Student student = weChatAuthService.login(body.code());
        return Result.success("微信登录成功", studentSessionService.establish(student, request, session));
    }

    @PostMapping("/bind")
    public Result<Map<String, Object>> bind(@Valid @RequestBody WeChatBindRequest body,
                                            HttpServletRequest request,
                                            HttpSession session) {
        guardIp(request);
        String account = body.studentno().trim();
        long lockRemain = loginSecurity.getLockRemainingSeconds(account);
        if (lockRemain > 0) {
            throw new WeChatAuthException(CODE_LOCKED,
                    "账号已被锁定，请于 " + ((lockRemain + 59) / 60) + " 分钟后再试");
        }
        try {
            Student student = weChatAuthService.bind(body.code(), account, body.password());
            loginSecurity.clearFailure(account);
            return Result.success("微信绑定成功", studentSessionService.establish(student, request, session));
        } catch (WeChatAuthException exception) {
            if (exception.getCode() == 401) {
                int remaining = loginSecurity.recordFailure(account);
                if (remaining <= 0) {
                    throw new WeChatAuthException(CODE_LOCKED,
                            "账号已被锁定，请于 " + loginSecurity.getLockMinutes() + " 分钟后再试");
                }
                throw new WeChatAuthException(401,
                        "学号或密码错误，还可尝试 " + remaining + " 次");
            }
            throw exception;
        }
    }

    @GetMapping("/status")
    @RequireRole("student")
    public Result<Map<String, Boolean>> status(HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        return Result.success(Map.of("bound", weChatAuthService.isBound(studentid)));
    }

    @PostMapping("/unbind")
    @RequireRole("student")
    public Result<Void> unbind(@Valid @RequestBody WeChatUnbindRequest body, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        weChatAuthService.unbind(studentid, body.password());
        return Result.successMsg("微信已解绑，当前登录不受影响");
    }

    private void guardIp(HttpServletRequest request) {
        if (!loginSecurity.allowIp(loginSecurity.resolveIp(request))) {
            throw new WeChatAuthException(CODE_TOO_MANY, "微信登录请求过于频繁，请稍后再试");
        }
    }
}
