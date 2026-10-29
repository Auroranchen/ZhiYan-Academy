package com.service;

import com.ai.AiModelRouter;
import com.config.AiRoutingProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class AiSafeCallMetricsTest {

    @Test
    void recordsModelAndTokenUsageForSuccessfulCall() throws Exception {
        ZhipuAIService ai = mock(ZhipuAIService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // 网关按场景选模型后走三参重载，桩要跟着改，否则调用拿不到返回值
        when(ai.callPublic(any(), any(), any())).thenReturn("教学建议");
        when(ai.getConfiguredModel()).thenReturn("deepseek-test");
        when(ai.consumeLastCallMetrics()).thenReturn(
                new ZhipuAIService.CallMetrics("deepseek-test", 120, 45, 165));

        AiSafeCallService service = new AiSafeCallService();
        ReflectionTestUtils.setField(service, "aiService", ai);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        // 无规则的路由：所有场景都回落默认模型，本测试只关心指标落库
        ReflectionTestUtils.setField(service, "modelRouter",
                new AiModelRouter(new AiRoutingProperties()));

        assertEquals("教学建议", service.callText("test.scene", "system", "user"));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("total_tokens"), args.capture());
        Object[] values = args.getValue();
        assertEquals("test.scene", values[0]);
        assertEquals("deepseek-test", values[3]);
        assertEquals(120, values[4]);
        assertEquals(45, values[5]);
        assertEquals(165, values[6]);
    }
}
