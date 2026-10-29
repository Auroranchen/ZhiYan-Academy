package com.eval;

import java.util.List;
import java.util.OptionalDouble;

/**
 * 一个变体的整轮结果与聚合指标。
 *
 * <p>聚合时分母用**有效样本数**而不是总题数：判分失败的题不能按 0 分计入均值，
 * 否则一次接口抖动会被读成「质量下降」。失败题单独计数并展示。
 */
public record VariantRun(String variantId, String generatorModel, String judgeModel,
                         List<CaseResult> results) {

    public List<CaseResult> answerable() {
        return results.stream().filter(r -> !r.negative()).toList();
    }

    public List<CaseResult> negatives() {
        return results.stream().filter(CaseResult::negative).toList();
    }

    public long failedCount() {
        return results.stream().filter(CaseResult::failed).count();
    }

    /** 检索命中率：题目标注的 mustCite 切片是否真的被检索出来。核心检索指标。 */
    public double retrievalHitRate() {
        return mean(answerable().stream().mapToDouble(r -> r.retrievalHit() ? 1 : 0).boxed().toList());
    }

    public double meanCitationPrecision() {
        return mean(answerable().stream().map(CaseResult::citationPrecision).toList());
    }

    public double meanFactRecall() {
        return mean(answerable().stream().map(CaseResult::factRecall).toList());
    }

    public OptionalDouble meanCorrectness() {
        return meanOfJudged(CaseResult::correctness);
    }

    public OptionalDouble meanGrounding() {
        return meanOfJudged(CaseResult::grounding);
    }

    /** 引用支撑度——本项目的核心贡献点。 */
    public OptionalDouble meanCitationSupport() {
        return meanOfJudged(CaseResult::citationSupport);
    }

    /** 负样本上「正确拒答」的比例。 */
    public double refusalAccuracy() {
        List<CaseResult> neg = negatives();
        if (neg.isEmpty()) return Double.NaN;
        return mean(neg.stream().mapToDouble(r -> r.refusalCorrect() ? 1 : 0).boxed().toList());
    }

    public long totalTokens() {
        return results.stream().mapToLong(CaseResult::totalTokens).sum();
    }

    public long totalElapsedMs() {
        return results.stream().mapToLong(CaseResult::elapsedMs).sum();
    }

    /**
     * 每个「正确回答」的 token 成本——评分表最认的性价比指标。
     *
     * @return 无正确样本时为 NaN
     */
    public double costPerCorrectAnswer() {
        long correct = results.stream()
                .filter(r -> r.judged() && r.correctness() != null && r.correctness() >= 4)
                .count();
        return correct == 0 ? Double.NaN : (double) totalTokens() / correct;
    }

    private OptionalDouble meanOfJudged(java.util.function.Function<CaseResult, Integer> field) {
        List<Integer> values = results.stream()
                .filter(CaseResult::judged)
                .map(field)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (values.isEmpty()) return OptionalDouble.empty();
        return OptionalDouble.of(values.stream().mapToInt(Integer::intValue).average().orElse(Double.NaN));
    }

    private static double mean(List<Double> values) {
        if (values.isEmpty()) return Double.NaN;
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }
}
