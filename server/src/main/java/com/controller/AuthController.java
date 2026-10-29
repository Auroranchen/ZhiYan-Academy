package com.controller;

import com.model.Admin;
import com.model.Student;
import com.model.Teacher;
import com.response.Result;
import com.service.AdminService;
import com.service.LoginSecurityService;
import com.service.SliderCaptchaService;
import com.service.StudentService;
import com.service.TeacherService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一登录入口。
 *
 * <p>一次请求即可完成「管理员 / 教师 / 学生」三种角色的判定，避免前端依次试探多个接口，
 * 同时在此集中实现 <b>登录失败锁定</b> 与 <b>IP 限流</b> 两项安全防护（状态存 Redis）。</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    /** 账号被锁定 */
    private static final int CODE_LOCKED = 423;
    /** 请求过于频繁 */
    private static final int CODE_TOO_MANY = 429;
    /** 滑动验证未通过 */
    private static final int CODE_CAPTCHA = 412;

    /**
     * 登录成功时会写入的全部身份属性。
     *
     * <p>同一浏览器会话内切换角色（如先登录教师、未退出再登录学生）时若不清空旧值，
     * 各控制器中「会话存在 teacherid / adminid 即视为该角色」的判断会把学生当成教师，
     * 造成越权。因此每次登录都必须整体清空后再写入本角色。</p>
     */
    private static final String[] IDENTITY_ATTRIBUTES = {
            "role",
            "adminid", "teacherid", "studentid",
            "username", "realname",
            "teacherno", "teachername",
            "studentno", "studentname",
            "classid"
    };

    @Autowired
    private AdminService adminService;
    @Autowired
    private TeacherService teacherService;
    @Autowired
    private StudentService studentService;
    @Autowired
    private LoginSecurityService loginSecurity;
    @Autowired
    private SliderCaptchaService sliderCaptcha;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params,
                                             HttpServletRequest request,
                                             HttpSession session) {
        String username = params.get("username");
        String password = params.get("password");

        if (username == null || username.trim().isEmpty()) {
            return Result.error("请输入账号");
        }
        if (password == null || password.trim().isEmpty()) {
            return Result.error("请输入密码");
        }
        username = username.trim();

        // 1) IP 限流，抵御暴力破解
        String ip = loginSecurity.resolveIp(request);
        if (!loginSecurity.allowIp(ip)) {
            return Result.error(CODE_TOO_MANY, "登录请求过于频繁，请稍后再试");
        }

        // 2) 滑动验证（一次性令牌，校验后即作废）
        if (!sliderCaptcha.consumeToken(params.get("captchaToken"))) {
            return Result.error(CODE_CAPTCHA, "请先完成滑动验证");
        }

        // 3) 账号锁定检查
        long lockRemain = loginSecurity.getLockRemainingSeconds(username);
        if (lockRemain > 0) {
            long minutes = (lockRemain + 59) / 60;
            return Result.error(CODE_LOCKED, "账号已被锁定，请于 " + minutes + " 分钟后再试");
        }

        // 4) 依次判定角色
        Admin admin = adminService.login(username, password);
        if (admin != null) {
            loginSecurity.clearFailure(username);
            beginSession(request, session, "admin");
            session.setAttribute("adminid", admin.getAdminid());
            session.setAttribute("username", admin.getUsername());
            session.setAttribute("realname", admin.getRealname());
            Map<String, Object> data = new HashMap<>();
            data.put("role", "admin");
            data.put("adminid", admin.getAdminid());
            data.put("username", admin.getUsername());
            data.put("realname", admin.getRealname());
            data.put("avatar", hasAvatar(admin.getAvatar()) ? "/avatar/get/admin/" + admin.getAdminid() : null);
            return Result.success("登录成功", data);
        }

        Teacher teacher = teacherService.login(username, password);
        if (teacher != null) {
            loginSecurity.clearFailure(username);
            beginSession(request, session, "teacher");
            session.setAttribute("teacherid", teacher.getTeacherid());
            session.setAttribute("teacherno", teacher.getTeacherno());
            session.setAttribute("teachername", teacher.getTeachername());
            Map<String, Object> data = new HashMap<>();
            data.put("role", "teacher");
            data.put("teacherid", teacher.getTeacherid());
            data.put("teacherno", teacher.getTeacherno());
            data.put("teachername", teacher.getTeachername());
            data.put("avatar", hasAvatar(teacher.getAvatar()) ? "/avatar/get/teacher/" + teacher.getTeacherid() : null);
            return Result.success("登录成功", data);
        }

        Student student = studentService.login(username, password);
        if (student != null) {
            loginSecurity.clearFailure(username);
            beginSession(request, session, "student");
            session.setAttribute("studentid", student.getStudentid());
            session.setAttribute("studentno", student.getStudentno());
            session.setAttribute("studentname", student.getStudentname());
            session.setAttribute("classid", student.getClassid());
            Map<String, Object> data = new HashMap<>();
            data.put("role", "student");
            data.put("studentid", student.getStudentid());
            data.put("studentno", student.getStudentno());
            data.put("studentname", student.getStudentname());
            data.put("classid", student.getClassid());
            data.put("classname", student.getClassname());
            data.put("majorname", student.getMajorname());
            data.put("avatar", hasAvatar(student.getAvatar()) ? "/avatar/get/student/" + student.getStudentid() : null);
            return Result.success("登录成功", data);
        }

        // 5) 全部失败：记录失败并提示剩余次数
        int remaining = loginSecurity.recordFailure(username);
        if (remaining <= 0) {
            return Result.error(CODE_LOCKED, "账号已被锁定，请于 " + loginSecurity.getLockMinutes() + " 分钟后再试");
        }
        return Result.error("账号或密码错误，还可尝试 " + remaining + " 次");
    }

    /**
     * 开启一个全新的登录身份：轮换会话 ID、清空上一次登录残留的身份属性，再写入角色。
     *
     * <p>清空是必须的——{@link #IDENTITY_ATTRIBUTES} 里的属性一旦残留，
     * 会让「会话存在该属性即视为该角色」的旧判断误判，从而越权。</p>
     *
     * @param request 当前请求，用于轮换会话 ID
     * @param session 登录会话
     * @param role    本次登录的角色：admin / teacher / student
     */
    private void beginSession(HttpServletRequest request, HttpSession session, String role) {
        rotateSessionId(request);
        for (String attribute : IDENTITY_ATTRIBUTES) {
            session.removeAttribute(attribute);
        }
        session.setAttribute("role", role);
    }

    private void rotateSessionId(HttpServletRequest request) {
        try {
            request.changeSessionId();
        } catch (IllegalStateException ignored) {
            // 容器尚未创建 Session 时由控制器参数创建；正常请求不会进入此分支。
        }
    }

    private boolean hasAvatar(String avatar) {
        return avatar != null && !avatar.isEmpty();
    }
}
