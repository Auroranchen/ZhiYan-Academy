package com.rag;

import java.util.List;

/**
 * 检索策略：对候选片段打分、排序，并选出最终进入上下文的片段。
 *
 * <p>抽出这层缝的目的有两个，缺一不可：
 * <ol>
 *   <li><b>消融</b>——不同权重、不同 Top-K、纯语义 / 纯关键词都能作为变体跑，
 *       从而回答「混合召回里每一项各自贡献了什么」；</li>
 *   <li><b>可离线评测</b>——策略是纯函数式的（候选进来、片段出去），
 *       配合 {@link ChunkSource} 就能在**没有数据库、没有 Spring** 的情况下复现检索。</li>
 * </ol>
 *
 * <p>实现必须保持确定性：同样的候选与查询必须给出同样的顺序，
 * 否则变体之间的指标差异会混入随机噪声。
 */
public interface RetrievalStrategy {

    /** 变体标识，会写进评测结果，便于对照。 */
    String name();

    /**
     * @param candidates 候选片段，{@code score} 字段未使用（恒为 0）
     * @param query      查询侧数据
     * @param config     本次检索的参数
     * @return 已排序、已筛选的片段，顺序即最终进入上下文的顺序
     */
    List<Chunk> rank(List<Chunk> candidates, RetrievalQuery query, RetrievalConfig config);
}
