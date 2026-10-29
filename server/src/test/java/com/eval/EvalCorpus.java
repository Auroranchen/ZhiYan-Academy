package com.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.Chunk;
import com.rag.ChunkSource;
import com.rag.EmbeddingService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 语料快照：一份脱离数据库的课程资料切片副本。
 *
 * <p><b>为什么只存文本、不存向量：</b>向量取决于所用的 embedding 模型与维度。
 * 把向量烤进快照，换模型就得重新导出，而且「维度消融」这类实验根本做不成。
 * 改成本地存文本、**加载时按变体现场计算向量**，快照就与模型无关了——
 * 同一份语料可以直接喂给 hash 向量器或真 embedding，从而让对比只差在模型这一个变量上。
 *
 * <p>切片口径与生产完全一致（共用 {@link com.rag.TextChunker}）。
 * 若口径不同，评测测的就不是生产实际索引的东西。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvalCorpus(String version, String createdAt, List<Resource> resources) {

    public static final String CURRENT_VERSION = "corpus-v1";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resource(int resourceId, String title, String filename, List<ChunkText> chunks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChunkText(int chunkIndex, String content) {
    }

    public int chunkCount() {
        return resources.stream().mapToInt(r -> r.chunks().size()).sum();
    }

    /**
     * 转成检索用的片段来源。
     *
     * <p>接收 {@link EmbeddingService} 而不是逐条函数，是为了走**批量**接口：
     * 179 个切片逐个调用远程向量器就是 179 次 HTTP，按 64 一批只要 3 次。
     * 这与生产侧的要求是同一条（见 {@code rebuildResourceIndex}）。
     */
    public ChunkSource asChunkSource(EmbeddingService embedder) {
        return (courseId, courseResources) -> {
            List<String> texts = new ArrayList<>();
            for (Resource resource : resources) {
                for (ChunkText chunk : resource.chunks()) texts.add(chunk.content());
            }
            List<double[]> vectors = embedder.embedBatch(texts);
            if (vectors.size() != texts.size()) {
                // 数量对不上宁可失败：错位会让检索返回「命中了但内容是另一段」的结果，且永不报错
                throw new IllegalStateException("向量数 " + vectors.size()
                        + " 与切片数 " + texts.size() + " 不符，拒绝继续以免向量与内容错位");
            }
            List<Chunk> out = new ArrayList<>(texts.size());
            int i = 0;
            for (Resource resource : resources) {
                for (ChunkText chunk : resource.chunks()) {
                    out.add(new Chunk(resource.resourceId(), resource.title(), chunk.chunkIndex(),
                            chunk.content(), vectors.get(i++), 0));
                }
            }
            return out;
        };
    }

    public void writeTo(Path path) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this),
                StandardCharsets.UTF_8);
    }

    public static EvalCorpus readFrom(Path path) throws IOException {
        return MAPPER.readValue(Files.readString(path, StandardCharsets.UTF_8), EvalCorpus.class);
    }
}
