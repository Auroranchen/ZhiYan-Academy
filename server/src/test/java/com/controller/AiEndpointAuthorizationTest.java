package com.controller;

import com.config.RequireRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 端点的授权回归护栏。
 *
 * <p>背景：{@code /ai/diagnose} 此前没有角色限制，任何已登录学生都能拿到掩码后的
 * API Key、接口地址与模型名，而且每次调用都是一次**真实的计费**请求。
 * 同类的还有三个教师向的出题接口，也对学生开放。
 *
 * <p>这里逐个钉死这些端点应有的角色。写成精确断言而不是「所有端点都必须有注解」，
 * 是为了避免对 {@code AiInsightController} / {@code LearningAgentController} 里
 * 那些面向学生、刻意无角色限制的端点产生误报——那批端点的授权口径需要单独梳理，
 * 不该由这个测试顺手替它们做决定。
 */
class AiEndpointAuthorizationTest {

    @Test
    @DisplayName("/ai/diagnose 仅限管理员：它既泄露接口信息又产生真实计费调用")
    void diagnoseIsAdminOnly() {
        assertRoles(AIController.class, "diagnose", "admin");
    }

    @Test
    @DisplayName("教师向的出题/识别接口不得对学生开放")
    void questionAuthoringEndpointsAreTeacherFacing() {
        assertRoles(AIController.class, "parseQuestions", "teacher", "admin");
        assertRoles(AIController.class, "generateQuestions", "teacher", "admin");
        assertRoles(AIController.class, "generateQuestionsFromPPT", "teacher", "admin");
    }

    @Test
    @DisplayName("作业分析仅限学生本人")
    void analyzeHomeworkIsStudentOnly() {
        assertRoles(AIController.class, "analyzeHomework", "student");
    }

    private static void assertRoles(Class<?> controller, String methodName, String... expected) {
        Method method = Arrays.stream(controller.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        controller.getSimpleName() + " 上找不到方法 " + methodName));

        RequireRole annotation = method.getAnnotation(RequireRole.class);
        assertThat(annotation)
                .as("%s#%s 必须声明 @RequireRole，否则它对所有已登录角色开放",
                        controller.getSimpleName(), methodName)
                .isNotNull();

        Set<String> actual = Set.of(annotation.value());
        assertThat(actual)
                .as("%s#%s 的角色集合", controller.getSimpleName(), methodName)
                .isEqualTo(Set.of(expected));
    }

    /** 供人工核对：列出所有反射可见的角色值，防止误写成不存在的角色名。 */
    @Test
    @DisplayName("角色名只能是有实际含义的三个")
    void roleNamesAreFromTheKnownVocabulary() {
        Set<String> declared = Arrays.stream(AIController.class.getDeclaredMethods())
                .map(m -> m.getAnnotation(RequireRole.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(a -> Arrays.stream(a.value()))
                .collect(Collectors.toSet());

        assertThat(declared).isSubsetOf(Set.of("student", "teacher", "admin"));
    }
}
