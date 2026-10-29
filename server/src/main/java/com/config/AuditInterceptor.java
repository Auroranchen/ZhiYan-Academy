package com.config;

import com.model.AuditLog;
import com.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Date;

/**
 * 操作审计拦截器。
 * 仅记录会改变数据的请求（POST/PUT/DELETE）以及登录/退出，
 * 自动从会话中识别操作者，记录模块、动作、IP、耗时与结果。
 */
@Component
public class AuditInterceptor implements HandlerInterceptor {

    private static final String START_TIME = "__audit_start_time";

    @Autowired
    private AuditLogService auditLogService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            boolean isLogin = uri.endsWith("/login");
            boolean isLogout = uri.endsWith("/logout");
            boolean mutating = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);

            // 仅审计变更类操作与登录/退出，跳过审计日志自身查询
            if (!mutating && !isLogin && !isLogout) {
                return;
            }
            if (uri.contains("/audit/")) {
                return;
            }

            Long start = (Long) request.getAttribute(START_TIME);
            int cost = start != null ? (int) (System.currentTimeMillis() - start) : 0;

            AuditLog log = new AuditLog();
            fillUser(request, log, isLogin);
            log.setAction(resolveAction(method, isLogin, isLogout, uri));
            log.setModule(resolveModule(uri));
            log.setTarget(resolveTarget(uri));
            log.setMethod(method);
            log.setUri(truncate(uri, 280));
            log.setIp(resolveIp(request));
            String qs = request.getQueryString();
            log.setDetail(qs != null ? truncate(qs, 480) : null);
            int httpStatus = response.getStatus();
            log.setStatus((httpStatus >= 200 && httpStatus < 400 && ex == null) ? 1 : 0);
            log.setCostMs(cost);
            log.setCreateTime(new Date());

            auditLogService.saveAsync(log);
        } catch (Exception ignored) {
            // 审计绝不影响业务
        }
    }

    private void fillUser(HttpServletRequest request, AuditLog log, boolean isLogin) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object role = session.getAttribute("role");
            if (role != null) {
                log.setUserType(role.toString());
            }
            Object sid = session.getAttribute("studentid");
            Object tid = session.getAttribute("teacherid");
            Object aid = session.getAttribute("adminid");
            if (sid != null) {
                log.setUserId((Integer) sid);
                log.setUserName(strAttr(session, "studentname"));
                if (log.getUserType() == null) log.setUserType("student");
            } else if (tid != null) {
                log.setUserId((Integer) tid);
                log.setUserName(strAttr(session, "teachername"));
                if (log.getUserType() == null) log.setUserType("teacher");
            } else if (aid != null) {
                log.setUserId((Integer) aid);
                log.setUserName(strAttr(session, "username"));
                if (log.getUserType() == null) log.setUserType("admin");
            }
        }
        if (log.getUserType() == null) {
            log.setUserType("anon");
        }
        if (log.getUserName() == null && isLogin) {
            log.setUserName("(登录尝试)");
        }
    }

    private String strAttr(HttpSession session, String key) {
        Object v = session.getAttribute(key);
        return v != null ? v.toString() : null;
    }

    private String resolveAction(String method, boolean isLogin, boolean isLogout, String uri) {
        if (isLogin) return "LOGIN";
        if (isLogout) return "LOGOUT";
        if (uri.contains("export")) return "EXPORT";
        if (uri.contains("grade") || uri.contains("batchGrade")) return "GRADE";
        switch (method.toUpperCase()) {
            case "POST":
                if (uri.contains("/update") || uri.contains("/edit")) return "UPDATE";
                if (uri.contains("/delete") || uri.contains("/remove")) return "DELETE";
                return "CREATE";
            case "PUT":
                return "UPDATE";
            case "DELETE":
                return "DELETE";
            default:
                return "OTHER";
        }
    }

    private String resolveModule(String uri) {
        // /ZhiYanAcademy/{module}/...
        String path = uri;
        int ctx = path.indexOf("/ZhiYanAcademy");
        if (ctx >= 0) {
            path = path.substring(ctx + "/ZhiYanAcademy".length());
        }
        if (path.startsWith("/")) path = path.substring(1);
        int slash = path.indexOf('/');
        String seg = slash > 0 ? path.substring(0, slash) : path;
        return seg.isEmpty() ? "system" : seg;
    }

    private String resolveTarget(String uri) {
        int ctx = uri.indexOf("/ZhiYanAcademy");
        String p = ctx >= 0 ? uri.substring(ctx + "/ZhiYanAcademy".length()) : uri;
        return truncate(p, 240);
    }

    private String resolveIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
