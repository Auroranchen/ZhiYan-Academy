package com.controller;

import com.response.Result;
import com.service.AiFeedbackService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ai-feedback")
public class AiFeedbackController {

    @Autowired
    private AiFeedbackService feedbackService;

    @PostMapping
    public Result<Void> save(@RequestBody Map<String, Object> body, HttpSession session) {
        Identity identity = identity(session);
        if (identity == null) return Result.error(401, "请先登录");
        try {
            Object rawRating = body.get("rating");
            int rating = rawRating instanceof Number ? ((Number) rawRating).intValue()
                    : Integer.parseInt(String.valueOf(rawRating));
            feedbackService.save(identity.role(), identity.id(), string(body.get("messageKey")),
                    string(body.get("scene")), rating, string(body.get("reason")),
                    string(body.get("question")), string(body.get("answer")));
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    private Identity identity(HttpSession session) {
        if (session == null) return null;
        if (session.getAttribute("studentid") instanceof Number id) return new Identity("student", id.intValue());
        if (session.getAttribute("teacherid") instanceof Number id) return new Identity("teacher", id.intValue());
        if (session.getAttribute("adminid") instanceof Number id) return new Identity("admin", id.intValue());
        return null;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record Identity(String role, Integer id) {}
}
