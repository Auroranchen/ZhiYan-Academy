package com.service;

import com.mapper.CourseMapper;
import com.mapper.TeacherMapper;
import com.model.Teacher;
import com.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 教师业务逻辑层
 */
@Service
public class TeacherService {

    @Autowired
    private TeacherMapper teacherMapper;

    @Autowired
    private PasswordService passwordService;

    /**
     * 分页查询教师列表（支持搜索）
     * 
     * @param keyword  搜索关键字（工号或姓名）
     * @param page     页码
     * @param pageSize 每页数量
     * @return 教师列表
     */
    public List<Teacher> getList(String keyword, Integer page, Integer pageSize) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;
        // 处理空字符串
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        return teacherMapper.findList(keyword, offset, pageSize);
    }

    /**
     * 获取教师总数（支持搜索条件）
     * 
     * @param keyword 搜索关键字
     * @return 总数
     */
    public Long getCount(String keyword) {
        // 处理空字符串
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        return teacherMapper.count(keyword);
    }

    /**
     * 根据ID获取教师
     * 
     * @param teacherid 教师ID
     * @return 教师对象
     */
    public Teacher getById(Integer teacherid) {
        if (teacherid == null) {
            return null;
        }
        return teacherMapper.findById(teacherid);
    }

    /**
     * 添加教师
     * 
     * @param teacher 教师对象
     * @return 操作结果
     */
    public Result<Void> add(Teacher teacher) {
        // 参数校验：工号不能为空
        if (teacher.getTeacherno() == null || teacher.getTeacherno().trim().isEmpty()) {
            return Result.error("工号不能为空");
        }
        
        // 参数校验：姓名不能为空
        if (teacher.getTeachername() == null || teacher.getTeachername().trim().isEmpty()) {
            return Result.error("教师姓名不能为空");
        }
        
        // 去除首尾空格
        teacher.setTeacherno(teacher.getTeacherno().trim());
        teacher.setTeachername(teacher.getTeachername().trim());
        
        // 检查工号是否已存在
        Teacher existing = teacherMapper.findByTeacherno(teacher.getTeacherno());
        if (existing != null) {
            return Result.error("工号已存在");
        }
        
        // 设置默认密码
        if (teacher.getPassword() == null || teacher.getPassword().trim().isEmpty()) {
            teacher.setPassword("123456");
        }
        teacher.setPassword(passwordService.encode(teacher.getPassword()));
        
        // 设置创建时间
        teacher.setCreatetime(new Date());
        
        // 插入数据
        int rows = teacherMapper.insert(teacher);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("添加失败");
    }

    /**
     * 更新教师
     * 
     * @param teacher 教师对象
     * @return 操作结果
     */
    public Result<Void> update(Teacher teacher) {
        // 参数校验：教师ID不能为空
        if (teacher.getTeacherid() == null) {
            return Result.error("教师ID不能为空");
        }
        
        // 参数校验：工号不能为空
        if (teacher.getTeacherno() == null || teacher.getTeacherno().trim().isEmpty()) {
            return Result.error("工号不能为空");
        }
        
        // 参数校验：姓名不能为空
        if (teacher.getTeachername() == null || teacher.getTeachername().trim().isEmpty()) {
            return Result.error("教师姓名不能为空");
        }
        
        // 检查教师是否存在
        Teacher existing = teacherMapper.findCredentialById(teacher.getTeacherid());
        if (existing == null) {
            return Result.error("教师不存在");
        }
        
        // 去除首尾空格
        teacher.setTeacherno(teacher.getTeacherno().trim());
        teacher.setTeachername(teacher.getTeachername().trim());
        teacher.setPassword(existing.getPassword());
        
        // 检查工号是否被其他教师使用
        Teacher other = teacherMapper.findByTeacherno(teacher.getTeacherno());
        if (other != null && !other.getTeacherid().equals(teacher.getTeacherid())) {
            return Result.error("工号已存在");
        }
        
        // 更新数据
        int rows = teacherMapper.update(teacher);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("更新失败");
    }

    /**
     * 删除教师
     * 
     * @param teacherid 教师ID
     * @return 操作结果
     */
    public Result<Void> delete(Integer teacherid) {
        // 参数校验
        if (teacherid == null) {
            return Result.error("教师ID不能为空");
        }
        
        // 检查教师是否存在
        Teacher existing = teacherMapper.findById(teacherid);
        if (existing == null) {
            return Result.error("教师不存在");
        }
        
        // 删除数据
        int rows = teacherMapper.delete(teacherid);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("删除失败");
    }

    /**
     * 教师登录验证
     * 
     * @param teacherno 工号
     * @param password  密码
     * @return 登录成功返回教师对象，失败返回null
     */
    public Teacher login(String teacherno, String password) {
        // 参数校验
        if (teacherno == null || teacherno.trim().isEmpty()) {
            return null;
        }
        if (password == null || password.trim().isEmpty()) {
            return null;
        }
        
        Teacher teacher = teacherMapper.findByTeacherno(teacherno.trim());
        if (teacher == null || !passwordService.matches(password, teacher.getPassword())) return null;
        if (passwordService.needsMigration(teacher.getPassword())) {
            teacherMapper.updatePassword(teacher.getTeacherid(), passwordService.encode(password));
        }
        teacher.setPassword(null);
        return teacher;
    }

    /**
     * 更新教师个人信息
     * 
     * @param teacher 教师对象
     * @return 操作结果
     */
    public Result<Void> updateProfile(Teacher teacher) {
        // 参数校验：教师ID不能为空
        if (teacher.getTeacherid() == null) {
            return Result.error("教师ID不能为空");
        }
        
        // 检查教师是否存在
        Teacher existing = teacherMapper.findCredentialById(teacher.getTeacherid());
        if (existing == null) {
            return Result.error("教师不存在");
        }
        
        // 更新个人信息（不包括工号和密码）
        existing.setTeachername(teacher.getTeachername());
        existing.setSex(teacher.getSex());
        existing.setPhone(teacher.getPhone());
        existing.setEmail(teacher.getEmail());
        existing.setTitle(teacher.getTitle());
        existing.setDepartment(teacher.getDepartment());
        
        int rows = teacherMapper.update(existing);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("更新失败");
    }

    /**
     * 修改教师密码
     * 
     * @param teacherid   教师ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 操作结果
     */
    public Result<Void> updatePassword(Integer teacherid, String oldPassword, String newPassword) {
        // 参数校验：教师ID不能为空
        if (teacherid == null) {
            return Result.error("教师ID不能为空");
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
        
        // 查询教师
        Teacher teacher = teacherMapper.findCredentialById(teacherid);
        if (teacher == null) {
            return Result.error("教师不存在");
        }
        
        // 验证旧密码
        if (!passwordService.matches(oldPassword, teacher.getPassword())) {
            return Result.error("旧密码错误");
        }
        
        // 更新密码
        int rows = teacherMapper.updatePassword(teacherid, passwordService.encode(newPassword));
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("密码修改失败");
    }
}
