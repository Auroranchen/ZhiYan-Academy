package com.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 统一 Session 鉴权。所有未进入公开白名单的 MVC 接口默认要求登录，
 * 角色限制通过 {@link RequireRole} 显式声明。
 */
@Component
public class SessionAuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public SessionAuthInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        HttpSession session = request.getSession(false);
        String role = resolveRole(session);
        if (role == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "登录已失效，请重新登录");
            return false;
        }

        if (handler instanceof HandlerMethod handlerMethod) {
            RequireRole requirement = handlerMethod.getMethodAnnotation(RequireRole.class);
            if (requirement == null) {
                requirement = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
            }
            if (requirement != null && Arrays.stream(requirement.value()).noneMatch(role::equals)) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "无权执行此操作");
                return false;
            }
        }
        return true;
    }

    private String resolveRole(HttpSession session) {
        if (session == null) return null;
        Object explicitRole = session.getAttribute("role");
        if (explicitRole != null) return String.valueOf(explicitRole);
        // 兼容改造前创建的 Session。
        if (session.getAttribute("adminid") != null) return "admin";
        if (session.getAttribute("teacherid") != null) return "teacher";
        if (session.getAttribute("studentid") != null) return "student";
        return null;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.error(status, message));
    }
}
