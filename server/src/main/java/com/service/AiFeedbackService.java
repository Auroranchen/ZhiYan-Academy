package com.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AiFeedbackService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void save(String userRole, Integer userId, String messageKey, String scene,
                     int rating, String reason, String question, String answer) {
        if (userRole == null || userId == null) throw new IllegalArgumentException("用户身份无效");
        if (messageKey == null || !messageKey.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new IllegalArgumentException("消息标识无效");
        }
        if (rating != 1 && rating != -1) throw new IllegalArgumentException("反馈值无效");
        String safeScene = truncate(scene == null || scene.isBlank() ? "assistant.chat" : scene, 64);
        String safeReason = truncate(normalize(reason), 200);
        String questionPreview = truncate(normalize(question), 500);
        String answerPreview = truncate(normalize(answer), 500);
        jdbcTemplate.update(
                "INSERT INTO ai_feedback(user_role,user_id,message_key,scene,rating,reason," +
                        "question_preview,answer_preview,create_time,update_time) " +
                        "VALUES (?,?,?,?,?,?,?,?,NOW(),NOW()) ON DUPLICATE KEY UPDATE " +
                        "rating=VALUES(rating),reason=VALUES(reason),update_time=NOW()",
                userRole, userId, messageKey, safeScene, rating, safeReason,
                questionPreview, answerPreview);
    }

    private String normalize(String value) {
        return value == null ? null : value.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
}
