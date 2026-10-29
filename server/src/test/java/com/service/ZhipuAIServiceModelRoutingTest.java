package com.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型覆盖参数在网关内部的生效规则。
 *
 * <p>{@code @Value} 注入的模型名在 {@code new ZhipuAIService()} 下为空，
 * 因此这里用反射显式设置，测的是「覆盖优先、否则回落默认」这一条判断本身。
 * 真正打到 HTTP 请求体里的 model 由 {@code callZhipuAI} 使用同一个方法计算，
 * 所以这条判断正确即意味着路由生效。
 */
class ZhipuAIServiceModelRoutingTest {

    private static final String DEFAULT_MODEL = "deepseek-v4-pro";

    private ZhipuAIService service() {
        ZhipuAIService service = new ZhipuAIService();
        ReflectionTestUtils.setField(service, "modelName", DEFAULT_MODEL);
        return service;
    }

    @Test
    void explicitOverrideWins() {
        assertThat(service().effectiveModel("deepseek-flash")).isEqualTo("deepseek-flash");
    }

    @Test
    void missingOverrideFallsBackToConfiguredModel() {
        assertThat(service().effectiveModel(null)).isEqualTo(DEFAULT_MODEL);
        assertThat(service().effectiveModel("")).isEqualTo(DEFAULT_MODEL);
        assertThat(service().effectiveModel("   ")).isEqualTo(DEFAULT_MODEL);
    }

    @Test
    void overrideIsTrimmedSoAStraySpaceDoesNotBreakTheModelName() {
        assertThat(service().effectiveModel(" deepseek-flash ")).isEqualTo("deepseek-flash");
    }
}
