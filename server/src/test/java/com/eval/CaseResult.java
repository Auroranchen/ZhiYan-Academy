package com.eval;

import java.util.List;

/**
 * 单题结果。
 *
 * <p>指标刻意分成两类：
 * <ul>
 *   <li><b>确定性</b>（引用有效性、引用精度、要点覆盖率）——零 API 成本、可复现，
 *       甚至在完全不调模型的情况下就能测（检索指标）。</li>
 *   <li><b>主观性</b>（judge 打的三项分）——有噪声，需要报方差。</li>
 * </ul>
 * 两类互为交叉验证：两边打架的地方才是真正值得看的地方。
 */
public record CaseResult(
        String caseId,
        String topic,
        boolean negative,
        boolean retrievalHit,
        boolean citationValid,
        double citationPrecision,
        int citationsUsed,
        int citationsInvalid,
        double factRecall,
        Integer correctness,
        Integer grounding,
        Integer citationSupport,
        List<String> unsupportedClaims,
        String verdict,
        int totalTokens,
        long elapsedMs,
        String error) {

    public boolean failed() {
        return error != null && !error.isBlank();
    }

    /** 负样本的「答对」= 明确说明资料未覆盖，而不是编一个答案。 */
    public boolean refusalCorrect() {
        return "refusal_correct".equals(verdict);
    }

    /** 判分是否可用（未判分时各主观项为 null，聚合时需按有效样本数平均）。 */
    public boolean judged() {
        return correctness != null && citationSupport != null;
    }
}
