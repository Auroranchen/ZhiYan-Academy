package com.rag;

/**
 * 检索参数。
 *
 * <p>把这些原先硬编码在类里的常量提出来，是为了让**消融实验**成为可能：
 * 权重、Top-K、每资料片段上限、噪声门槛都可以按变体注入，而不是改代码重编译。
 *
 * <p>{@link #defaults()} 的取值必须与重构前逐字节一致——它是所有对比实验的基线。
 */
public record RetrievalConfig(
        double weightSemantic,
        double weightCoverage,
        double weightTitle,
        double weightExactPhrase,
        int topK,
        int maxChunksPerResource,
        double minRelevanceScore) {

    /** 重构前的原始取值，作为基线变体 V0。 */
    public static RetrievalConfig defaults() {
        return new RetrievalConfig(0.55, 0.30, 0.10, 0.05, 5, 2, 0.08);
    }

    public RetrievalConfig withWeights(double semantic, double coverage, double title, double exactPhrase) {
        return new RetrievalConfig(semantic, coverage, title, exactPhrase,
                topK, maxChunksPerResource, minRelevanceScore);
    }

    public RetrievalConfig withTopK(int newTopK) {
        return new RetrievalConfig(weightSemantic, weightCoverage, weightTitle, weightExactPhrase,
                newTopK, maxChunksPerResource, minRelevanceScore);
    }

    /**
     * 换噪声门槛。
     *
     * <p>这个值**必须随向量器重新校准**：哈希向量的无关文本余弦接近 0，
     * 真实 embedding 的无关文本余弦可达 0.4–0.7。沿用同一门槛会让它在其中一种下彻底失效。
     */
    public RetrievalConfig withMinRelevanceScore(double newMin) {
        return new RetrievalConfig(weightSemantic, weightCoverage, weightTitle, weightExactPhrase,
                topK, maxChunksPerResource, newMin);
    }

    /** 只保留关键词覆盖，忽略向量相似度——用于回答「embedding 相比关键词检索赚到了什么」。 */
    public RetrievalConfig lexicalOnly() {
        return new RetrievalConfig(0.0, 0.30, 0.10, 0.05,
                topK, maxChunksPerResource, minRelevanceScore);
    }

    /** 只保留向量相似度。 */
    public RetrievalConfig semanticOnly() {
        return new RetrievalConfig(1.0, 0.0, 0.0, 0.0,
                topK, maxChunksPerResource, minRelevanceScore);
    }
}
