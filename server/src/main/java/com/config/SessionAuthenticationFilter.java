package com.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** 将现有业务 Session 身份桥接到 Spring SecurityContext。 */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            HttpSession session = request.getSession(false);
            String role = resolveRole(session);
            if (role != null) {
                String principal = resolvePrincipal(session, role);
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveRole(HttpSession session) {
        if (session == null) return null;
        Object role = session.getAttribute("role");
        if (role != null) return String.valueOf(role);
        if (session.getAttribute("adminid") != null) return "admin";
        if (session.getAttribute("teacherid") != null) return "teacher";
        if (session.getAttribute("studentid") != null) return "student";
        return null;
    }

    private String resolvePrincipal(HttpSession session, String role) {
        Object value = switch (role) {
            case "admin" -> session.getAttribute("adminid");
            case "teacher" -> session.getAttribute("teacherid");
            case "student" -> session.getAttribute("studentid");
            default -> null;
        };
        return role + ":" + (value == null ? "unknown" : value);
    }
}
