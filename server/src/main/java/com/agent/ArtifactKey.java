package com.agent;

import java.util.List;
import java.util.Optional;

/**
 * 流水线各智能体产物的键——**封闭词汇表**。
 *
 * <p>交接关系必须约束在这个集合内：开放词汇意味着模型可以「发明」一个不存在的步骤，
 * 而下游没有任何代码能执行它——现场演示时表现为流水线卡住或某一段永远为空。
 *
 * <p>步骤分成三段：
 * <ul>
 *   <li><b>结构性前置</b>（诊断、协调）：产出后续所有步骤都依赖的输入，不可重排；</li>
 *   <li><b>可重排内容</b>：交接关系只在这一段内生效，这是唯一允许改变顺序的地方；</li>
 *   <li><b>结构性后置</b>（评审、复审、编排）：必须看到全部产物，不可重排。</li>
 * </ul>
 * 把可重排范围收窄到内容段，是让现场演示安全的实用取舍：
 * 模型即使给出荒谬的交接，也影响不到结构。
 */
public enum ArtifactKey {

    DIAGNOSE("diagnose", "诊断智能体"),
    COORDINATE("coordinate", "协调智能体"),

    PLAN("plan", "规划智能体"),
    RESOURCE("resource", "资源智能体"),
    MINDMAP("mindmap", "思维导图智能体"),
    QUIZ("quiz", "出题智能体"),
    READING("reading", "阅读智能体"),
    CODECASE("codecase", "代码实操智能体"),
    EXPLAINER("explainer", "讲解动画智能体"),

    REVIEW("review", "评审智能体"),
    REVIEW_FINAL("reviewFinal", "复审智能体"),
    ASSEMBLE("assemble", "编排智能体");

    private final String key;
    private final String label;

    ArtifactKey(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** 结构性前置：产出全局依赖，顺序不可变。 */
    public static final List<ArtifactKey> FIXED_PREFIX = List.of(DIAGNOSE, COORDINATE);

    /** 可重排内容段：交接关系只在这一段内生效。 */
    public static final List<ArtifactKey> REORDERABLE =
            List.of(PLAN, RESOURCE, MINDMAP, QUIZ, READING, CODECASE, EXPLAINER);

    /** 结构性后置：需要看到全部产物，顺序不可变。 */
    public static final List<ArtifactKey> FIXED_SUFFIX = List.of(REVIEW, REVIEW_FINAL, ASSEMBLE);

    /** 默认全序：前置 + 内容段默认次序 + 后置。无交接关系时就是这个顺序。 */
    public static final List<ArtifactKey> DEFAULT_ORDER;

    static {
        List<ArtifactKey> order = new java.util.ArrayList<>();
        order.addAll(FIXED_PREFIX);
        order.addAll(REORDERABLE);
        order.addAll(FIXED_SUFFIX);
        DEFAULT_ORDER = List.copyOf(order);
    }

    public static Optional<ArtifactKey> of(String key) {
        if (key == null) return Optional.empty();
        for (ArtifactKey value : values()) {
            if (value.key.equalsIgnoreCase(key.trim())) return Optional.of(value);
        }
        return Optional.empty();
    }

    public boolean reorderable() {
        return REORDERABLE.contains(this);
    }
}
