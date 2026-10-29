package com.controller;

import com.model.HomeworkComment;
import com.response.Result;
import com.service.HomeworkCommentService;
import com.service.ResourceAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.List;

/**
 * 作业评论控制器。
 *
 * 读取接口此前只要求登录，未校验调用者是否属于该作业所在课程，
 * 任意登录用户都能翻看其他班级的作业讨论，现已收敛到 canViewHomework。
 */
@RestController
@RequestMapping("/homeworkComment")
public class HomeworkCommentController {

    @Autowired
    private HomeworkCommentService homeworkCommentService;

    @Autowired
    private ResourceAccessService resourceAccess;

    /**
     * 获取作业评论列表
     */
    @GetMapping("/byHomework/{homeworkid}")
    public Result<List<HomeworkComment>> getByHomeworkid(@PathVariable("homeworkid") Integer homeworkid,
                                                         HttpSession session) {
        if (!resourceAccess.canViewHomework(homeworkid, session)) {
            return Result.error(403, "无权查看该作业的评论");
        }
        List<HomeworkComment> list = homeworkCommentService.getByHomeworkid(homeworkid);
        Long total = homeworkCommentService.getCountByHomeworkid(homeworkid);
        return Result.success(list, total);
    }

    /**
     * 获取评论详情
     */
    @GetMapping("/{id}")
    public Result<HomeworkComment> getById(@PathVariable("id") Integer id, HttpSession session) {
        HomeworkComment comment = homeworkCommentService.getById(id);
        if (comment == null) {
            return Result.error("评论不存在");
        }
        if (!resourceAccess.canViewHomework(comment.getHomeworkid(), session)) {
            return Result.error(403, "无权查看该评论");
        }
        return Result.success(comment);
    }

    /**
     * 添加评论（身份绑定会话，忽略客户端伪造的 studentid/teacherid）
     */
    @PostMapping("/add")
    public Result<Void> add(@RequestBody HomeworkComment comment, HttpSession session) {
        Integer studentid = resourceAccess.studentId(session);
        Integer teacherid = resourceAccess.teacherId(session);
        if (studentid == null && teacherid == null) {
            return Result.error(401, "请先登录");
        }
        if (!resourceAccess.canViewHomework(comment.getHomeworkid(), session)) {
            return Result.error(403, "无权在该作业下评论");
        }
        // 防伪造：强制以会话身份为准
        comment.setStudentid(studentid);
        comment.setTeacherid(teacherid);
        return homeworkCommentService.add(comment);
    }

    /**
     * 删除评论（仅作者本人或管理员）
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id, HttpSession session) {
        Integer studentid = resourceAccess.studentId(session);
        Integer teacherid = resourceAccess.teacherId(session);
        boolean admin = resourceAccess.isAdmin(session);
        if (studentid == null && teacherid == null && !admin) {
            return Result.error(401, "请先登录");
        }
        HomeworkComment existing = homeworkCommentService.getById(id);
        if (existing == null) {
            return Result.error("评论不存在");
        }
        boolean owner = (studentid != null && studentid.equals(existing.getStudentid()))
                || (teacherid != null && teacherid.equals(existing.getTeacherid()));
        // 任课教师应能清理自己作业下的违规评论
        boolean manager = resourceAccess.canManageHomework(existing.getHomeworkid(), session);
        if (!admin && !owner && !manager) {
            return Result.error(403, "无权删除该评论");
        }
        return homeworkCommentService.delete(id);
    }
}
