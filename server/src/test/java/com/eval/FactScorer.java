package com.eval;

import java.util.List;

/**
 * 要点覆盖率：**确定性**打分，零 API 成本。
 *
 * <p>与 LLM-as-judge 主观分互为交叉验证：如果 judge 说"完全正确"而要点覆盖率很低，
 * 那多半是 judge 在放水（或题目与语料对不上）；反之若覆盖率很高而 judge 说"错误"，
 * 值得人工看一眼。两个来源互相打架时才是真正需要看的地方。
 *
 * <p>刻意保持粗糙——它只做「答案里有没有出现这些短语」这一件事。
 * 做得再精细也替代不了 judge，反而会引入自己的判断偏差。
 */
public final class FactScorer {

    private FactScorer() {
    }

    /**
     * @return 命中比例 0..1；无要点要求时返回 1.0（负样本不按要点打分）
     */
    public static double recall(List<String> requiredFacts, String answer) {
        if (requiredFacts == null || requiredFacts.isEmpty()) return 1.0;
        if (answer == null || answer.isBlank()) return 0.0;
        String normalized = normalize(answer);
        long hit = requiredFacts.stream()
                .filter(fact -> fact != null && !fact.isBlank())
                .filter(fact -> normalized.contains(normalize(fact)))
                .count();
        return (double) hit / requiredFacts.size();
    }

    /** 归一去空白与全角半角差异，避免因排版差异误判为未命中。 */
    static String normalize(String text) {
        return text.replaceAll("\\s+", "")
                .replace('（', '(').replace('）', ')')
                .replace('，', ',').replace('。', '.')
                .toLowerCase(java.util.Locale.ROOT);
    }
}
