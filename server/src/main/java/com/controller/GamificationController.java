package com.controller;

import com.response.Result;
import com.service.GamificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 积分 / 徽章 / 排行榜控制器（学生端）
 */
@RestController
@RequestMapping("/gamification")
public class GamificationController {

    @Autowired
    private GamificationService gamificationService;

    /**
     * 个人积分画像。
     */
    @GetMapping("/profile")
    public Result<Map<String, Object>> profile(HttpSession session) {
        Integer studentid = currentStudent(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(gamificationService.getProfile(studentid));
    }

    /**
     * 每日打卡。
     */
    @PostMapping("/checkin")
    public Result<Map<String, Object>> checkin(HttpSession session) {
        Integer studentid = currentStudent(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        Map<String, Object> r = gamificationService.checkin(studentid);
        Boolean already = (Boolean) r.get("already");
        String msg = (already != null && already) ? "今日已打卡" : "打卡成功 +" + r.get("awarded") + " 积分";
        return Result.success(msg, r);
    }

    /**
     * 排行榜：scope=class 仅本班，否则全校。
     */
    @GetMapping("/leaderboard")
    public Result<List<Map<String, Object>>> leaderboard(
            @RequestParam(defaultValue = "all") String scope,
            HttpSession session) {
        Integer classid = null;
        if ("class".equals(scope)) {
            Object cid = session.getAttribute("classid");
            classid = cid != null ? (Integer) cid : null;
        }
        return Result.success(gamificationService.leaderboard(classid, 20));
    }

    private Integer currentStudent(HttpSession session) {
        Object sid = session.getAttribute("studentid");
        return sid != null ? (Integer) sid : null;
    }
}
