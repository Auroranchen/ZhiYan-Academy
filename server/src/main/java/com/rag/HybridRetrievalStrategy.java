package com.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 混合召回：向量相似度负责模糊匹配，词元覆盖与标题命中负责精确概念。
 *
 * <p><b>本类的算术是从 {@code CourseKnowledgeContextService} 原样搬过来的</b>，
 * 没有做任何「顺手优化」。基线的逐位可复现比代码好看重要得多——
 * 一旦这里变了，所有消融对比都会失去参照。
 * {@code RetrievalCharacterizationTest} 用精确到 4 位小数的分数钉住了这一点。
 */
public class HybridRetrievalStrategy implements RetrievalStrategy {

    /** 向量相似度。用函数式接口而不是直接依赖具体的向量实现，便于评测替换。 */
    @FunctionalInterface
    public interface VectorSimilarity {
        double between(double[] left, double[] right);
    }

    private final VectorSimilarity similarity;
    private final String name;

    public HybridRetrievalStrategy(VectorSimilarity similarity) {
        this(similarity, "hybrid");
    }

    public HybridRetrievalStrategy(VectorSimilarity similarity, String name) {
        this.similarity = similarity;
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Chunk> rank(List<Chunk> candidates, RetrievalQuery query, RetrievalConfig config) {
        List<Chunk> ranked = new ArrayList<>(candidates.size());
        for (Chunk chunk : candidates) {
            double score = query.blank()
                    ? 1.0 / (1 + chunk.index())
                    : score(query, chunk, config);
            ranked.add(chunk.withScore(score));
        }
        ranked.sort(Comparator.comparingDouble(Chunk::score).reversed());
        return select(ranked, query.blank(), config);
    }

    /** 打分公式，与重构前逐字一致。 */
    private double score(RetrievalQuery query, Chunk chunk, RetrievalConfig config) {
        double semantic = Math.max(0, similarity.between(query.vector(), chunk.embedding()));
        Set<String> contentTerms = RetrievalText.searchTerms(RetrievalText.normalizeForSearch(chunk.content()));
        Set<String> titleTerms = RetrievalText.searchTerms(RetrievalText.normalizeForSearch(chunk.title()));

        long contentHits = query.terms().stream().filter(contentTerms::contains).count();
        long titleHits = query.terms().stream().filter(titleTerms::contains).count();
        double coverage = query.terms().isEmpty() ? 0 : (double) contentHits / query.terms().size();
        double titleCoverage = query.terms().isEmpty() ? 0 : (double) titleHits / query.terms().size();
        boolean exactPhrase = query.normalized().length() >= 2
                && RetrievalText.normalizeForSearch(chunk.content()).contains(query.normalized());

        return semantic * config.weightSemantic()
                + coverage * config.weightCoverage()
                + titleCoverage * config.weightTitle()
                + (exactPhrase ? config.weightExactPhrase() : 0);
    }

    /** 控制每份资料最多命中若干片段，并丢弃低相关噪声；空查询不做噪声过滤。 */
    private List<Chunk> select(List<Chunk> ranked, boolean blankQuery, RetrievalConfig config) {
        List<Chunk> selected = new ArrayList<>();
        Map<Integer, Integer> resourceCounts = new HashMap<>();
        for (Chunk chunk : ranked) {
            if (!blankQuery && chunk.score() < config.minRelevanceScore()) continue;
            if (chunk.resourceId() != null) {
                int count = resourceCounts.getOrDefault(chunk.resourceId(), 0);
                if (count >= config.maxChunksPerResource()) continue;
                resourceCounts.put(chunk.resourceId(), count + 1);
            }
            selected.add(chunk);
            if (selected.size() >= config.topK()) break;
        }
        return selected;
    }
}
