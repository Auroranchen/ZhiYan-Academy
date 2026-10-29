package com.controller;

import com.config.RequireRole;
import com.response.Result;
import com.service.PeerReviewService;
import com.service.ResourceAccessService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 作业互评控制器。
 * 教师端：开启/重新分配互评、查看进度与汇总；学生端：评阅他人作业、查看收到的评价。
 *
 * 教师端接口一律先过 canManageHomework：此前只校验「是教师」，
 * 任何教师都能操作他人课程的互评，而进度与汇总更是任何登录用户都能直接读。
 */
@RestController
@RequestMapping("/peer")
public class PeerReviewController {

    @Autowired
    private PeerReviewService peerReviewService;

    @Autowired
    private ResourceAccessService resourceAccess;

    // ============== 教师端 ==============

    /** 开启 / 重新分配互评 */
    @PostMapping("/assign")
    @RequireRole({"teacher", "admin"})
    public Result<Map<String, Object>> assign(@RequestBody Map<String, Object> body, HttpSession session) {
        Integer homeworkid = toInt(body.get("homeworkid"));
        if (!resourceAccess.canManageHomework(homeworkid, session)) {
            return Result.error(403, "无权操作该作业的互评");
        }
        Integer teacherid = resourceAccess.teacherId(session);
        Integer reviewersPer = toInt(body.get("reviewersPer"));
        Integer maxScore = toInt(body.get("maxScore"));
        Integer anonymous = toInt(body.get("anonymous"));
        return peerReviewService.assign(homeworkid, teacherid, reviewersPer, maxScore, anonymous);
    }

    /** 互评配置与进度 */
    @GetMapping("/task/{homeworkid}")
    @RequireRole({"teacher", "admin"})
    public Result<Map<String, Object>> task(@PathVariable Integer homeworkid, HttpSession session) {
        if (!resourceAccess.canManageHomework(homeworkid, session)) {
            return Result.error(403, "无权查看该作业的互评");
        }
        return Result.success(peerReviewService.getTask(homeworkid));
    }

    /** 关闭互评 */
    @PostMapping("/close/{homeworkid}")
    @RequireRole({"teacher", "admin"})
    public Result<Void> close(@PathVariable Integer homeworkid, HttpSession session) {
        if (!resourceAccess.canManageHomework(homeworkid, session)) {
            return Result.error(403, "无权操作该作业的互评");
        }
        return peerReviewService.closeTask(homeworkid);
    }

    /** 互评汇总（每位学生收到的均分/条数） */
    @GetMapping("/summary/{homeworkid}")
    @RequireRole({"teacher", "admin"})
    public Result<List<Map<String, Object>>> summary(@PathVariable Integer homeworkid, HttpSession session) {
        if (!resourceAccess.canManageHomework(homeworkid, session)) {
            return Result.error(403, "无权查看该作业的互评汇总");
        }
        return Result.success(peerReviewService.summary(homeworkid));
    }

    /** 将同伴均分一键回填为参考成绩 */
    @PostMapping("/apply/{homeworkid}")
    @RequireRole({"teacher", "admin"})
    public Result<Map<String, Object>> apply(@PathVariable Integer homeworkid, HttpSession session) {
        if (!resourceAccess.canManageHomework(homeworkid, session)) {
            return Result.error(403, "无权回填该作业的成绩");
        }
        return peerReviewService.applyScores(homeworkid);
    }

    // ============== 学生端 ==============
    // 以下接口均以会话中的 studentid 为查询条件，天然只能取到本人数据

    /** 我的待评任务 */
    @GetMapping("/todo")
    @RequireRole({"student"})
    public Result<List<Map<String, Object>>> todo(HttpSession session) {
        Integer studentid = resourceAccess.studentId(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(peerReviewService.myTasks(studentid, 0));
    }

    /** 我的全部互评任务（含已完成） */
    @GetMapping("/mine")
    @RequireRole({"student"})
    public Result<List<Map<String, Object>>> mine(HttpSession session) {
        Integer studentid = resourceAccess.studentId(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(peerReviewService.myTasks(studentid, null));
    }

    /** 评阅详情（含被评作业内容），Service 内按 reviewerid 二次校验 */
    @GetMapping("/detail/{id}")
    @RequireRole({"student"})
    public Result<Map<String, Object>> detail(@PathVariable Integer id, HttpSession session) {
        Integer studentid = resourceAccess.studentId(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        return peerReviewService.detail(id, studentid);
    }

    /** 提交互评打分 */
    @PostMapping("/submit")
    @RequireRole({"student"})
    public Result<Void> submit(@RequestBody Map<String, Object> body, HttpSession session) {
        Integer studentid = resourceAccess.studentId(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        Integer id = toInt(body.get("id"));
        BigDecimal score = body.get("score") != null ? new BigDecimal(body.get("score").toString()) : null;
        String comment = body.get("comment") != null ? body.get("comment").toString() : null;
        return peerReviewService.submit(id, studentid, score, comment);
    }

    /** 我收到的互评 */
    @GetMapping("/received")
    @RequireRole({"student"})
    public Result<List<Map<String, Object>>> received(HttpSession session) {
        Integer studentid = resourceAccess.studentId(session);
        if (studentid == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(peerReviewService.received(studentid));
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
