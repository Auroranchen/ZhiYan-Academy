package com.service;

import com.mapper.HomeworkMapper;
import com.mapper.StudentHomeworkMapper;
import com.model.Homework;
import com.model.StudentHomework;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 作业辅导对话的策略解析器。
 *
 * <p>把「这次对话该不该加固」这个判断从控制器里彻底剥离出来，并且**只依赖服务端状态**：
 * 客户端只能说「我在做哪个作业」，说什么话、带什么 systemPrompt 都不影响判定。
 *
 * <p>设计范式照抄 {@code AiExplainService.getExplain}：客户端给出资源 ID →
 * 服务端查库 → 服务端决定策略。
 */
@Service
public class HomeworkChatPolicyResolver {

    @Autowired(required = false)
    private StudentHomeworkMapper studentHomeworkMapper;

    @Autowired(required = false)
    private HomeworkMapper homeworkMapper;

    /**
     * 解析结果。
     *
     * @param homeworkId     请求的作业 ID
     * @param courseId       作业所属课程，用于追加课程资料检索
     * @param restricted     true 时必须启用作业辅导加固
     * @param rejectionReason 非 null 表示请求应被拒绝，值是给前端的提示
     */
    public record HomeworkChatPolicy(Integer homeworkId, Integer courseId, boolean restricted,
                                     String rejectionReason) {

        static HomeworkChatPolicy rejected(String reason) {
            return new HomeworkChatPolicy(null, null, true, reason);
        }

        public boolean allowed() {
            return rejectionReason == null;
        }
    }

    /**
     * 依据服务端状态判定本次对话的策略。
     *
     * <p>**fail-closed**：任何无法确认「已提交」的情况（缺 ID、查不到作业、没有提交行）
     * 都落到受限分支，而不是放行。
     *
     * @param homeworkId 客户端指定的作业 ID，可为 null
     * @param studentId  当前登录学生 ID
     */
    public HomeworkChatPolicy resolve(Integer homeworkId, Integer studentId) {
        if (homeworkId == null) {
            return HomeworkChatPolicy.rejected("缺少 homeworkId：作业辅导对话必须指明是哪份作业");
        }
        if (studentId == null) {
            return HomeworkChatPolicy.rejected("作业辅导对话仅对学生开放");
        }
        if (homeworkMapper == null || studentHomeworkMapper == null) {
            return HomeworkChatPolicy.rejected("服务暂不可用，请稍后再试");
        }

        Homework homework = homeworkMapper.findById(homeworkId);
        if (homework == null) {
            return HomeworkChatPolicy.rejected("作业不存在");
        }

        // 未提交 = 这一行不存在，findByHomeworkAndStudent 返回 null
        StudentHomework submission = studentHomeworkMapper
                .findByHomeworkAndStudent(homeworkId, studentId);

        return new HomeworkChatPolicy(
                homeworkId,
                homework.getCourseid(),
                AiAccessPolicy.useRestrictedChat(submission),
                null);
    }
}
