package com.service;

import com.exception.WeChatAuthException;
import com.mapper.WeChatStudentBindingMapper;
import com.model.Student;
import com.model.WeChatStudentBinding;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@Transactional(readOnly = true)
public class WeChatAuthService {
    private final WeChatMiniProgramClient miniProgramClient;
    private final WeChatStudentBindingMapper bindingMapper;
    private final StudentService studentService;

    public WeChatAuthService(WeChatMiniProgramClient miniProgramClient,
                             WeChatStudentBindingMapper bindingMapper,
                             StudentService studentService) {
        this.miniProgramClient = miniProgramClient;
        this.bindingMapper = bindingMapper;
        this.studentService = studentService;
    }

    @Transactional
    public Student login(String code) {
        WeChatMiniProgramClient.WeChatIdentity identity = miniProgramClient.exchangeCode(code);
        WeChatStudentBinding binding = bindingMapper.findByOpenid(miniProgramClient.appId(), identity.openid());
        if (binding == null) {
            throw new WeChatAuthException(428, "该微信尚未关联学生账号，请先完成首次绑定");
        }
        binding.setUnionid(identity.unionid());
        binding.setLastlogintime(new Date());
        bindingMapper.touchLogin(binding);
        return requireStudent(binding.getStudentid());
    }

    @Transactional
    public Student bind(String code, String studentno, String password) {
        Student credential = studentService.login(studentno, password);
        if (credential == null) {
            throw new WeChatAuthException(401, "学号或密码错误");
        }
        WeChatMiniProgramClient.WeChatIdentity identity = miniProgramClient.exchangeCode(code);
        String appId = miniProgramClient.appId();
        WeChatStudentBinding byOpenid = bindingMapper.findByOpenid(appId, identity.openid());
        if (byOpenid != null && !credential.getStudentid().equals(byOpenid.getStudentid())) {
            throw new WeChatAuthException(409, "该微信已关联其他学生账号");
        }
        WeChatStudentBinding byStudent = bindingMapper.findByStudent(appId, credential.getStudentid());
        if (byStudent != null && !identity.openid().equals(byStudent.getOpenid())) {
            throw new WeChatAuthException(409, "该学生账号已关联其他微信，请先在原微信中解绑");
        }
        Date now = new Date();
        if (byOpenid == null && byStudent == null) {
            WeChatStudentBinding binding = new WeChatStudentBinding();
            binding.setStudentid(credential.getStudentid());
            binding.setAppid(appId);
            binding.setOpenid(identity.openid());
            binding.setUnionid(identity.unionid());
            binding.setBindtime(now);
            binding.setLastlogintime(now);
            try {
                bindingMapper.insert(binding);
            } catch (DuplicateKeyException exception) {
                throw new WeChatAuthException(409, "微信或学生账号已被绑定，请刷新后重试");
            }
        } else {
            WeChatStudentBinding existing = byOpenid != null ? byOpenid : byStudent;
            existing.setUnionid(identity.unionid());
            existing.setLastlogintime(now);
            bindingMapper.touchLogin(existing);
        }
        return requireStudent(credential.getStudentid());
    }

    public boolean isBound(Integer studentid) {
        return bindingMapper.findByStudent(miniProgramClient.appId(), studentid) != null;
    }

    @Transactional
    public void unbind(Integer studentid, String password) {
        Student student = requireStudent(studentid);
        if (studentService.login(student.getStudentno(), password) == null) {
            throw new WeChatAuthException(401, "学生账号密码错误，无法解绑");
        }
        if (bindingMapper.deleteByStudent(miniProgramClient.appId(), studentid) == 0) {
            throw new WeChatAuthException(404, "当前学生账号尚未绑定微信");
        }
    }

    private Student requireStudent(Integer studentid) {
        Student student = studentService.getById(studentid);
        if (student == null) {
            throw new WeChatAuthException(404, "关联的学生账号不存在");
        }
        return student;
    }
}
