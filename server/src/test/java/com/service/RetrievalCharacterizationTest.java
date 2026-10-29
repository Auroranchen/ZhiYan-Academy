package com.service;

import com.model.CourseResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 检索打分的**行为快照**（characterization test）。
 *
 * <p>用途：把「混合召回」的算术逐位钉死。抽出 {@code RetrievalStrategy} 缝时，
 * 打分与排序必须与重构前**完全一致**——否则后面所有消融对比都失去基线，
 * 分不清差异是来自策略变更还是重构时不小心改了权重。
 *
 * <p>断言的是 score 的精确值（四舍五入到 4 位，与 {@code Citation} 的产出一致），
 * 不是「大概排序对」。
 */
class RetrievalCharacterizationTest {

    @TempDir
    Path tempDir;

    private CourseKnowledgeContextService service;
    private CourseResourceService resourceService;

    @BeforeEach
    void setUp() {
        service = new CourseKnowledgeContextService();
        resourceService = Mockito.mock(CourseResourceService.class);
        ReflectionTestUtils.setField(service, "courseResourceService", resourceService);
        ReflectionTestUtils.setField(service, "wordService", new WordService());
        ReflectionTestUtils.setField(service, "embeddingService", new LocalEmbeddingService());
        // jdbcTemplate 保持 null → 走 transientChunks，完全不碰数据库
    }

    private CourseResource resource(int id, String title, String filename, Path path) {
        CourseResource r = new CourseResource();
        r.setId(id);
        r.setTitle(title);
        r.setFilename(filename);
        r.setFilepath(path.toString());
        return r;
    }

    private void stubCorpus() throws Exception {
        Path a = tempDir.resolve("a.md");
        Files.writeString(a, "人工智能是研究智能行为的学科。机器学习是其中一个重要分支。",
                StandardCharsets.UTF_8);
        Path b = tempDir.resolve("b.md");
        Files.writeString(b, "数据结构研究数据的组织方式。二叉树是一种重要的非线性结构。",
                StandardCharsets.UTF_8);
        Path c = tempDir.resolve("c.md");
        Files.writeString(c, "二叉树的前序遍历先访问根节点，再依次遍历左子树与右子树。",
                StandardCharsets.UTF_8);

        when(resourceService.getByCourseid(eq(7), anyInt(), anyInt())).thenReturn(List.of(
                resource(1, "绪论", "a.md", a),
                resource(2, "数据结构", "b.md", b),
                resource(3, "二叉树", "c.md", c)));
        when(resourceService.resolveStoredPath(any()))
                .thenAnswer(inv -> Path.of(inv.getArgument(0, String.class)));
    }

    /** 把检索结果压成稳定的可比较字符串 */
    private static String snapshot(CourseKnowledgeContextService.KnowledgeContext ctx) {
        return ctx.citations().stream()
                .map(c -> c.label() + "@" + c.title() + "=" + c.score())
                .collect(Collectors.joining(" ; "));
    }

    @Test
    @DisplayName("带查询词时的打分与排序")
    void pinnedScoresWithQuery() throws Exception {
        stubCorpus();

        String actual = snapshot(service.build(7, "二叉树 前序遍历"));

        // 精确到 4 位小数：资料3（唯一含「二叉树」「前序遍历」）显著领先，
        // 资料2 靠「二叉树」勉强过 0.08 噪声门槛，资料1 被门槛滤掉
        assertThat(actual).isEqualTo("资料3-1@二叉树=0.4929 ; 资料2-1@数据结构=0.1687");
    }

    @Test
    @DisplayName("空查询时的退化解（按片段序号，不作相关性过滤）")
    void pinnedScoresWithBlankQuery() throws Exception {
        stubCorpus();

        String actual = snapshot(service.build(7, ""));

        assertThat(actual).isEqualTo(
                "资料1-1@绪论=1.0 ; 资料2-1@数据结构=1.0 ; 资料3-1@二叉树=1.0");
    }
}
