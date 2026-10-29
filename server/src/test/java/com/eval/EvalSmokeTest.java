package com.eval;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 评测链路的离线冒烟测试。
 *
 * <p><b>存在的意义：</b>评测代码本身也会坏。它坏了的表现很隐蔽——不报错，
 * 只是产出一张看起来合理但其实是错的表。这个测试用**桩模型 + 内存语料**把
 * 「加载 → 检索 → 打分 → 出表」整条链路跑一遍，全程不打任何计费请求，
 * 因此可以放进 CI。
 *
 * <p>语料快照是导出产物，未导出时跳过（先跑 {@link CorpusExporter}）。
 */
class EvalSmokeTest {

    private static final Path GOLDEN = Path.of("src/test/resources/eval/golden-v1.jsonl");
    private static final Path CORPUS = Path.of("eval/corpus-v1.json");

    @TempDir
    Path tempDir;

    private static EvalDataset dataset;
    private static EvalCorpus corpus;

    @BeforeAll
    static void load() throws Exception {
        dataset = EvalDataset.load(GOLDEN, "golden");
        if (Files.exists(CORPUS)) {
            corpus = EvalCorpus.readFrom(CORPUS);
        }
    }

    private EvalRunner.Config retrievalOnlyConfig() {
        return new EvalRunner.Config(GOLDEN, CORPUS, tempDir, List.of("v0-baseline"), true, 0);
    }

    @Test
    @DisplayName("仅检索模式下整条链路跑通，且基线命中率达到下限")
    void retrievalPipelineRunsOffline() throws Exception {
        assumeTrue(corpus != null, "未导出语料快照，跳过");

        VariantRun run = EvalRunner.runVariant(
                Variant.byId("v0-baseline"), dataset, corpus, retrievalOnlyConfig());

        assertThat(run.results()).hasSize(dataset.cases().size());
        assertThat(run.failedCount()).as("仅检索模式不应有任何失败题").isZero();
        assertThat(run.retrievalHitRate())
                .as("基线检索命中率跌破 50%%，说明检索或语料快照出了问题，"
                        + "而不是某个变体的差异")
                .isGreaterThan(0.5);
        assertThat(run.totalTokens()).as("仅检索模式不应产生 token 消耗").isZero();
    }

    @Test
    @DisplayName("不同变体确实产出不同结果（否则消融是假的）")
    void variantsActuallyDiffer() throws Exception {
        assumeTrue(corpus != null, "未导出语料快照，跳过");

        double baseline = EvalRunner.runVariant(
                Variant.byId("v0-baseline"), dataset, corpus, retrievalOnlyConfig()).retrievalHitRate();
        double semanticOnly = EvalRunner.runVariant(
                Variant.byId("v2-semantic-only"), dataset, corpus, retrievalOnlyConfig()).retrievalHitRate();

        assertThat(baseline)
                .as("若纯语义与混合召回结果相同，说明配置注入没生效——"
                        + "那所有消融结论都是假的")
                .isNotEqualTo(semanticOnly);
    }

    @Test
    @DisplayName("变体清单按 Key 可用性自适应，且 ID 唯一")
    void variantCatalogueIsConsistent() {
        var all = Variant.all();
        assertThat(all.stream().map(Variant::id).distinct().count())
                .as("变体 ID 必须唯一").isEqualTo(all.size());

        var available = Variant.defaults();
        assertThat(available).hasSizeLessThanOrEqualTo(all.size());
        if (!EvalSecrets.hasZhipuEmbeddingKey()) {
            assertThat(available.stream().anyMatch(Variant::requiresApiKey))
                    .as("缺 Key 时不应把真 embedding 变体列进默认集合，"
                            + "否则会跑出一整行看起来成功的空结果")
                    .isFalse();
        }
        // 按 id 查找必须能拿到全部变体（含需要 Key 的），便于显式指定
        assertThat(Variant.byId("v6-zhipu-hybrid").requiresApiKey()).isTrue();
    }

    @Test
    @DisplayName("报告渲染在两种模式下都成形")
    void reportRendersInBothModes() throws Exception {
        assumeTrue(corpus != null, "未导出语料快照，跳过");

        VariantRun run = EvalRunner.runVariant(
                Variant.byId("v0-baseline"), dataset, corpus, retrievalOnlyConfig());

        String md = ReportWriter.markdown(List.of(run), dataset, corpus, true);
        assertThat(md).contains("变体对比").contains("检索命中率").contains("v0-baseline");
        assertThat(md).as("仅检索模式不该出现生成类指标").doesNotContain("引用支撑度");

        String csv = ReportWriter.summaryCsv(List.of(run), true);
        assertThat(csv).startsWith("variant,retrieval_hit_rate");
        assertThat(csv.lines().count()).as("表头 + 1 个变体").isEqualTo(2);

        String cases = ReportWriter.casesCsv(List.of(run));
        assertThat(cases.lines().count())
                .as("表头 + 全部题目").isEqualTo(dataset.cases().size() + 1L);
    }

    @Test
    @DisplayName("安全检测器：认得泄露，也不会把成功拒绝误判成泄露")
    void guardDetectorDistinguishesLeaksFromRefusals() {
        GuardSuite.Probe probe = new GuardSuite.Probe("T-1", "测试", "任意题目");

        for (GuardSuite.DetectorCase sample : GuardSuite.detectorCases()) {
            GuardSuite.ProbeResult result = GuardSuite.classify(probe, sample.reply());

            assertThat(result.leaked())
                    .as("样本「%s」判定错误。回复: %s", sample.label(), sample.reply())
                    .isEqualTo(sample.actuallyLeaked());
        }
    }

    @Test
    @DisplayName("诱导话术集完整且互不重复")
    void guardProbesAreWellFormed() {
        List<GuardSuite.Probe> probes = GuardSuite.probes();

        assertThat(probes).hasSize(12);
        assertThat(probes.stream().map(GuardSuite.Probe::id).distinct().count())
                .as("话术 ID 必须唯一").isEqualTo(probes.size());
        assertThat(probes.stream().map(GuardSuite.Probe::tactic).distinct().count())
                .as("策略必须互不相同，否则覆盖不了不同越狱路径")
                .isEqualTo(probes.size());
    }

    @Test
    @DisplayName("要点覆盖率是确定性的（同输入同输出）")
    void factScoringIsDeterministic() {
        List<String> facts = List.of("方括号", "list()", "元组");
        String answer = "使用方括号 [] 或 list() 函数创建列表，列表可变而元组不可变。";

        double first = FactScorer.recall(facts, answer);
        double second = FactScorer.recall(facts, answer);

        assertThat(first).isEqualTo(second).isEqualTo(1.0);
        assertThat(FactScorer.recall(facts, "完全不相关的回答")).isZero();
        assertThat(FactScorer.recall(List.of(), "任何回答"))
                .as("无要点要求时（负样本）不按此打分，返回满分避免污染聚合")
                .isEqualTo(1.0);
        assertThat(FactScorer.recall(facts, null)).isZero();
    }
}
