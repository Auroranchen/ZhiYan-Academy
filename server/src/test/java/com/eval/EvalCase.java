package com.eval;

import java.util.List;

/**
 * 一道金标准评测题。
 *
 * @param id              稳定标识，写进结果表用于逐题对照
 * @param topic           主题，用于按主题聚合指标
 * @param type            concept / code / debug / design / <b>negative</b>
 * @param requiredFacts   参考答案里的必备要点。用**确定性**匹配打分（零 API 成本），
 *                        与 judge 的主观分互为交叉验证
 * @param mustCite        本题应当被检索到的切片。
 *                        用 {@code {resourceId, chunkIndex}} 而不是 {@code 资料N-M} 标签——
 *                        标签是每次检索时按命中顺序临时分配的，写死在题里会随语料变动而失效
 * @param courseId        课程 ID。当前语料快照是单一合成课程，故此值暂不参与过滤
 */
public record EvalCase(
        String id,
        Integer courseId,
        String topic,
        String type,
        String difficulty,
        String question,
        String referenceAnswer,
        List<String> requiredFacts,
        List<MustCite> mustCite) {

    public record MustCite(int resourceId, int chunkIndex) {
    }

    /**
     * 负样本：语料里确实没有答案的题。
     *
     * <p>正确答案是明确说「资料中没有相关内容」，而不是编一个听起来合理的回答。
     * 这类题最能暴露幻觉——也是多数参赛作品不会去测的部分。
     */
    public boolean negative() {
        return "negative".equals(type);
    }

    public boolean hasMustCite() {
        return mustCite != null && !mustCite.isEmpty();
    }
}
