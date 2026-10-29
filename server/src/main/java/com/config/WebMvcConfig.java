package com.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置类
 * 配置静态资源映射、审计拦截器等
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuditInterceptor auditInterceptor;

    @Autowired
    private SessionAuthInterceptor sessionAuthInterceptor;

    @Autowired
    private AiRateLimitInterceptor aiRateLimitInterceptor;

    /**
     * 注册操作审计拦截器，拦截全部接口（拦截器内部仅记录变更类操作）。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessionAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/wechat/auth/login",
                        "/wechat/auth/bind",
                        "/captcha/**",
                        "/public/**",
                        "/enroll/apply",
                        "/enroll/upload",
                        "/avatar/get/**",
                        "/actuator/**",
                        "/error"
                )
                .order(0);
        registry.addInterceptor(aiRateLimitInterceptor)
                .addPathPatterns(
                        "/ai/**",
                        "/agent/**",
                        "/asr/**",
                        "/ai-insight/**",
                        "/profile/chat",
                        "/profile/evaluate",
                        "/profile/resource-evaluate",
                        "/learnpath/generate"
                )
                .order(1);
        registry.addInterceptor(auditInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/upload/**")
                .order(2);
    }

}
