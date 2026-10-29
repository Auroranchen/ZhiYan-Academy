package com.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Set;

/**
 * Actuator 端点访问保护：
 * 1. 本机（127.0.0.1 / ::1 / localhost）请求放行；
 * 2. 外部请求必须携带 X-Actuator-Token 请求头且值匹配 app.actuator.token 配置；
 * 3. 不满足条件返回 403。
 *
 * 轻量实现，不依赖 Spring Security。
 */
@Configuration
public class ActuatorSecurityFilter {

    @Value("${app.actuator.token:}")
    private String expectedToken;

    private static final Set<String> LOCAL_ADDRS = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");

    @Bean
    public FilterRegistrationBean<Filter> actuatorFilter() {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                    throws IOException, ServletException {
                HttpServletRequest request = (HttpServletRequest) req;
                HttpServletResponse response = (HttpServletResponse) res;

                String remoteAddr = request.getRemoteAddr();
                if (LOCAL_ADDRS.contains(remoteAddr) && request.getRequestURI().endsWith("/actuator/health")) {
                    chain.doFilter(req, res);
                    return;
                }

                String token = request.getHeader("X-Actuator-Token");
                if (expectedToken != null && !expectedToken.isBlank() && expectedToken.equals(token)) {
                    chain.doFilter(req, res);
                    return;
                }

                response.setStatus(403);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"msg\":\"Actuator access denied\"}");
            }
        });
        reg.addUrlPatterns("/actuator/*");
        reg.setName("actuatorSecurityFilter");
        reg.setOrder(1);
        return reg;
    }
}
