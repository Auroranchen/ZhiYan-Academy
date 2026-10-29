package com.util;

import java.util.HashSet;
import java.util.Set;

/**
 * 文本相似度计算。
 *
 * 查重的判定依据原先是 PlagiarismService 里的四个私有方法，和数据库读写、
 * 报告落库缠在一起，改一次归一化规则就得连带跑通整条链路，也没法单独验证。
 * 算法集中在这里，PlagiarismService 只负责取数据、比对、存报告。
 *
 * 判定会直接落到「疑似抄袭」名单上，任何改动都应先补测试再动实现。
 */
public final class TextSimilarity {

    private TextSimilarity() {
    }

    /** 字符 n-gram 的长度。改动会使历史报告的相似度不可比。 */
    public static final int SHINGLE = 3;

    /**
     * 归一化：转小写并去掉空白与中英文标点。
     *
     * 抄袭者常改动标点和空格，保留这些字符会让相似度虚低。
     *
     * @param s 原始文本，可为 null
     * @return 归一化后的文本，null 输入返回空串
     */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase()
                .replaceAll("[\\s\\p{Punct}，。；：！？、—（）【】“”‘’《》]", "");
    }

    /**
     * 切分字符 n-gram 集合。
     *
     * 短于一个 gram 的文本整体作为一个元素，避免返回空集导致相似度恒为 0。
     *
     * @param text 已归一化的文本
     * @return n-gram 集合，空文本返回空集
     */
    public static Set<String> shingles(String text) {
        Set<String> set = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return set;
        }
        if (text.length() < SHINGLE) {
            set.add(text);
            return set;
        }
        for (int i = 0; i + SHINGLE <= text.length(); i++) {
            set.add(text.substring(i, i + SHINGLE));
        }
        return set;
    }

    /**
     * Jaccard 系数：交集大小除以并集大小。
     *
     * @return 0~1，任一集合为空时返回 0
     */
    public static double jaccard(Set<String> a, Set<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> smaller = a.size() < b.size() ? a : b;
        Set<String> larger = a.size() < b.size() ? b : a;

        int inter = 0;
        for (String s : smaller) {
            if (larger.contains(s)) {
                inter++;
            }
        }
        int union = a.size() + b.size() - inter;
        return union == 0 ? 0 : (double) inter / union;
    }

    /**
     * 两段原始文本的相似度百分比，保留一位小数。
     *
     * @return 0~100
     */
    public static double similarityPercent(String a, String b) {
        double sim = jaccard(shingles(normalize(a)), shingles(normalize(b)));
        return Math.round(sim * 1000) / 10.0;
    }
}
