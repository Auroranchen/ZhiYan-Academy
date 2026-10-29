package com.service;

import com.mapper.AdminMapper;
import com.model.Admin;
import com.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 管理员业务逻辑层
 */
@Service
public class AdminService {

    @Autowired
    private AdminMapper adminMapper;

    @Autowired
    private PasswordService passwordService;

    /**
     * 管理员登录验证
     * 
     * @param username 用户名
     * @param password 密码
     * @return 登录成功返回管理员对象，失败返回null
     */
    public Admin login(String username, String password) {
        // 参数校验
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        if (password == null || password.trim().isEmpty()) {
            return null;
        }
        
        Admin admin = adminMapper.findByUsername(username.trim());
        if (admin == null || !passwordService.matches(password, admin.getPassword())) return null;
        if (passwordService.needsMigration(admin.getPassword())) {
            adminMapper.updatePassword(admin.getAdminid(), passwordService.encode(password));
        }
        admin.setPassword(null);
        return admin;
    }

    /**
     * 根据ID获取管理员信息
     * 
     * @param adminid 管理员ID
     * @return 管理员对象
     */
    public Admin getById(Integer adminid) {
        if (adminid == null) {
            return null;
        }
        return adminMapper.findById(adminid);
    }

    /**
     * 根据用户名获取管理员信息
     * 
     * @param username 用户名
     * @return 管理员对象
     */
    public Admin getByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        return adminMapper.findByUsername(username.trim());
    }

    /**
     * 修改管理员密码
     * 
     * @param adminid     管理员ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 操作结果
     */
    public Result<Void> updatePassword(Integer adminid, String oldPassword, String newPassword) {
        // 参数校验：管理员ID不能为空
        if (adminid == null) {
            return Result.error("管理员ID不能为空");
        }
        
        // 参数校验：旧密码不能为空
        if (oldPassword == null || oldPassword.trim().isEmpty()) {
            return Result.error("旧密码不能为空");
        }
        
        // 参数校验：新密码不能为空
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return Result.error("新密码不能为空");
        }
        
        // 参数校验：新密码长度
        if (newPassword.length() < 6) {
            return Result.error("新密码长度不能少于6位");
        }
        
        // 查询管理员
        Admin admin = adminMapper.findCredentialById(adminid);
        if (admin == null) {
            return Result.error("管理员不存在");
        }
        
        // 验证旧密码
        if (!passwordService.matches(oldPassword, admin.getPassword())) {
            return Result.error("旧密码错误");
        }
        
        // 更新密码
        int rows = adminMapper.updatePassword(adminid, passwordService.encode(newPassword));
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("密码修改失败");
    }
}
