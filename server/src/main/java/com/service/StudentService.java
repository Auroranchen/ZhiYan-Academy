package com.service;

import com.mapper.StudentMapper;
import com.model.Student;
import com.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 学生业务逻辑层
 */
@Service
public class StudentService {

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private PasswordService passwordService;

    /**
     * 分页查询学生列表（支持搜索）
     * 
     * @param keyword  搜索关键字（学号或姓名）
     * @param classid  班级ID
     * @param majorid  专业ID
     * @param page     页码
     * @param pageSize 每页数量
     * @return 学生列表
     */
    public List<Student> getList(String keyword, Integer classid, Integer majorid, Integer page, Integer pageSize) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;
        // 处理空字符串
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        return studentMapper.findList(keyword, classid, majorid, offset, pageSize);
    }

    /**
     * 获取学生总数（支持搜索条件）
     * 
     * @param keyword 搜索关键字
     * @param classid 班级ID
     * @param majorid 专业ID
     * @return 总数
     */
    public Long getCount(String keyword, Integer classid, Integer majorid) {
        // 处理空字符串
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        return studentMapper.count(keyword, classid, majorid);
    }

    /**
     * 根据ID获取学生
     * 
     * @param studentid 学生ID
     * @return 学生对象
     */
    public Student getById(Integer studentid) {
        if (studentid == null) {
            return null;
        }
        return studentMapper.findById(studentid);
    }

    /**
     * 添加学生
     * 
     * @param student 学生对象
     * @return 操作结果
     */
    public Result<Void> add(Student student) {
        // 参数校验：学号不能为空
        if (student.getStudentno() == null || student.getStudentno().trim().isEmpty()) {
            return Result.error("学号不能为空");
        }
        
        // 参数校验：姓名不能为空
        if (student.getStudentname() == null || student.getStudentname().trim().isEmpty()) {
            return Result.error("学生姓名不能为空");
        }
        
        // 去除首尾空格
        student.setStudentno(student.getStudentno().trim());
        student.setStudentname(student.getStudentname().trim());
        
        // 检查学号是否已存在
        Student existing = studentMapper.findByStudentno(student.getStudentno());
        if (existing != null) {
            return Result.error("学号已存在");
        }
        
        // 设置默认密码
        if (student.getPassword() == null || student.getPassword().trim().isEmpty()) {
            student.setPassword("123456");
        }
        student.setPassword(passwordService.encode(student.getPassword()));
        
        // 设置创建时间
        student.setCreatetime(new Date());
        
        // 插入数据
        int rows = studentMapper.insert(student);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("添加失败");
    }

    /**
     * 更新学生
     * 
     * @param student 学生对象
     * @return 操作结果
     */
    public Result<Void> update(Student student) {
        // 参数校验：学生ID不能为空
        if (student.getStudentid() == null) {
            return Result.error("学生ID不能为空");
        }
        
        // 参数校验：学号不能为空
        if (student.getStudentno() == null || student.getStudentno().trim().isEmpty()) {
            return Result.error("学号不能为空");
        }
        
        // 参数校验：姓名不能为空
        if (student.getStudentname() == null || student.getStudentname().trim().isEmpty()) {
            return Result.error("学生姓名不能为空");
        }
        
        // 检查学生是否存在
        Student existing = studentMapper.findCredentialById(student.getStudentid());
        if (existing == null) {
            return Result.error("学生不存在");
        }
        
        // 去除首尾空格
        student.setStudentno(student.getStudentno().trim());
        student.setStudentname(student.getStudentname().trim());
        student.setPassword(existing.getPassword());
        
        // 检查学号是否被其他学生使用
        Student other = studentMapper.findByStudentno(student.getStudentno());
        if (other != null && !other.getStudentid().equals(student.getStudentid())) {
            return Result.error("学号已存在");
        }
        
        // 更新数据
        int rows = studentMapper.update(student);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("更新失败");
    }

    /**
     * 删除学生
     * 
     * @param studentid 学生ID
     * @return 操作结果
     */
    public Result<Void> delete(Integer studentid) {
        // 参数校验
        if (studentid == null) {
            return Result.error("学生ID不能为空");
        }
        
        // 检查学生是否存在
        Student existing = studentMapper.findById(studentid);
        if (existing == null) {
            return Result.error("学生不存在");
        }
        
        // 删除数据
        int rows = studentMapper.delete(studentid);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("删除失败");
    }

    /**
     * 学生注册
     * 
     * @param student 学生对象
     * @return 操作结果
     */
    public Result<Void> register(Student student) {
        // 参数校验：学号不能为空
        if (student.getStudentno() == null || student.getStudentno().trim().isEmpty()) {
            return Result.error("学号不能为空");
        }
        
        // 参数校验：姓名不能为空
        if (student.getStudentname() == null || student.getStudentname().trim().isEmpty()) {
            return Result.error("学生姓名不能为空");
        }
        
        // 参数校验：密码不能为空
        if (student.getPassword() == null || student.getPassword().trim().isEmpty()) {
            return Result.error("密码不能为空");
        }
        
        // 参数校验：班级不能为空
        if (student.getClassid() == null) {
            return Result.error("班级不能为空");
        }
        
        // 去除首尾空格
        student.setStudentno(student.getStudentno().trim());
        student.setStudentname(student.getStudentname().trim());
        student.setPassword(passwordService.encode(student.getPassword()));
        
        // 检查学号是否已存在
        Student existing = studentMapper.findByStudentno(student.getStudentno());
        if (existing != null) {
            return Result.error("学号已存在");
        }
        
        // 设置创建时间
        student.setCreatetime(new Date());
        
        // 插入数据
        int rows = studentMapper.insert(student);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("注册失败");
    }

    /**
     * 学生登录验证
     * 
     * @param studentno 学号
     * @param password  密码
     * @return 登录成功返回学生对象，失败返回null
     */
    public Student login(String studentno, String password) {
        // 参数校验
        if (studentno == null || studentno.trim().isEmpty()) {
            return null;
        }
        if (password == null || password.trim().isEmpty()) {
            return null;
        }
        
        Student student = studentMapper.findByStudentno(studentno.trim());
        if (student == null || !passwordService.matches(password, student.getPassword())) return null;
        if (passwordService.needsMigration(student.getPassword())) {
            studentMapper.updatePassword(student.getStudentid(), passwordService.encode(password));
        }
        student.setPassword(null);
        return student;
    }

    /**
     * 更新学生个人信息
     * 
     * @param student 学生对象
     * @return 操作结果
     */
    public Result<Void> updateProfile(Student student) {
        // 参数校验：学生ID不能为空
        if (student.getStudentid() == null) {
            return Result.error("学生ID不能为空");
        }
        
        // 检查学生是否存在
        Student existing = studentMapper.findCredentialById(student.getStudentid());
        if (existing == null) {
            return Result.error("学生不存在");
        }
        
        // 更新个人信息（不包括学号和密码）
        existing.setStudentname(student.getStudentname());
        existing.setSex(student.getSex());
        existing.setPhone(student.getPhone());
        existing.setEmail(student.getEmail());
        existing.setAddress(student.getAddress());
        
        int rows = studentMapper.update(existing);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("更新失败");
    }

    /**
     * 修改学生密码
     * 
     * @param studentid   学生ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 操作结果
     */
    public Result<Void> updatePassword(Integer studentid, String oldPassword, String newPassword) {
        // 参数校验：学生ID不能为空
        if (studentid == null) {
            return Result.error("学生ID不能为空");
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
        
        // 查询学生
        Student student = studentMapper.findCredentialById(studentid);
        if (student == null) {
            return Result.error("学生不存在");
        }
        
        // 验证旧密码
        if (!passwordService.matches(oldPassword, student.getPassword())) {
            return Result.error("旧密码错误");
        }
        
        // 更新密码
        int rows = studentMapper.updatePassword(studentid, passwordService.encode(newPassword));
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("密码修改失败");
    }
}
