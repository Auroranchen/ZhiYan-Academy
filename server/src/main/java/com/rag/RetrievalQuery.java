package com.rag;

import java.util.Set;

/**
 * 一次检索的查询侧数据：原始串、归一化串、查询向量、查询词元。
 *
 * @param normalized 归一化后的查询串，也是「精确短语命中」的比对基准
 * @param vector     查询向量，维度必须与候选片段一致（不一致的片段已在上游被跳过）
 * @param terms      查询词元，用于计算内容/标题覆盖率
 */
public record RetrievalQuery(String raw, String normalized, double[] vector, Set<String> terms) {

    /** 空查询走退化解：按片段序号排序，且不做相关性过滤。 */
    public boolean blank() {
        return raw == null || raw.isBlank();
    }
}
