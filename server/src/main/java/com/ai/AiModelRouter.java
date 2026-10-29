package com.ai;

import com.config.AiRoutingProperties;
import org.springframework.stereotype.Component;

/**
 * 按场景选择模型。
 *
 * <p>与 {@link AiPrompts} 同属「AI 层策略」：网关只管调用，**用哪个模型由这里决定**。
 * 默认模型由调用方传入（来自 {@code ai.zhipu.model}），路由只负责在命中规则时覆盖它，
 * 这样即使没有任何规则配置，行为也与路由存在之前完全一致。
 */
@Component
public class AiModelRouter {

    private final AiRoutingProperties properties;

    public AiModelRouter(AiRoutingProperties properties) {
        this.properties = properties;
    }

    /**
     * 选出该场景应使用的模型。
     *
     * <p>规则按**最长前缀**取胜，因此 {@code agent.} 与 {@code agent.revise.} 可以共存，
     * 后者只对返工调用生效。前缀/模型为空的规则直接跳过——配置写残了不该让流水线跑不起来。
     *
     * @param scene        场景名（如 {@code agent.quiz}），可为空
     * @param defaultModel 未命中任何规则时使用的模型，来自 {@code ai.zhipu.model}
     */
    public String modelFor(String scene, String defaultModel) {
        if (scene == null || scene.isBlank()) return defaultModel;

        String bestPrefix = null;
        String bestModel = null;
        for (AiRoutingProperties.Rule rule : properties.getRules()) {
            if (rule == null) continue;
            String prefix = rule.prefix();
            String model = rule.model();
            if (prefix == null || prefix.isBlank() || model == null || model.isBlank()) continue;
            if (!scene.startsWith(prefix)) continue;
            if (bestPrefix == null || prefix.length() > bestPrefix.length()) {
                bestPrefix = prefix;
                bestModel = model;
            }
        }
        return bestModel != null ? bestModel : defaultModel;
    }
}
