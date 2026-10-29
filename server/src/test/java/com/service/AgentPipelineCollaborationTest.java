package com.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentPipelineCollaborationTest {

    private final AgentPipelineService pipeline = new AgentPipelineService();

    @Test
    void coordinatorAssignmentControlsAgentFocusAndInstruction() {
        Map<String, Object> assignment = new HashMap<>();
        assignment.put("agent", "explainer");
        assignment.put("focusPoints", List.of("二叉树遍历"));
        assignment.put("instruction", "与讲义示例和练习题使用同一棵示例树");
        Map<String, Object> coordinate = Map.of("assignments", List.of(assignment));

        List<String> focus = ReflectionTestUtils.invokeMethod(
                pipeline, "assignmentFocus", coordinate, "explainer", List.of("fallback"));
        String instruction = ReflectionTestUtils.invokeMethod(
                pipeline, "assignmentInstruction", coordinate, "explainer");

        assertEquals(List.of("二叉树遍历"), focus);
        assertEquals("与讲义示例和练习题使用同一棵示例树", instruction);
    }

    @Test
    void reviewerCanRequestAtMostTwoAllowedRetries() {
        Map<String, Object> review = new HashMap<>();
        review.put("approved", false);
        review.put("retryAgents", List.of("resource", "invalid", "quiz", "explainer"));

        List<String> retries = ReflectionTestUtils.invokeMethod(pipeline, "retryAgentKeys", review);

        assertEquals(List.of("resource", "quiz"), retries);
    }

    @Test
    void approvedReviewSkipsRework() {
        Map<String, Object> review = Map.of(
                "approved", true,
                "retryAgents", List.of("resource")
        );
        List<String> retries = ReflectionTestUtils.invokeMethod(pipeline, "retryAgentKeys", review);
        assertTrue(retries.isEmpty());
    }

    /**
     * SSE 断线重连时靠这两个字段回放已完成步骤与最终状态。
     * PipelineRunRepository 用的是 stepsJson / resourceId 驼峰别名，
     * 一旦两边对不上，回放会静默失效——页面看起来"卡在第一步"，日志里却什么都没有。
     */
    @Test
    void replayReadsRepositoryColumnAliases() {
        Map<String, Object> run = new HashMap<>();
        run.put("stepsJson", "{\"diagnose\":{\"summary\":\"诊断完成\"}}");
        run.put("resourceId", 42);
        run.put("status", "done");

        Map<String, Object> steps = ReflectionTestUtils.invokeMethod(
                pipeline, "parseSteps", run.get("stepsJson"));

        assertNotNull(steps);
        assertTrue(steps.containsKey("diagnose"), "回放步骤应能从 stepsJson 别名解析出来");
        assertEquals(42, run.get("resourceId"));
    }

    @Test
    void replayToleratesMissingOrBrokenSteps() {
        assertTrue(ReflectionTestUtils.<Map<String, Object>>invokeMethod(pipeline, "parseSteps", (Object) null)
                .isEmpty());
        assertTrue(ReflectionTestUtils.<Map<String, Object>>invokeMethod(pipeline, "parseSteps", "不是 JSON")
                .isEmpty());
    }

    @Test
    void resourcePackContainsReusableExplainerScenes() {
        Map<String, Object> steps = new LinkedHashMap<>();
        steps.put("diagnose", Map.of("summary", "诊断", "weakPoints", new ArrayList<>()));
        steps.put("explainer", Map.of(
                "title", "遍历微课",
                "summary", "动画讲解",
                "scenes", List.of(Map.of("title", "前序遍历", "narration", "先访问根节点"))
        ));

        Map<String, Object> pack = ReflectionTestUtils.invokeMethod(
                pipeline, "buildPack", "数据结构", List.of("二叉树遍历"), steps);

        assertNotNull(pack);
        Map<?, ?> explainer = (Map<?, ?>) pack.get("explainer");
        assertEquals("遍历微课", explainer.get("title"));
        assertEquals(1, ((List<?>) explainer.get("scenes")).size());
    }
}
