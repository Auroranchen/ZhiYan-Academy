package com.controller;

import com.model.WrongQuestion;
import com.response.Result;
import com.service.GamificationService;
import com.service.WrongBookService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 错题本 + 知识点掌握画像控制器（学生端）
 */
@RestController
@RequestMapping("/wrongbook")
public class WrongBookController {

    @Autowired
    private WrongBookService wrongBookService;

    @Autowired
    private GamificationService gamificationService;

    /**
     * 手动归集错题（重新扫描全部已提交在线作业）。
     */
    @PostMapping("/collect")
    public Result<Integer> collect(HttpSession session) {
        Integer studentid = currentStudent(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        int count = wrongBookService.collect(studentid);
        return Result.success("已归集 " + count + " 道错题", count);
    }

    /**
     * 错题列表（自动先归集一次，保证数据最新）。
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) Integer courseid,
            @RequestParam(required = false) Integer mastered,
            HttpSession session) {
        Integer studentid = currentStudent(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        wrongBookService.collect(studentid);
        List<WrongQuestion> list = wrongBookService.list(studentid, courseid, mastered);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("wrongCount", wrongBookService.countWrong(studentid));
        data.put("masteredCount", wrongBookService.countMastered(studentid));
        return Result.success(data);
    }

    /**
     * 知识点掌握画像（按课程正确率，雷达图）。
     */
    @GetMapping("/mastery")
    public Result<List<Map<String, Object>>> mastery(HttpSession session) {
        Integer studentid = currentStudent(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(wrongBookService.masteryByCourse(studentid));
    }

    /**
     * 掌握度热力图（课程 × 掌握度/待攻克/高频错题）。
     */
    @GetMapping("/heatmap")
    public Result<List<Map<String, Object>>> heatmap(HttpSession session) {
        Integer studentid = currentStudent(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        wrongBookService.collect(studentid);
        return Result.success(wrongBookService.heatmap(studentid));
    }

    /**
     * 标记错题为已掌握（奖励积分）。
     */
    @PostMapping("/master/{id}")
    public Result<Void> master(@PathVariable Long id, HttpSession session) {
        Integer studentid = currentStudent(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        boolean ok = wrongBookService.setMastered(id, studentid, 1);
        if (ok) {
            gamificationService.onMasterWrongQuestion(studentid);
            return Result.successMsg("已标记为掌握，继续加油！");
        }
        return Result.error("操作失败");
    }

    /**
     * 取消已掌握标记。
     */
    @PostMapping("/unmaster/{id}")
    public Result<Void> unmaster(@PathVariable Long id, HttpSession session) {
        Integer studentid = currentStudent(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        return wrongBookService.setMastered(id, studentid, 0) ? Result.success() : Result.error("操作失败");
    }

    /**
     * 移除错题。
     */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id, HttpSession session) {
        Integer studentid = currentStudent(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        return wrongBookService.remove(id, studentid) ? Result.success() : Result.error("删除失败");
    }

    private Integer currentStudent(HttpSession session) {
        Object sid = session.getAttribute("studentid");
        return sid != null ? (Integer) sid : null;
    }
}
