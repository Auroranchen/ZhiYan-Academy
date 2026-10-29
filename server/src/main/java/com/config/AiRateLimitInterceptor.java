package com.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 与语音费用型接口的用户级固定窗口限流。
 */
@Component
public class AiRateLimitInterceptor implements HandlerInterceptor {

    /**
     * JSON 序列化工具。
     */
    private final ObjectMapper objectMapper;

    /**
     * 滑动窗口计数器：key 为「用户:分钟」，value 为该分钟内的计数窗口。
     */
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * 每分钟每用户允许的最大请求数。
     */
    @Value("${app.ai-rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;


    public AiRateLimitInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 在请求处理之前执行拦截。
     *
     * @param request  HTTP 请求对象
     * @param response HTTP 响应对象
     * @param handler  处理请求的处理器对象
     * @return 如果返回 {@code true}，则继续处理请求；如果返回 {@code false}，则中断请求处理
     * @throws Exception 如果发生异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        String userKey = userKey(session);
        if (userKey == null) return true; // 统一鉴权拦截器负责未登录请求

        long minute = System.currentTimeMillis() / 60_000L;
        String key = userKey + ":" + minute;
        Window window = windows.computeIfAbsent(key, ignored -> new Window(minute));
        if (window.count.incrementAndGet() > Math.max(1, requestsPerMinute)) {
            response.setStatus(429);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Result.error(429, "AI 请求过于频繁，请稍后再试"));
            return false;
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> entry.getValue().minute < minute - 1);
        }
        return true;
    }

    /**
     * 从会话中获取用户密钥。
     *
     * @param session HTTP 会话对象
     * @return 用户密钥，如果会话为空或会话中不存在用户信息则返回 {@code null}
     */
    private String userKey(HttpSession session) {
        if (session == null) return null;
        if (session.getAttribute("adminid") != null) return "admin:" + session.getAttribute("adminid");
        if (session.getAttribute("teacherid") != null) return "teacher:" + session.getAttribute("teacherid");
        if (session.getAttribute("studentid") != null) return "student:" + session.getAttribute("studentid");
        return null;
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
