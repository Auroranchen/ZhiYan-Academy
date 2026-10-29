package com.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * 评测报告输出。
 *
 * <p>产出两样东西：**Markdown**（可直接截图/贴进技术方案与答辩材料的对比表）
 * 和 **CSV**（便于继续加工成图表）。评测若不产出可展示的表格，就只是自娱自乐。
 */
public final class ReportWriter {

    private ReportWriter() {
    }

    public static void write(List<VariantRun> runs, Path outDir, EvalDataset dataset,
                             EvalCorpus corpus, boolean retrievalOnly) throws IOException {
        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve("report.md"), markdown(runs, dataset, corpus, retrievalOnly),
                StandardCharsets.UTF_8);
        Files.writeString(outDir.resolve("summary.csv"), summaryCsv(runs, retrievalOnly),
                StandardCharsets.UTF_8);
        Files.writeString(outDir.resolve("cases.csv"), casesCsv(runs),
                StandardCharsets.UTF_8);
    }

    static String markdown(List<VariantRun> runs, EvalDataset dataset, EvalCorpus corpus,
                           boolean retrievalOnly) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 智枢学堂 · RAG 评测报告\n\n");
        sb.append("- 生成时间：").append(Instant.now()).append("\n");
        sb.append("- 金标准集：").append(dataset.name())
                .append("（可答 ").append(dataset.answerable().size())
                .append(" / 负样本 ").append(dataset.negatives().size()).append("）\n");
        sb.append("- 语料快照：").append(corpus.version())
                .append("（资料 ").append(corpus.resources().size())
                .append(" / 切片 ").append(corpus.chunkCount()).append("）\n");
        sb.append("- 模式：").append(retrievalOnly
                ? "仅检索（不调用模型，指标均为确定性计算）" : "完整评测（含生成与 LLM 评判）").append("\n");
        if (!runs.isEmpty()) {
            sb.append("- 生成模型：").append(runs.get(0).generatorModel())
                    .append("　评判模型：").append(runs.get(0).judgeModel()).append("\n");
        }
        sb.append("\n");

        sb.append("## 变体对比\n\n");
        if (retrievalOnly) {
            sb.append("| 变体 | 检索命中率 |\n|---|---|\n");
            for (VariantRun run : runs) {
                sb.append("| ").append(run.variantId())
                        .append(" | ").append(pct(run.retrievalHitRate())).append(" |\n");
            }
            sb.append("\n> 本次为仅检索模式：不调用任何模型，指标全部是确定性计算。"
                    + "它衡量的是**纯检索质量**，与生成模型无关。\n");
        } else {
            sb.append("> 评判与生成使用**不同厂商**的模型，以降低自偏好偏差。\n\n");
            sb.append("| 变体 | 检索命中率 | 引用精度 | 要点覆盖率 | 引用支撑度 | 正确性 | 依据性 | 拒答准确率 | 单题均 token |\n");
            sb.append("|---|---|---|---|---|---|---|---|---|\n");
            for (VariantRun run : runs) {
                sb.append("| ").append(run.variantId())
                        .append(" | ").append(pct(run.retrievalHitRate()))
                        .append(" | ").append(pct(run.meanCitationPrecision()))
                        .append(" | ").append(pct(run.meanFactRecall()))
                        .append(" | ").append(score(run.meanCitationSupport()))
                        .append(" | ").append(score(run.meanCorrectness()))
                        .append(" | ").append(score(run.meanGrounding()))
                        .append(" | ").append(pct(run.refusalAccuracy()))
                        .append(" | ").append(avgTokens(run)).append(" |\n");
            }
        }

        sb.append("\n> **检索命中率** = 题目标注的切片是否真的被检索出来（越接近 100% 越好，"
                + "与模型无关，纯检索质量）。\n");
        if (!retrievalOnly) {
            sb.append("> **引用支撑度** = 被引用的片段是否**真的支撑**对应论断——"
                    + "机械校验只能查标签是否存在，查不出「引了一段真话但和结论无关」，这一项才是增量。\n");
        }

        sb.append("\n## 逐题明细\n\n");
        for (VariantRun run : runs) {
            sb.append("### ").append(run.variantId()).append("\n\n");
            if (retrievalOnly) {
                sb.append("| 题号 | 主题 | 检索命中 |\n|---|---|---|\n");
                for (CaseResult r : run.results()) {
                    sb.append("| ").append(r.caseId())
                            .append(" | ").append(r.topic())
                            .append(" | ").append(r.retrievalHit() ? "✓" : "✗").append(" |\n");
                }
            } else {
                sb.append("| 题号 | 主题 | 检索命中 | 引用精度 | 要点覆盖 | 支撑度 | verdict | 耗时 ms |\n");
                sb.append("|---|---|---|---|---|---|---|---|\n");
                for (CaseResult r : run.results()) {
                    sb.append("| ").append(r.caseId())
                            .append(" | ").append(r.topic())
                            .append(" | ").append(r.retrievalHit() ? "✓" : "✗")
                            .append(" | ").append(pct(r.citationPrecision()))
                            .append(" | ").append(pct(r.factRecall()))
                            .append(" | ").append(nz(r.citationSupport()))
                            .append(" | ").append(r.failed() ? "ERR" : r.verdict())
                            .append(" | ").append(r.elapsedMs()).append(" |\n");
                    if (r.failed()) {
                        sb.append("| | | | | | | ⚠ ").append(escape(r.error())).append(" | |\n");
                    }
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    static String summaryCsv(List<VariantRun> runs, boolean retrievalOnly) {
        StringBuilder sb = new StringBuilder();
        sb.append("variant,retrieval_hit_rate,citation_precision,fact_recall");
        if (!retrievalOnly) sb.append(",citation_support,correctness,grounding,refusal_accuracy");
        sb.append(",avg_tokens_per_case,total_tokens,failed_cases\n");
        for (VariantRun run : runs) {
            sb.append(run.variantId())
                    .append(',').append(num(run.retrievalHitRate()))
                    .append(',').append(num(run.meanCitationPrecision()))
                    .append(',').append(num(run.meanFactRecall()));
            if (!retrievalOnly) {
                sb.append(',').append(num(run.meanCitationSupport().orElse(Double.NaN)))
                        .append(',').append(num(run.meanCorrectness().orElse(Double.NaN)))
                        .append(',').append(num(run.meanGrounding().orElse(Double.NaN)))
                        .append(',').append(num(run.refusalAccuracy()));
            }
            sb.append(',').append(avgTokens(run))
                    .append(',').append(run.totalTokens())
                    .append(',').append(run.failedCount())
                    .append('\n');
        }
        return sb.toString();
    }

    static String casesCsv(List<VariantRun> runs) {
        StringBuilder sb = new StringBuilder();
        sb.append("variant,case_id,topic,negative,retrieval_hit,citation_valid,citation_precision,"
                + "citation_used,citation_invalid,fact_recall,citation_support,correctness,grounding,"
                + "verdict,unsupported_claims,tokens,elapsed_ms,error\n");
        for (VariantRun run : runs) {
            for (CaseResult r : run.results()) {
                sb.append(run.variantId()).append(',')
                        .append(r.caseId()).append(',')
                        .append(csv(r.topic())).append(',')
                        .append(r.negative()).append(',')
                        .append(r.retrievalHit()).append(',')
                        .append(r.citationValid()).append(',')
                        .append(num(r.citationPrecision())).append(',')
                        .append(r.citationsUsed()).append(',')
                        .append(r.citationsInvalid()).append(',')
                        .append(num(r.factRecall())).append(',')
                        .append(r.citationSupport() == null ? "" : r.citationSupport()).append(',')
                        .append(r.correctness() == null ? "" : r.correctness()).append(',')
                        .append(r.grounding() == null ? "" : r.grounding()).append(',')
                        .append(r.verdict()).append(',')
                        .append(csv(String.join(" / ", r.unsupportedClaims()))).append(',')
                        .append(r.totalTokens()).append(',')
                        .append(r.elapsedMs()).append(',')
                        .append(csv(r.error()))
                        .append('\n');
            }
        }
        return sb.toString();
    }

    private static String pct(double ratio) {
        if (Double.isNaN(ratio)) return "n/a";
        return String.format("%.1f%%", ratio * 100);
    }

    private static String score(OptionalDouble value) {
        if (value.isEmpty()) return "n/a";
        return String.format("%.2f", value.orElse(Double.NaN));
    }

    private static String nz(Integer value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private static String num(double value) {
        return Double.isNaN(value) ? "" : String.format("%.4f", value);
    }

    private static long avgTokens(VariantRun run) {
        if (run.results().isEmpty()) return 0;
        return run.totalTokens() / run.results().size();
    }

    private static String csv(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")
                ? "\"" + escaped + "\"" : escaped;
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("|", "\\|").replace("\n", " ");
    }

    /** 供测试构造多行摘要用。 */
    static String joinIds(List<VariantRun> runs) {
        return runs.stream().map(VariantRun::variantId).collect(Collectors.joining(","));
    }
}
