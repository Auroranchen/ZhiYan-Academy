package com.controller;

import com.config.RequireRole;
import com.model.Admin;
import com.response.Result;
import com.service.AdminService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理员控制器
 * 处理管理员登录、退出、获取信息等请求
 */
@RestController
@RequestMapping("/admin")
@RequireRole("admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    /**
     * 管理员登录
     * 
     * @param params  包含username和password的参数
     * @param session HTTP会话
     * @return 登录结果
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params, HttpSession session) {
        return Result.error(410, "旧登录入口已停用，请使用 /auth/login");
    }

    /**
     * 管理员退出登录
     * 
     * @param session HTTP会话
     * @return 退出结果
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpSession session) {
        // 清除Session
        session.invalidate();
        return Result.success("退出成功", (Void) null);
    }

    /**
     * 获取当前登录管理员信息
     * 
     * @param session HTTP会话
     * @return 管理员信息
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> getInfo(HttpSession session) {
        // 获取Session中的管理员ID
        Integer adminid = (Integer) session.getAttribute("adminid");
        if (adminid == null) {
            return Result.error(401, "请先登录");
        }
        
        // 查询管理员信息
        Admin admin = adminService.getById(adminid);
        if (admin == null) {
            return Result.error(401, "请先登录");
        }
        
        // 返回管理员信息
        Map<String, Object> data = new HashMap<>();
        data.put("adminid", admin.getAdminid());
        data.put("username", admin.getUsername());
        data.put("realname", admin.getRealname());
        
        return Result.success(data);
    }

    /**
     * 修改管理员密码
     * 
     * @param params  包含oldPassword和newPassword的参数
     * @param session HTTP会话
     * @return 操作结果
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(@RequestBody Map<String, String> params, HttpSession session) {
        // 获取Session中的管理员ID
        Integer adminid = (Integer) session.getAttribute("adminid");
        if (adminid == null) {
            return Result.error(401, "请先登录");
        }
        
        // 获取参数
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");
        
        return adminService.updatePassword(adminid, oldPassword, newPassword);
    }
}
