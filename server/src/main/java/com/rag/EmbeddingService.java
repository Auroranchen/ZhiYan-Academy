package com.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量化服务。
 *
 * <p>本接口存在的意义是把「用哪种向量」从检索逻辑里剥离出来——这是做消融实验的前提：
 * 同一份语料、同一套检索参数，只换向量器，差异才归因得清。
 *
 * <p><b>{@link #modelId()} 与 {@link #dimensions()} 不是装饰性的。</b>
 * 它们会被写进 {@code course_knowledge_chunk} 的标记列，参与索引失效判断。
 * 少了这个标记，换向量器不会触发重建索引——旧向量被静默沿用，维度不匹配的片段
 * 在检索时被逐行跳过，最终退化成「每次请求都重新解析文件并重新向量化」。
 * 这个故障**不会报错**，只会让系统变慢。
 */
public interface EmbeddingService {

    /** 单条向量化。 */
    double[] embed(String text);

    /**
     * 批量向量化。默认逐个调用；远程实现应覆写为真正的批量请求——
     * 一份 80 切片 × 12 资料的课程若逐个远程调用，就是一个事务里 960 次串行 HTTP。
     */
    default List<double[]> embedBatch(List<String> texts) {
        List<double[]> out = new ArrayList<>(texts.size());
        for (String text : texts) out.add(embed(text));
        return out;
    }

    /** 向量维度。会写进索引标记列，用于识别失效的旧向量。 */
    int dimensions();

    /**
     * 向量器标识（模型名 + 维度足够）。
     * 会写进索引标记列；只要它变了，索引就必须重建。
     */
    String modelId();

    /** 两个向量的相似度。默认按点积算——各实现都应产出已归一化的向量。 */
    default double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length != right.length) return 0;
        double dot = 0;
        for (int i = 0; i < left.length; i++) dot += left[i] * right[i];
        return dot;
    }

    /** 向量序列化为可入库的字符串。 */
    String toJson(double[] vector);

    /** 从库中反序列化；失败应返回空数组而不是抛异常，由调用方按维度不匹配跳过。 */
    double[] fromJson(String json);
}
