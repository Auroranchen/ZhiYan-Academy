package com.rag;

/**
 * 课程资料的检索片段。
 *
 * <p>从 {@code CourseKnowledgeContextService} 的私有嵌套 record 提升为顶层类型，
 * 因为检索策略需要看到它——这是把打分逻辑抽成可替换策略的前提。
 *
 * @param score 检索得分。仅由 {@link RetrievalStrategy} 写入，来源数据里恒为 0。
 */
public record Chunk(Integer resourceId, String title, int index, String content,
                    double[] embedding, double score) {

    public Chunk withScore(double newScore) {
        return new Chunk(resourceId, title, index, content, embedding, newScore);
    }
}
