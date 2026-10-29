package com.rag;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 检索用的文本归一化与切词——纯函数，无状态。
 *
 * <p>从 {@code CourseKnowledgeContextService} 抽出来，是为了让检索策略能够独立复用：
 * 打分逻辑需要和调用方用**完全相同**的切词口径，否则「关键词覆盖率」这一项就失去意义。
 */
public final class RetrievalText {

    private RetrievalText() {
    }

    /** 查询归一化：小写、压缩空白。 */
    public static String normalizeForSearch(String text) {
        if (text == null) return "";
        return text.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /**
     * 切出用于覆盖率计算的词元：中文使用二元组，英文/数字保留完整词元。
     *
     * <p>无外部依赖，与 {@code LocalEmbeddingService} 的特征口径一致。
     */
    public static Set<String> searchTerms(String text) {
        Set<String> terms = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return terms;
        for (String word : text.split("[^\\p{L}\\p{N}]+")) {
            if (!word.isBlank() && !containsHan(word)) terms.add(word);
        }
        String han = text.replaceAll("[^\\p{IsHan}]", "");
        if (han.length() == 1) terms.add(han);
        for (int i = 0; i < han.length() - 1; i++) terms.add(han.substring(i, i + 2));
        return terms;
    }

    public static boolean containsHan(String value) {
        return value != null && value.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }
}
