package com.eval;

import com.model.CourseResource;
import com.rag.Chunk;
import com.rag.ChunkSource;
import com.service.CourseKnowledgeContextService;
import com.service.CourseResourceService;
import com.service.LocalEmbeddingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 语料快照的读写，以及「检索能不依赖数据库跑起来」这条核心能力。
 *
 * <p>后半句才是重点：评测 harness 之所以能在舞台上用笔记本跑、不需要 MySQL 与 Docker，
 * 靠的就是把 {@link ChunkSource} 换成内存快照。这个测试把那条路径真正走通一遍，
 * 而不是只断言 JSON 能来回序列化。
 */
class EvalCorpusTest {

    @TempDir
    Path tempDir;

    private static EvalCorpus sampleCorpus() {
        return new EvalCorpus(EvalCorpus.CURRENT_VERSION, "2026-01-01T00:00:00Z", List.of(
                new EvalCorpus.Resource(1, "二叉树", "tree.pptx", List.of(
                        new EvalCorpus.ChunkText(0, "二叉树的前序遍历先访问根节点，再依次遍历左子树与右子树。"),
                        new EvalCorpus.ChunkText(1, "二叉树每个节点最多有两个子节点。"))),
                new EvalCorpus.Resource(2, "绪论", "intro.docx", List.of(
                        new EvalCorpus.ChunkText(0, "人工智能是研究智能行为的学科。")))));
    }

    @Test
    @DisplayName("快照能写盘再读回，内容与切片数不变")
    void roundTripsThroughDisk() throws Exception {
        Path file = tempDir.resolve("corpus.json");
        EvalCorpus original = sampleCorpus();
        original.writeTo(file);

        EvalCorpus loaded = EvalCorpus.readFrom(file);

        assertThat(loaded.version()).isEqualTo(EvalCorpus.CURRENT_VERSION);
        assertThat(loaded.resources()).hasSize(2);
        assertThat(loaded.chunkCount()).isEqualTo(3);
        assertThat(loaded.resources().get(0).chunks().get(0).content())
                .contains("前序遍历");
    }

    @Test
    @DisplayName("注入内存快照后，检索在没有 JdbcTemplate 的情况下照常工作")
    void inMemoryChunkSourceDrivesRetrievalWithoutDatabase() {
        LocalEmbeddingService embedder = new LocalEmbeddingService();

        CourseKnowledgeContextService service = new CourseKnowledgeContextService();
        CourseResourceService resourceService = Mockito.mock(CourseResourceService.class);
        ReflectionTestUtils.setField(service, "courseResourceService", resourceService);
        ReflectionTestUtils.setField(service, "wordService", new com.service.WordService());
        ReflectionTestUtils.setField(service, "embeddingService", embedder);
        // 关键：注入内存语料，且 jdbcTemplate 全程为 null
        ReflectionTestUtils.setField(service, "chunkSourceOverride",
                sampleCorpus().asChunkSource(embedder));

        when(resourceService.getByCourseid(eq(7), anyInt(), anyInt()))
                .thenReturn(List.of(new CourseResource()));

        CourseKnowledgeContextService.KnowledgeContext context =
                service.build(7, "二叉树 前序遍历");

        assertThat(context.citations()).isNotEmpty();
        assertThat(context.citations().get(0).title()).isEqualTo("二叉树");
        assertThat(context.text()).contains("资料1-1");
        assertThat(context.text()).contains("前序遍历");
    }

    @Test
    @DisplayName("asChunkSource 产出的片段按资料分组，资源 ID 与标题保持")
    void chunkSourceKeepsResourceIdentity() {
        ChunkSource source = sampleCorpus().asChunkSource(new com.service.LocalEmbeddingService());

        List<Chunk> chunks = source.load(7, List.of());

        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting(Chunk::resourceId).containsExactly(1, 1, 2);
        assertThat(chunks).extracting(Chunk::title).containsExactly("二叉树", "二叉树", "绪论");
        assertThat(chunks.get(0).score()).isZero();
        assertThat(Files.exists(tempDir)).isTrue();
    }
}
