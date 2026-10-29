package com.controller;

import com.config.RequireRole;
import com.model.Student;
import com.response.Result;
import com.service.StudentService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生控制器
 * 处理学生的增删改查请求
 */
@RestController
@RequestMapping("/student")
public class StudentController {

    @Autowired
    private StudentService studentService;

    /**
     * 获取学生列表（分页、搜索）
     * 
     * @param keyword  搜索关键字（学号或姓名）
     * @param classid  班级ID
     * @param majorid  专业ID
     * @param page     页码，默认1
     * @param pageSize 每页数量，默认10
     * @return 学生列表
     */
    @GetMapping("/list")
    @RequireRole({"admin", "teacher"})
    public Result<List<Student>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer classid,
            @RequestParam(required = false) Integer majorid,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        // 查询列表
        List<Student> list = studentService.getList(keyword, classid, majorid, page, pageSize);
        // 查询总数
        Long total = studentService.getCount(keyword, classid, majorid);
        return Result.success(list, total);
    }

    /**
     * 获取学生详情
     * 
     * @param id 学生ID
     * @return 学生对象
     */
    @GetMapping("/{id}")
    @RequireRole({"admin", "teacher"})
    public Result<Student> getById(@PathVariable("id") Integer id) {
        Student student = studentService.getById(id);
        if (student == null) {
            return Result.error("学生不存在");
        }
        return Result.success(student);
    }

    /**
     * 添加学生
     * 
     * @param student 学生对象
     * @return 操作结果
     */
    @PostMapping("/add")
    @RequireRole("admin")
    public Result<Void> add(@RequestBody Student student) {
        return studentService.add(student);
    }

    /**
     * 更新学生
     * 
     * @param student 学生对象
     * @return 操作结果
     */
    @PutMapping("/update")
    @RequireRole("admin")
    public Result<Void> update(@RequestBody Student student) {
        return studentService.update(student);
    }

    /**
     * 删除学生
     * 
     * @param id 学生ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{id}")
    @RequireRole("admin")
    public Result<Void> delete(@PathVariable("id") Integer id) {
        return studentService.delete(id);
    }

    /**
     * 学生注册
     * 
     * @param student 学生对象
     * @return 注册结果
     */
    @PostMapping("/register")
    @RequireRole("admin")
    public Result<Void> register(@RequestBody Student student) {
        return studentService.register(student);
    }

    /**
     * 学生登录
     * 
     * @param params  包含studentno和password的参数
     * @param session HTTP会话
     * @return 登录结果
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params, HttpSession session) {
        return Result.error(410, "旧登录入口已停用，请使用 /auth/login");
    }

    /**
     * 学生退出登录
     * 
     * @param session HTTP会话
     * @return 退出结果
     */
    @PostMapping("/logout")
    @RequireRole("student")
    public Result<Void> logout(HttpSession session) {
        session.invalidate();
        return Result.success("退出成功", (Void) null);
    }

    /**
     * 获取当前登录学生信息
     * 
     * @param session HTTP会话
     * @return 学生信息
     */
    @GetMapping("/info")
    @RequireRole("student")
    public Result<Student> getInfo(HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        
        Student student = studentService.getById(studentid);
        if (student == null) {
            return Result.error(401, "请先登录");
        }
        
        // 不返回密码
        student.setPassword(null);
        return Result.success(student);
    }

    /**
     * 更新学生个人信息
     * 
     * @param student 学生对象
     * @param session HTTP会话
     * @return 操作结果
     */
    @PutMapping("/profile")
    @RequireRole("student")
    public Result<Void> updateProfile(@RequestBody Student student, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        
        student.setStudentid(studentid);
        return studentService.updateProfile(student);
    }

    /**
     * 修改学生密码
     * 
     * @param params  包含oldPassword和newPassword的参数
     * @param session HTTP会话
     * @return 操作结果
     */
    @PutMapping("/password")
    @RequireRole("student")
    public Result<Void> updatePassword(@RequestBody Map<String, String> params, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");
        
        return studentService.updatePassword(studentid, oldPassword, newPassword);
    }
}
