package com.controller;

import com.config.RequireRole;
import com.model.Teacher;
import com.response.Result;
import com.service.TeacherService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 教师控制器
 * 处理教师的增删改查请求
 */
@RestController
@RequestMapping("/teacher")
public class TeacherController {

    @Autowired
    private TeacherService teacherService;

    /**
     * 获取教师列表（分页、搜索）
     * 
     * @param keyword  搜索关键字（工号或姓名）
     * @param page     页码，默认1
     * @param pageSize 每页数量，默认10
     * @return 教师列表
     */
    @GetMapping("/list")
    @RequireRole("admin")
    public Result<List<Teacher>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        // 查询列表
        List<Teacher> list = teacherService.getList(keyword, page, pageSize);
        // 查询总数
        Long total = teacherService.getCount(keyword);
        return Result.success(list, total);
    }

    /**
     * 获取教师详情
     * 
     * @param id 教师ID
     * @return 教师对象
     */
    @GetMapping("/{id}")
    @RequireRole("admin")
    public Result<Teacher> getById(@PathVariable("id") Integer id) {
        Teacher teacher = teacherService.getById(id);
        if (teacher == null) {
            return Result.error("教师不存在");
        }
        return Result.success(teacher);
    }

    /**
     * 添加教师
     * 
     * @param teacher 教师对象
     * @return 操作结果
     */
    @PostMapping("/add")
    @RequireRole("admin")
    public Result<Void> add(@RequestBody Teacher teacher) {
        return teacherService.add(teacher);
    }

    /**
     * 更新教师
     * 
     * @param teacher 教师对象
     * @return 操作结果
     */
    @PutMapping("/update")
    @RequireRole("admin")
    public Result<Void> update(@RequestBody Teacher teacher) {
        return teacherService.update(teacher);
    }

    /**
     * 删除教师
     * 
     * @param id 教师ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{id}")
    @RequireRole("admin")
    public Result<Void> delete(@PathVariable("id") Integer id) {
        return teacherService.delete(id);
    }

    /**
     * 教师登录
     * 
     * @param params  包含teacherno和password的参数
     * @param session HTTP会话
     * @return 登录结果
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params, HttpSession session) {
        return Result.error(410, "旧登录入口已停用，请使用 /auth/login");
    }

    /**
     * 教师退出登录
     * 
     * @param session HTTP会话
     * @return 退出结果
     */
    @PostMapping("/logout")
    @RequireRole("teacher")
    public Result<Void> logout(HttpSession session) {
        session.invalidate();
        return Result.success("退出成功", (Void) null);
    }

    /**
     * 获取当前登录教师信息
     * 
     * @param session HTTP会话
     * @return 教师信息
     */
    @GetMapping("/info")
    @RequireRole("teacher")
    public Result<Teacher> getInfo(HttpSession session) {
        Integer teacherid = (Integer) session.getAttribute("teacherid");
        if (teacherid == null) {
            return Result.error(401, "请先登录");
        }
        
        Teacher teacher = teacherService.getById(teacherid);
        if (teacher == null) {
            return Result.error(401, "请先登录");
        }
        
        // 不返回密码
        teacher.setPassword(null);
        return Result.success(teacher);
    }

    /**
     * 更新教师个人信息
     * 
     * @param teacher 教师对象
     * @param session HTTP会话
     * @return 操作结果
     */
    @PutMapping("/profile")
    @RequireRole("teacher")
    public Result<Void> updateProfile(@RequestBody Teacher teacher, HttpSession session) {
        Integer teacherid = (Integer) session.getAttribute("teacherid");
        if (teacherid == null) {
            return Result.error(401, "请先登录");
        }
        
        teacher.setTeacherid(teacherid);
        return teacherService.updateProfile(teacher);
    }

    /**
     * 修改教师密码
     * 
     * @param params  包含oldPassword和newPassword的参数
     * @param session HTTP会话
     * @return 操作结果
     */
    @PutMapping("/password")
    @RequireRole("teacher")
    public Result<Void> updatePassword(@RequestBody Map<String, String> params, HttpSession session) {
        Integer teacherid = (Integer) session.getAttribute("teacherid");
        if (teacherid == null) {
            return Result.error(401, "请先登录");
        }
        
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");
        
        return teacherService.updatePassword(teacherid, oldPassword, newPassword);
    }
}
