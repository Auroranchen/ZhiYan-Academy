package com.ai;

import com.config.AiRoutingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 场景级模型路由。
 *
 * <p>存在理由：流水线一次要跑 11–14 次大模型调用，实测 {@code deepseek-flash} 吞吐
 * 212 tok/s 而 {@code deepseek-v4-pro} 只有 73 tok/s——模型选择直接决定整条流水线是
 * 90s 还是 450s。把切换做成**按场景前缀**，是为了只让流水线享受快模型，
 * 而作业批改、学情报告等场景维持原有模型不受影响。
 *
 * <p>这里是纯策略逻辑，不依赖 Spring 容器，因此直接构造规则测试。
 */
class AiModelRouterTest {

    private static final String DEFAULT_MODEL = "deepseek-v4-pro";

    private static AiModelRouter router(AiRoutingProperties.Rule... rules) {
        AiRoutingProperties properties = new AiRoutingProperties();
        properties.setRules(List.of(rules));
        return new AiModelRouter(properties);
    }

    @Test
    @DisplayName("前缀命中时使用规则指定的模型")
    void matchingPrefixUsesRuleModel() {
        AiModelRouter router = router(new AiRoutingProperties.Rule("agent.", "deepseek-flash"));

        assertThat(router.modelFor("agent.quiz", DEFAULT_MODEL)).isEqualTo("deepseek-flash");
        assertThat(router.modelFor("agent.coordinate", DEFAULT_MODEL)).isEqualTo("deepseek-flash");
    }

    @Test
    @DisplayName("未命中的场景一律回落默认模型——作业批改、学情报告不受影响")
    void unmatchedSceneFallsBackToDefault() {
        AiModelRouter router = router(new AiRoutingProperties.Rule("agent.", "deepseek-flash"));

        assertThat(router.modelFor("chat", DEFAULT_MODEL)).isEqualTo(DEFAULT_MODEL);
        assertThat(router.modelFor("insight.classReport", DEFAULT_MODEL)).isEqualTo(DEFAULT_MODEL);
        assertThat(router.modelFor("profile.evaluate", DEFAULT_MODEL)).isEqualTo(DEFAULT_MODEL);
        // 只匹配前缀，"agent" 本身没有点号，不算命中
        assertThat(router.modelFor("agentX", DEFAULT_MODEL)).isEqualTo(DEFAULT_MODEL);
    }

    @Test
    @DisplayName("多条规则同时命中时取最长前缀——更具体的规则优先")
    void longestPrefixWins() {
        AiModelRouter router = router(
                new AiRoutingProperties.Rule("agent.", "deepseek-flash"),
                new AiRoutingProperties.Rule("agent.revise.", "deepseek-v4-pro"));

        assertThat(router.modelFor("agent.revise.quiz", DEFAULT_MODEL)).isEqualTo("deepseek-v4-pro");
        assertThat(router.modelFor("agent.quiz", DEFAULT_MODEL)).isEqualTo("deepseek-flash");
    }

    @Test
    @DisplayName("没有规则时就是默认模型")
    void noRulesKeepsDefault() {
        AiModelRouter router = router();

        assertThat(router.modelFor("agent.quiz", DEFAULT_MODEL)).isEqualTo(DEFAULT_MODEL);
    }

    @Test
    @DisplayName("残缺规则被忽略而不是让路由崩掉——配置错误不该拖垮流水线")
    void malformedRulesAreIgnored() {
        AiModelRouter router = router(
                new AiRoutingProperties.Rule(null, "deepseek-flash"),
                new AiRoutingProperties.Rule("   ", "deepseek-flash"),
                new AiRoutingProperties.Rule("agent.", null),
                new AiRoutingProperties.Rule("agent.quiz", "  "),
                new AiRoutingProperties.Rule("insight.", "deepseek-flash"));

        // 前缀/模型为空的规则被跳过，agent.quiz 回落到默认
        assertThat(router.modelFor("agent.quiz", DEFAULT_MODEL)).isEqualTo(DEFAULT_MODEL);
        // 合法规则照常生效
        assertThat(router.modelFor("insight.classReport", DEFAULT_MODEL)).isEqualTo("deepseek-flash");
    }

    @Test
    @DisplayName("场景为空时回落默认；默认模型为空时如实返回，交由网关自己兜底")
    void blankInputsAreTolerated() {
        AiModelRouter router = router(new AiRoutingProperties.Rule("agent.", "deepseek-flash"));

        assertThat(router.modelFor(null, DEFAULT_MODEL)).isEqualTo(DEFAULT_MODEL);
        assertThat(router.modelFor("", DEFAULT_MODEL)).isEqualTo(DEFAULT_MODEL);
        assertThat(router.modelFor("   ", DEFAULT_MODEL)).isEqualTo(DEFAULT_MODEL);
        assertThat(router.modelFor("agent.quiz", null)).isEqualTo("deepseek-flash");
        assertThat(router.modelFor("chat", null)).isNull();
    }
}
