package com.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalEmbeddingServiceTest {

    private final LocalEmbeddingService embeddings = new LocalEmbeddingService();

    @Test
    void similarChineseTextHasHigherCosineScore() {
        double[] query = embeddings.embed("二叉树前序遍历根左右");
        double related = embeddings.cosine(query, embeddings.embed("二叉树的前序遍历先访问根节点再访问左右子树"));
        double unrelated = embeddings.cosine(query, embeddings.embed("关系数据库事务隔离级别与并发控制"));

        assertTrue(related > unrelated);
        assertTrue(related > 0);
    }

    @Test
    void vectorJsonRoundTripPreservesSimilarity() {
        double[] vector = embeddings.embed("快速排序分治思想");
        double[] restored = embeddings.fromJson(embeddings.toJson(vector));
        assertEquals(LocalEmbeddingService.DIMENSIONS, restored.length);
        assertEquals(1.0, embeddings.cosine(vector, restored), 0.0001);
    }

    @Test
    void contentHashIsStable() {
        assertEquals(embeddings.contentHash("课程资料"), embeddings.contentHash("课程资料"));
        assertNotEquals(embeddings.contentHash("课程资料"), embeddings.contentHash("其他资料"));
    }
}
