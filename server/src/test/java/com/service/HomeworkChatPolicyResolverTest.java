package com.service;

import com.mapper.HomeworkMapper;
import com.mapper.StudentHomeworkMapper;
import com.model.Homework;
import com.model.StudentHomework;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 作业辅导加固策略的服务端判定。
 *
 * <p>这个类的存在意义是回答一个问题：<b>调用方能不能自己把防线关掉？</b>
 * 改造前可以——{@code AIController} 靠读请求体里的 systemPrompt 是否包含
 * 「当前作业」/「作业辅导模式」来决定要不要加固，而那两个子串只有前端会拼，
 * 直接 curl 省略它们即可完全绕过。改造后判定只依赖服务端查库，
 * 所以这里重点覆盖「拿不到证据时必须落到受限分支」。
 */
class HomeworkChatPolicyResolverTest {

    private static final Integer STUDENT_ID = 42;
    private static final Integer HOMEWORK_ID = 7;

    private StudentHomeworkMapper studentHomeworkMapper;
    private HomeworkMapper homeworkMapper;
    private HomeworkChatPolicyResolver resolver;

    @BeforeEach
    void setUp() {
        studentHomeworkMapper = mock(StudentHomeworkMapper.class);
        homeworkMapper = mock(HomeworkMapper.class);
        resolver = new HomeworkChatPolicyResolver();
        ReflectionTestUtils.setField(resolver, "studentHomeworkMapper", studentHomeworkMapper);
        ReflectionTestUtils.setField(resolver, "homeworkMapper", homeworkMapper);
    }

    private Homework homeworkOf(Integer courseId) {
        Homework homework = new Homework();
        homework.setHomeworkid(HOMEWORK_ID);
        homework.setCourseid(courseId);
        return homework;
    }

    private StudentHomework submissionWithStatus(Integer status) {
        StudentHomework sh = new StudentHomework();
        sh.setId(1001);
        sh.setHomeworkid(HOMEWORK_ID);
        sh.setStudentid(STUDENT_ID);
        sh.setStatus(status);
        return sh;
    }

    @Test
    @DisplayName("缺少 homeworkId 时直接拒绝，而不是静默降级到无加固模式")
    void missingHomeworkIdIsRejectedNotSilentlyUnguarded() {
        HomeworkChatPolicyResolver.HomeworkChatPolicy policy = resolver.resolve(null, STUDENT_ID);

        assertThat(policy.allowed()).isFalse();
        assertThat(policy.restricted()).isTrue();
        assertThat(policy.rejectionReason()).contains("homeworkId");
    }

    @Test
    @DisplayName("非学生身份（拿不到 studentId）拒绝")
    void missingStudentIsRejected() {
        assertThat(resolver.resolve(HOMEWORK_ID, null).allowed()).isFalse();
    }

    @Test
    @DisplayName("作业不存在时拒绝")
    void unknownHomeworkIsRejected() {
        when(homeworkMapper.findById(HOMEWORK_ID)).thenReturn(null);

        assertThat(resolver.resolve(HOMEWORK_ID, STUDENT_ID).allowed()).isFalse();
    }

    @Test
    @DisplayName("没有提交行（=未提交）时必须受限")
    void absentSubmissionMeansRestricted() {
        when(homeworkMapper.findById(HOMEWORK_ID)).thenReturn(homeworkOf(3));
        when(studentHomeworkMapper.findByHomeworkAndStudent(HOMEWORK_ID, STUDENT_ID)).thenReturn(null);

        HomeworkChatPolicyResolver.HomeworkChatPolicy policy = resolver.resolve(HOMEWORK_ID, STUDENT_ID);

        assertThat(policy.allowed()).isTrue();
        assertThat(policy.restricted()).isTrue();
        assertThat(policy.courseId()).isEqualTo(3);
    }

    @Test
    @DisplayName("status=0（未提交）时受限")
    void statusZeroIsRestricted() {
        when(homeworkMapper.findById(HOMEWORK_ID)).thenReturn(homeworkOf(3));
        when(studentHomeworkMapper.findByHomeworkAndStudent(HOMEWORK_ID, STUDENT_ID))
                .thenReturn(submissionWithStatus(0));

        assertThat(resolver.resolve(HOMEWORK_ID, STUDENT_ID).restricted()).isTrue();
    }

    @Test
    @DisplayName("已提交（status>=1）时解除加固")
    void submittedLiftsTheGuard() {
        when(homeworkMapper.findById(HOMEWORK_ID)).thenReturn(homeworkOf(3));
        when(studentHomeworkMapper.findByHomeworkAndStudent(HOMEWORK_ID, STUDENT_ID))
                .thenReturn(submissionWithStatus(1));

        HomeworkChatPolicyResolver.HomeworkChatPolicy policy = resolver.resolve(HOMEWORK_ID, STUDENT_ID);

        assertThat(policy.allowed()).isTrue();
        assertThat(policy.restricted()).isFalse();
    }
}
