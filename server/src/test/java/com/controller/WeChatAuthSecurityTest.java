package com.controller;

import com.config.RequireRole;
import com.dto.wechat.WeChatUnbindRequest;
import com.model.Student;
import com.service.StudentSessionService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WeChatAuthSecurityTest {
    @Test
    void bindingStatusAndUnbindRequireStudentRole() throws Exception {
        Method status = WeChatAuthController.class.getMethod("status", HttpSession.class);
        Method unbind = WeChatAuthController.class.getMethod(
                "unbind", WeChatUnbindRequest.class, HttpSession.class);

        assertStudentRole(status);
        assertStudentRole(unbind);
    }

    @Test
    void wechatLoginClearsAnyPreviousPrivilegedSessionAttributes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        session.setAttribute("adminid", 1);
        session.setAttribute("teacherid", 2);
        session.setAttribute("role", "admin");
        Student student = new Student();
        student.setStudentid(7);
        student.setStudentno("2023001001");
        student.setStudentname("学生");
        student.setClassid(3);

        Map<String, Object> data = new StudentSessionService().establish(student, request, session);

        assertNull(session.getAttribute("adminid"));
        assertNull(session.getAttribute("teacherid"));
        assertEquals("student", session.getAttribute("role"));
        assertEquals(7, session.getAttribute("studentid"));
        assertEquals("wechat", data.get("loginType"));
        assertFalse(data.containsKey("openid"));
    }

    private void assertStudentRole(Method method) {
        RequireRole guard = method.getAnnotation(RequireRole.class);
        assertNotNull(guard);
        assertEquals(1, guard.value().length);
        assertEquals("student", guard.value()[0]);
    }
}
