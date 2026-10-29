package com.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class ZhipuAIServiceGradingTest {

    private final ZhipuAIService service = new ZhipuAIService();

    @Test
    void parsesHighConfidenceGradeAsCompletedCandidate() {
        ZhipuAIService.GradeResult result = service.parseGradeResult(
                "{\"score\":86,\"comment\":\"论证完整，示例部分可进一步展开\",\"confidence\":0.91}");

        assertEquals(new BigDecimal("86"), result.getScore());
        assertEquals(0.91, result.getConfidence(), 0.0001);
        assertFalse(result.isRequiresReview());
    }

    @Test
    void lowConfidenceGradeRequiresTeacherReview() {
        ZhipuAIService.GradeResult result = service.parseGradeResult(
                "{\"score\":72,\"comment\":\"提交内容存在无法确认的部分\",\"confidence\":0.42}");

        assertTrue(result.isRequiresReview());
    }

    @Test
    void malformedGradeNeverFallsBackToSixty() {
        assertThrows(ZhipuAIService.AIGradingException.class,
                () -> service.parseGradeResult("模型暂时无法判断，建议给60分"));
        assertThrows(ZhipuAIService.AIGradingException.class,
                () -> service.parseGradeResult("{\"comment\":\"缺少成绩\"}"));
    }

    @Test
    void shortAnswerScoreIsBoundedAndLowConfidenceRequiresReview() {
        ZhipuAIService.GradeResult result = service.parseShortAnswerResult(
                "{\"score\":99,\"comment\":\"部分依据不足\",\"confidence\":0.6}", 20);

        assertEquals(new BigDecimal("20"), result.getScore());
        assertTrue(result.isRequiresReview());
    }

    @Test
    void retriesOnlyTemporaryFailures() {
        assertFalse(service.isRetryable(new ZhipuAIService.AIHttpException(400, "bad request", 0)));
        assertFalse(service.isRetryable(new ZhipuAIService.AIHttpException(401, "unauthorized", 0)));
        assertTrue(service.isRetryable(new ZhipuAIService.AIHttpException(429, "limited", 2000)));
        assertTrue(service.isRetryable(new ZhipuAIService.AIHttpException(503, "unavailable", 0)));
        assertTrue(service.isRetryable(new ZhipuAIService.RetryableAIException("timeout", null)));
        assertFalse(service.isRetryable(new ZhipuAIService.AIConfigurationException("bad key")));
    }

    @Test
    void retryDelayUsesExponentialBackoffAndRetryAfterWithCap() {
        ReflectionTestUtils.setField(service, "retryBaseDelayMs", 1000L);
        ReflectionTestUtils.setField(service, "retryMaxDelayMs", 10000L);

        assertEquals(1000L, service.retryDelayMillis(
                new ZhipuAIService.RetryableAIException("timeout", null), 0));
        assertEquals(4000L, service.retryDelayMillis(
                new ZhipuAIService.RetryableAIException("timeout", null), 2));
        assertEquals(8000L, service.retryDelayMillis(
                new ZhipuAIService.AIHttpException(429, "limited", 8000), 0));
        assertEquals(10000L, service.retryDelayMillis(
                new ZhipuAIService.AIHttpException(429, "limited", 30000), 0));
    }
}
