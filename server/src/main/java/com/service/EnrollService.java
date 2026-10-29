package com.service;

import com.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 高校入驻申请业务层。
 * 使用 JdbcTemplate 自动建表并存储申请，避免额外的手工 DDL 步骤。
 */
@Service
public class EnrollService {

    private static final Logger logger = LoggerFactory.getLogger(EnrollService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MailNotifyService mailNotifyService;

    public Result<Void> apply(String school, String name, String role, String phone, String remark,
                              String attachment, String attachmentName) {
        if (school == null || school.trim().isEmpty()) return Result.error("请填写学校 / 机构名称");
        if (name == null || name.trim().isEmpty()) return Result.error("请填写联系人");
        if (phone == null || !phone.trim().matches("^1[3-9]\\d{9}$")) return Result.error("请输入有效的手机号");
        try {
            jdbcTemplate.update(
                "INSERT INTO enroll_application(school, contact_name, contact_role, phone, remark, attachment, attachment_name, status, create_time) " +
                "VALUES(?,?,?,?,?,?,?,0,?)",
                school.trim(),
                name.trim(),
                role != null ? role.trim() : null,
                phone.trim(),
                remark != null ? remark.trim() : null,
                attachment,
                attachmentName,
                new Date()
            );
            notifyByMail(school.trim(), name.trim(), role, phone.trim(), remark, attachmentName);
            return Result.success();
        } catch (Exception e) {
            logger.error("保存入驻申请失败: {}", e.getMessage());
            return Result.error("提交失败，请稍后重试");
        }
    }

    /** 提交成功后异步发邮件提醒运营方，失败不影响业务 */
    private void notifyByMail(String school, String name, String role, String phone, String remark, String attachmentName) {
        String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String text = "智枢学堂收到一份新的高校入驻申请：\n\n" +
                "学校 / 机构：" + school + "\n" +
                "联系人：" + name + (role != null && !role.trim().isEmpty() ? "（" + role.trim() + "）" : "") + "\n" +
                "手机号：" + phone + "\n" +
                "需求说明：" + (remark != null && !remark.trim().isEmpty() ? remark.trim() : "（未填写）") + "\n" +
                "附件文档：" + (attachmentName != null && !attachmentName.isEmpty() ? attachmentName + "（请登录管理员后台下载）" : "（无）") + "\n" +
                "提交时间：" + time + "\n\n" +
                "请尽快登录管理员后台查看并跟进。";
        mailNotifyService.sendAsync("【智枢学堂】新的高校入驻申请：" + school, text);
    }

    /** 按 ID 查询单条申请（用于附件下载） */
    public Map<String, Object> getById(Integer id) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, school, attachment, attachment_name AS attachmentName FROM enroll_application WHERE id=?", id);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    public List<Map<String, Object>> list() {
        try {
            return jdbcTemplate.queryForList(
                "SELECT id, school, contact_name AS contactName, contact_role AS contactRole, " +
                "phone, remark, attachment, attachment_name AS attachmentName, status, create_time AS createTime " +
                "FROM enroll_application ORDER BY id DESC"
            );
        } catch (Exception e) {
            logger.warn("查询入驻申请失败: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 更新申请状态。仅允许已有列 status：0 待审 / 1 已处理 / 2 驳回。
     */
    public Result<Void> updateStatus(Integer id, Integer status) {
        if (id == null || id <= 0) return Result.error("无效的申请 ID");
        if (status == null || (status != 0 && status != 1 && status != 2)) {
            return Result.error("状态仅支持：0待审 / 1已处理 / 2驳回");
        }
        try {
            int updated = jdbcTemplate.update(
                "UPDATE enroll_application SET status=? WHERE id=?", status, id);
            if (updated == 0) return Result.error(404, "申请不存在");
            return Result.success();
        } catch (Exception e) {
            logger.error("更新入驻申请状态失败: {}", e.getMessage());
            return Result.error("更新失败，请稍后重试");
        }
    }
}
