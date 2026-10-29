package com.eval;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 金标准集的完整性检查。
 *
 * <p>出题是人工活，最容易犯且**最不容易被发现**的错误是 {@code mustCite} 指向
 * 不存在的切片——评测照样跑得通，只是"引用命中率"永远偏低，然后你会去怀疑检索，
 * 而不是怀疑题目本身。所以这里把题面与实际语料做一次机械比对。
 *
 * <p>语料快照是导出产物，未导出时跳过（先跑一次 {@link CorpusExporter}）。
 */
class GoldenSetIntegrityTest {

    private static final Path GOLDEN = Path.of("src/test/resources/eval/golden-v1.jsonl");
    private static final Path CORPUS = Path.of("eval/corpus-v1.json");

    private static EvalDataset dataset;
    private static EvalCorpus corpus;

    @BeforeAll
    static void load() throws Exception {
        dataset = EvalDataset.load(GOLDEN, "golden-v1");
        if (Files.exists(CORPUS)) {
            corpus = EvalCorpus.readFrom(CORPUS);
        }
    }

    @Test
    @DisplayName("题目 ID 唯一，且每题都有题干与参考答案")
    void casesAreWellFormed() {
        Set<String> ids = new HashSet<>();
        for (EvalCase c : dataset.cases()) {
            assertThat(ids.add(c.id()))
                    .as("题目 ID 重复: %s", c.id())
                    .isTrue();
            assertThat(c.question()).as("%s 缺少题干", c.id()).isNotBlank();
            assertThat(c.referenceAnswer()).as("%s 缺少参考答案", c.id()).isNotBlank();
            assertThat(c.topic()).as("%s 缺少主题", c.id()).isNotBlank();
        }
        assertThat(dataset.answerable()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(dataset.negatives()).as("必须包含负样本，否则测不出幻觉").isNotEmpty();
    }

    @Test
    @DisplayName("每道可答题的 mustCite 都指向语料里真实存在的切片")
    void mustCitePointsAtRealChunks() {
        assumeTrue(corpus != null, "未导出语料快照，跳过（先跑 CorpusExporter）");

        Set<String> existing = corpus.resources().stream()
                .flatMap(r -> r.chunks().stream()
                        .map(c -> r.resourceId() + "#" + c.chunkIndex()))
                .collect(Collectors.toSet());

        for (EvalCase c : dataset.answerable()) {
            assertThat(c.hasMustCite())
                    .as("%s 必须声明 mustCite，否则无法评估检索命中", c.id())
                    .isTrue();
            for (EvalCase.MustCite cite : c.mustCite()) {
                assertThat(existing)
                        .as("%s 引用了不存在的切片 %d#%d —— 语料快照里没有这一块",
                                c.id(), cite.resourceId(), cite.chunkIndex())
                        .contains(cite.resourceId() + "#" + cite.chunkIndex());
            }
        }
    }

    @Test
    @DisplayName("可答题的 mustCite 切片里确实含有 requiredFacts 的要点")
    void citedChunksActuallyContainTheRequiredFacts() {
        assumeTrue(corpus != null, "未导出语料快照，跳过");

        for (EvalCase c : dataset.answerable()) {
            if (c.requiredFacts().isEmpty()) continue;
            String citedText = c.mustCite().stream()
                    .map(cite -> chunkText(cite.resourceId(), cite.chunkIndex()))
                    .collect(Collectors.joining("\n"));

            long covered = c.requiredFacts().stream()
                    .filter(fact -> citedText.contains(fact))
                    .count();

            // 不要求全中（要点可能是同义改写），但至少要有过半落在被引用切片里，
            // 否则说明题目与语料对不上——这种题会让检索指标无端变差
            assertThat(covered)
                    .as("%s 的 mustCite 切片只覆盖了 %d/%d 个要点，题目与语料可能对不上。"
                                    + "被引内容: %s",
                            c.id(), covered, c.requiredFacts().size(), abbreviate(citedText))
                    .isGreaterThanOrEqualTo((c.requiredFacts().size() + 1) / 2);
        }
    }

    @Test
    @DisplayName("负样本的主题确实不在语料里（否则它就是一道可答题）")
    void negativeTopicsAreAbsentFromCorpus() {
        assumeTrue(corpus != null, "未导出语料快照，跳过");

        String allText = corpus.resources().stream()
                .flatMap(r -> r.chunks().stream())
                .map(EvalCorpus.ChunkText::content)
                .collect(Collectors.joining("\n"));

        for (EvalCase c : dataset.negatives()) {
            // 用题目里的关键实体词做检查：这些词若在语料中出现，说明主题其实被覆盖了
            for (String probe : List.of("InnoDB", "MyISAM", "隔离级别", "Docker", "虚拟机", "Redis", "AOF", "RDB")) {
                if (!c.question().contains(probe)) continue;
                assertThat(allText)
                        .as("负样本 %s 的关键词「%s」在语料里出现了 —— 这题其实可答，"
                                + "应当改成可答题或换一个真正超纲的主题", c.id(), probe)
                        .doesNotContain(probe);
            }
        }
    }

    private static String chunkText(int resourceId, int chunkIndex) {
        return corpus.resources().stream()
                .filter(r -> r.resourceId() == resourceId)
                .flatMap(r -> r.chunks().stream())
                .filter(c -> c.chunkIndex() == chunkIndex)
                .map(EvalCorpus.ChunkText::content)
                .findFirst()
                .orElse("");
    }

    private static String abbreviate(String text) {
        return text.length() > 120 ? text.substring(0, 120) + "…" : text;
    }
}
