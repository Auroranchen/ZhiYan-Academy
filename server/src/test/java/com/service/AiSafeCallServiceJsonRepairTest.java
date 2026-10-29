package com.service;

import com.ai.AiModelRouter;
import com.config.AiRoutingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 解析失败时先做本地修复，而不是立刻重发调用。
 *
 * <p>来自一条实测：{@code agent.codecase} 生成了 17,033 token 的产物，只因内容里
 * 少转义一个引号就解析失败，于是重发了整次调用——72.2s 白跑，又花了 82.0s 重来。
 * 本地修复是零成本的，能修好就不该重发。
 *
 * <p>但也不能因为「能修」就放过真正坏掉的输出：修复无效时必须照旧重试，
 * 否则流水线会因为一个不可解析的产物直接失败。
 */
class AiSafeCallServiceJsonRepairTest {

    private ZhipuAIService aiService;
    private AiSafeCallService gateway;

    @BeforeEach
    void setUp() throws Exception {
        aiService = mock(ZhipuAIService.class);
        when(aiService.getConfiguredModel()).thenReturn("deepseek-v4-pro");

        gateway = new AiSafeCallService();
        ReflectionTestUtils.setField(gateway, "aiService", aiService);
        ReflectionTestUtils.setField(gateway, "jdbcTemplate", mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(gateway, "modelRouter",
                new AiModelRouter(new AiRoutingProperties()));
    }

    @Test
    @DisplayName("少转义引号的产物被本地修好，只调用模型一次")
    void repairableJsonIsFixedWithoutRecallingTheModel() throws Exception {
        when(aiService.callPublic(anyString(), anyString(), anyString()))
                .thenReturn("{\"cases\":[{\"code\":\"sum = \" + str(a+b)\",\"lang\":\"python\"}]}");

        Map<String, Object> parsed = gateway.callJson("agent.codecase", "system", "user");

        assertThat(parsed).as("修复后应当能解析出结构").containsKey("cases");
        verify(aiService, times(1))
                .callPublic(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("修不好的产物仍然重试，不把流水线直接搞失败")
    void unrepairableJsonStillRetries() throws Exception {
        when(aiService.callPublic(anyString(), anyString(), anyString()))
                .thenReturn("模型今天不想输出 JSON")
                .thenReturn("{\"ok\":true}");

        Map<String, Object> parsed = gateway.callJson("agent.quiz", "system", "user");

        assertThat(parsed).containsEntry("ok", true);
        verify(aiService, times(2))
                .callPublic(anyString(), anyString(), anyString());
    }
}
