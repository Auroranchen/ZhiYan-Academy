package com.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LearningProfileClosureTest {

    private final LearningProfileService service = new LearningProfileService();

    @Test
    void answerNormalizationHandlesQuestionTypesCorrectly() {
        assertEquals("0", ReflectionTestUtils.invokeMethod(service, "normalizeAnswer", "0", "single"));
        assertEquals("0", ReflectionTestUtils.invokeMethod(service, "normalizeAnswer", "A", "single"));
        assertEquals("0", ReflectionTestUtils.invokeMethod(service, "normalizeAnswer", 0.0, "single"));
        assertEquals("false", ReflectionTestUtils.invokeMethod(service, "normalizeAnswer", "0", "judge"));
        assertEquals("0,2", ReflectionTestUtils.invokeMethod(service, "normalizeAnswer", "2, 0", "multi"));
        assertEquals("0,2", ReflectionTestUtils.invokeMethod(service, "normalizeAnswer", "C,A", "multi"));
        assertEquals("二叉树", ReflectionTestUtils.invokeMethod(service, "normalizeAnswer", " 二叉 树 ", "blank"));
        assertEquals("TCP", ReflectionTestUtils.invokeMethod(service, "normalizeAnswer", "TCP", "blank"));
    }

    @Test
    void weakPointsProduceReviewAndRetestPath() {
        List<Map<String, Object>> path = ReflectionTestUtils.invokeMethod(
                service, "adaptivePath", List.of("二叉树遍历"), 60.0);

        assertNotNull(path);
        assertEquals(3, path.size());
        assertEquals("回看讲解", path.get(0).get("title"));
        assertEquals("再次测评", path.get(2).get("title"));
    }

    @Test
    void highMasteryMovesToTransferStage() {
        List<Map<String, Object>> path = ReflectionTestUtils.invokeMethod(
                service, "adaptivePath", List.of(), 92.0);
        assertEquals(1, path.size());
        assertEquals("迁移应用", path.get(0).get("title"));
    }
}
