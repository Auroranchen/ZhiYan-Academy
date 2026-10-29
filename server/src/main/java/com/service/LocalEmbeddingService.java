package com.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 零外部依赖的本地特征哈希向量器。
 * 中文使用字符二元组，英文使用词元；用于课程私有资料的向量召回。
 * 后续可在不改变检索接口的情况下替换为讯飞或其他 Embedding 模型。
 */
/**
 * 注意：本类**不是** Spring Bean。容器里唯一的 {@code EmbeddingService} 由
 * {@code EmbeddingConfig} 装配（本地哈希或智谱二选一），避免出现两个同类型 Bean
 * 导致注入歧义。测试仍可直接 {@code new LocalEmbeddingService()} 使用。
 */
public class LocalEmbeddingService implements com.rag.EmbeddingService {

    static final int DIMENSIONS = 384;

    /**
     * 标记列取值。换用真 embedding 后这个串会变，从而触发索引重建——
     * 这正是它存在的意义，不要为了「稳定」而把它写死。
     */
    public static final String MODEL_ID = "local-hash-384";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public String modelId() {
        return MODEL_ID;
    }

    public double[] embed(String text) {
        double[] vector = new double[DIMENSIONS];
        String normalized = normalize(text);
        List<String> features = features(normalized);
        for (String feature : features) {
            int hash = stableHash(feature);
            int index = Math.floorMod(hash, DIMENSIONS);
            vector[index] += (hash & 1) == 0 ? 1.0 : -1.0;
        }
        normalize(vector);
        return vector;
    }

    public double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length != right.length) return 0;
        double dot = 0;
        for (int i = 0; i < left.length; i++) dot += left[i] * right[i];
        return dot;
    }

    public String toJson(double[] vector) {
        try { return objectMapper.writeValueAsString(vector); }
        catch (Exception e) { return "[]"; }
    }

    public double[] fromJson(String json) {
        try {
            List<Double> values = objectMapper.readValue(json, new TypeReference<List<Double>>() {});
            double[] result = new double[values.size()];
            for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
            return result;
        } catch (Exception e) {
            return new double[0];
        }
    }

    /**
     * @deprecated 内容指纹与向量器无关，已移到 {@link com.rag.ContentHash}。
     *             保留此方法仅为兼容既有调用点，新代码请直接用 ContentHash。
     */
    @Deprecated
    public String contentHash(String text) {
        return com.rag.ContentHash.of(text);
    }

    private List<String> features(String text) {
        List<String> result = new ArrayList<>();
        String[] words = text.split("[^\\p{L}\\p{N}]+");
        for (String word : words) {
            if (!word.isBlank()) result.add("w:" + word);
        }
        String compact = text.replaceAll("[^\\p{IsHan}\\p{L}\\p{N}]", "");
        for (int i = 0; i < compact.length() - 1; i++) {
            result.add("b:" + compact.substring(i, i + 2));
        }
        return result;
    }

    private int stableHash(String value) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x01000193;
        }
        return hash;
    }

    private String normalize(String text) {
        return String.valueOf(text).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private void normalize(double[] vector) {
        double norm = 0;
        for (double value : vector) norm += value * value;
        if (norm == 0) return;
        norm = Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) vector[i] /= norm;
    }
}
