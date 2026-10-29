package com.service;

import com.mapper.HomeworkCommentMapper;
import com.model.HomeworkComment;
import com.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 作业评论业务逻辑层
 */
@Service
public class HomeworkCommentService {

    @Autowired
    private HomeworkCommentMapper homeworkCommentMapper;

    /**
     * 获取作业评论列表（包含回复）
     */
    public List<HomeworkComment> getByHomeworkid(Integer homeworkid) {
        List<HomeworkComment> comments = homeworkCommentMapper.findByHomeworkid(homeworkid);
        // 加载每条评论的回复
        for (HomeworkComment comment : comments) {
            List<HomeworkComment> replies = homeworkCommentMapper.findReplies(comment.getId());
            comment.setReplies(replies);
        }
        return comments;
    }

    /**
     * 获取评论详情
     */
    public HomeworkComment getById(Integer id) {
        return homeworkCommentMapper.findById(id);
    }

    /**
     * 添加评论
     */
    public Result<Void> add(HomeworkComment comment) {
        if (comment.getHomeworkid() == null) {
            return Result.error("作业ID不能为空");
        }
        if (comment.getContent() == null || comment.getContent().trim().isEmpty()) {
            return Result.error("评论内容不能为空");
        }
        if (comment.getStudentid() == null && comment.getTeacherid() == null) {
            return Result.error("评论者不能为空");
        }

        comment.setContent(comment.getContent().trim());
        if (comment.getParentid() == null) {
            comment.setParentid(0);
        }
        comment.setCreatetime(new Date());

        int rows = homeworkCommentMapper.insert(comment);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("添加失败");
    }

    /**
     * 删除评论（含回复）
     */
    public Result<Void> delete(Integer id) {
        if (id == null) {
            return Result.error("评论ID不能为空");
        }

        HomeworkComment comment = homeworkCommentMapper.findById(id);
        if (comment == null) {
            return Result.error("评论不存在");
        }

        // 先删子回复，避免孤儿记录
        List<HomeworkComment> replies = homeworkCommentMapper.findReplies(id);
        if (replies != null) {
            for (HomeworkComment reply : replies) {
                if (reply != null && reply.getId() != null) {
                    homeworkCommentMapper.delete(reply.getId());
                }
            }
        }

        int rows = homeworkCommentMapper.delete(id);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("删除失败");
    }

    /**
     * 获取评论数量
     */
    public Long getCountByHomeworkid(Integer homeworkid) {
        return homeworkCommentMapper.countByHomeworkid(homeworkid);
    }
}
