package com.service;

import com.model.Student;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/** 统一建立学生 Session，供账号登录与微信登录复用同一身份协议。 */
@Service
public class StudentSessionService {
    public Map<String, Object> establish(Student student, HttpServletRequest request, HttpSession session) {
        try {
            request.changeSessionId();
        } catch (IllegalStateException ignored) {
            // Session 已由控制器参数创建，容器不支持轮换时继续使用现有安全策略。
        }
        session.removeAttribute("adminid");
        session.removeAttribute("username");
        session.removeAttribute("realname");
        session.removeAttribute("teacherid");
        session.removeAttribute("teacherno");
        session.removeAttribute("teachername");
        session.setAttribute("studentid", student.getStudentid());
        session.setAttribute("studentno", student.getStudentno());
        session.setAttribute("studentname", student.getStudentname());
        session.setAttribute("classid", student.getClassid());
        session.setAttribute("role", "student");

        Map<String, Object> data = new HashMap<>();
        data.put("role", "student");
        data.put("studentid", student.getStudentid());
        data.put("studentno", student.getStudentno());
        data.put("studentname", student.getStudentname());
        data.put("classid", student.getClassid());
        data.put("classname", student.getClassname());
        data.put("majorname", student.getMajorname());
        data.put("avatar", hasText(student.getAvatar())
                ? "/avatar/get/student/" + student.getStudentid() : null);
        data.put("loginType", "wechat");
        return data;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
