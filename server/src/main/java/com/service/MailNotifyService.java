package com.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 邮件提醒服务。
 * 异步发送，发送失败只记日志，绝不影响业务流程（如入驻申请的提交）。
 * 需在 application.yml 中配置 spring.mail（QQ 邮箱使用 SMTP 授权码）与 app.notify.mail。
 */
@Service
public class MailNotifyService {

    private static final Logger logger = LoggerFactory.getLogger(MailNotifyService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.notify.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.notify.mail.to:}")
    private String to;

    @Value("${spring.mail.username:}")
    private String from;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    /** 单线程守护线程池：邮件量极小，顺序发送即可 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mail-notify");
        t.setDaemon(true);
        return t;
    });

    /** 异步发送通知邮件（带主题与正文），不抛出任何异常 */
    public void sendAsync(String subject, String text) {
        if (!enabled || to == null || to.isBlank()) return;
        if (mailSender == null || from == null || from.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
            logger.warn("邮件通知未生效：请在 application.yml 的 spring.mail.password 中填入 QQ 邮箱 SMTP 授权码");
            return;
        }
        executor.submit(() -> {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(from);
                msg.setTo(to);
                msg.setSubject(subject);
                msg.setText(text);
                mailSender.send(msg);
                logger.info("通知邮件已发送至 {}：{}", to, subject);
            } catch (Exception e) {
                logger.warn("通知邮件发送失败：{}", e.getMessage());
            }
        });
    }
}
