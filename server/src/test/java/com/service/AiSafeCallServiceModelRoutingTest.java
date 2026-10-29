package com.service;

import com.ai.AiModelRouter;
import com.config.AiRoutingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 网关是否真的把「按场景选出的模型」传给了底层调用。
 *
 * <p>{@code AiModelRouterTest} 只证明选模型这段逻辑对；这里补的是**接线**——
 * 路由算出来却忘了往下传，是这类改动最容易漏的一步，而且症状很隐蔽：
 * 功能照常工作，只是速度一点没快。
 */
class AiSafeCallServiceModelRoutingTest {

    private static final String DEFAULT_MODEL = "deepseek-v4-pro";

    private ZhipuAIService aiService;
    private AiSafeCallService gateway;

    private void setUpGateway() throws Exception {
        aiService = mock(ZhipuAIService.class);
        when(aiService.getConfiguredModel()).thenReturn(DEFAULT_MODEL);
        when(aiService.callPublic(anyString(), anyString(), anyString())).thenReturn("{\"ok\":true}");

        AiRoutingProperties properties = new AiRoutingProperties();
        properties.setRules(List.of(new AiRoutingProperties.Rule("agent.", "deepseek-flash")));

        gateway = new AiSafeCallService();
        ReflectionTestUtils.setField(gateway, "aiService", aiService);
        ReflectionTestUtils.setField(gateway, "jdbcTemplate", mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(gateway, "modelRouter", new AiModelRouter(properties));
    }

    @Test
    @DisplayName("流水线场景把快模型传下去")
    void pipelineScenePassesFastModelDownstream() throws Exception {
        setUpGateway();

        gateway.callJson("agent.quiz", "出题", "出一道题");

        verify(aiService).callPublic(anyString(), anyString(), eq("deepseek-flash"));
    }

    @Test
    @DisplayName("非流水线场景沿用默认模型，不被误伤")
    void otherScenesKeepDefaultModel() throws Exception {
        setUpGateway();

        gateway.callJson("insight.classReport", "报告", "生成报告");

        verify(aiService).callPublic(anyString(), anyString(), eq(DEFAULT_MODEL));
    }
}
