package com.controller;

import com.config.RequireRole;
import com.model.StudentFavorite;
import com.response.Result;
import com.service.AiExplainService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiExplainController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiExplainController.class);

    @Autowired
    private AiExplainService aiExplainService;

    /**
     * 获取作业AI讲解（仅本人 / 教师 / 管理员）
     */
    @GetMapping("/explain/{studentHomeworkId}")
    @RequireRole({"student", "teacher", "admin"})
    public Result<List<Map<String, Object>>> getExplain(@PathVariable Integer studentHomeworkId,
                                                        HttpSession session) {
        Integer studentId = (Integer) session.getAttribute("studentid");
        boolean privileged = session.getAttribute("teacherid") != null
                || session.getAttribute("adminid") != null;
        try {
            List<Map<String, Object>> list = aiExplainService.getExplain(studentHomeworkId, studentId, privileged);
            if (list == null) {
                return Result.error("获取讲解失败：作业类型不支持或数据解析错误");
            }
            if (list.isEmpty()) {
                return Result.error("未能生成讲解，请稍后重试");
            }
            return Result.success(list);
        } catch (SecurityException se) {
            return Result.error(403, se.getMessage());
        } catch (Exception e) {
            log.error("获取 AI 讲解失败", e);
            return Result.error("获取讲解发生异常：" + e.getMessage());
        }
    }

    /**
     * 收藏题目讲解（studentId 以会话为准）
     */
    @PostMapping("/favorite")
    @RequireRole("student")
    public Result<Void> addFavorite(@RequestBody StudentFavorite favorite, HttpSession session) {
        Integer studentId = (Integer) session.getAttribute("studentid");
        if (studentId == null) return Result.error(401, "请先登录学生账号");
        favorite.setStudentId(studentId);
        boolean success = aiExplainService.addFavorite(favorite);
        return success ? Result.success() : Result.error("收藏失败");
    }

    /**
     * 当前学生的收藏列表
     */
    @GetMapping("/favorites")
    @RequireRole("student")
    public Result<List<StudentFavorite>> listFavorites(HttpSession session) {
        Integer studentId = (Integer) session.getAttribute("studentid");
        if (studentId == null) return Result.error(401, "请先登录学生账号");
        return Result.success(aiExplainService.listFavorites(studentId));
    }

    /** 取消收藏 */
    @PostMapping("/favorite/remove/{id}")
    @RequireRole("student")
    public Result<Void> removeFavorite(@PathVariable Integer id, HttpSession session) {
        Integer studentId = (Integer) session.getAttribute("studentid");
        if (studentId == null) return Result.error(401, "请先登录学生账号");
        boolean ok = aiExplainService.removeFavorite(studentId, id);
        return ok ? Result.success() : Result.error("取消收藏失败或不存在");
    }
}
