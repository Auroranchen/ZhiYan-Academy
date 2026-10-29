package com.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景级模型路由配置（{@code app.ai-routing.*}）。
 *
 * <p>存在理由：模型吞吐直接决定流水线时长——实测 {@code deepseek-flash} 为 212 tok/s，
 * {@code deepseek-v4-pro} 只有 73 tok/s。而流水线一次要跑 11–14 次调用，
 * 于是「用哪个模型」成了整条流水线是 90s 还是 450s 的分水岭。
 *
 * <p>做成**按场景前缀**而不是全局开关，是为了只让流水线用快模型，
 * 作业批改、学情报告等场景维持原有模型不受影响。
 */
@Component
@ConfigurationProperties(prefix = "app.ai-routing")
public class AiRoutingProperties {

    /** 场景前缀 → 模型。同一场景命中多条时取最长前缀（更具体的规则优先）。 */
    private List<Rule> rules = new ArrayList<>();

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules == null ? new ArrayList<>() : rules;
    }

    /**
     * @param prefix 场景前缀，如 {@code agent.}；为空则忽略该规则
     * @param model  命中时使用的模型名；为空则忽略该规则
     */
    public record Rule(String prefix, String model) {
    }
}
