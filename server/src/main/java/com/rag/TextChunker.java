package com.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * 把课程资料正文切成检索片段——纯函数，无状态、无外部依赖。
 *
 * <p>从 {@code CourseKnowledgeContextService.split} 原样抽出。抽出来的理由是
 * <b>语料快照导出器必须与生产用完全相同的切片口径</b>：如果导出时按另一套规则切，
 * 评测测的就不是生产实际索引的东西，结论没有意义。
 */
public final class TextChunker {

    /** 生产基线值，改动前请先确认不会让既有索引失效（内容哈希是按切片算的）。 */
    public static final int DEFAULT_CHUNK_SIZE = 1000;
    public static final int DEFAULT_OVERLAP = 160;
    public static final int DEFAULT_MAX_CHUNKS = 80;

    private TextChunker() {
    }

    public static List<String> chunk(String content) {
        return chunk(content, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP, DEFAULT_MAX_CHUNKS);
    }

    /**
     * 切分规则：定长窗口 + 重叠；窗口末尾若距段落边界足够近则回退到边界，
     * 避免把一句话拦腰截断。
     */
    public static List<String> chunk(String content, int chunkSize, int overlap, int maxChunks) {
        List<String> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) return chunks;
        int start = 0;
        while (start < content.length() && chunks.size() < maxChunks) {
            int end = Math.min(content.length(), start + chunkSize);
            if (end < content.length()) {
                int paragraph = content.lastIndexOf('\n', end);
                if (paragraph > start + chunkSize / 2) end = paragraph;
            }
            String chunk = content.substring(start, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);
            if (end >= content.length()) break;
            start = Math.max(start + 1, end - overlap);
        }
        return chunks;
    }
}
