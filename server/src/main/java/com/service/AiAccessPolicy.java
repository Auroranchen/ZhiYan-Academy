package com.service;

import com.model.StudentHomework;

/**
 * 教学场景下的 AI 访问策略判定——全系统**唯一**的定义源。
 *
 * <p><b>为什么单独抽一个类：</b>这些判定原先散落在 {@code AiExplainService} 里且是私有的，
 * 于是 {@code AIController} 没法复用，只好用「请求体里的 systemPrompt 是否含某个中文短语」
 * 来判断作业模式——那等于把防线交给它本该约束的那一方来开关。
 * 判定口径收敛到这里之后，讲解与对话走的是同一套规则。
 *
 * <p><b>铁律：</b>本类的所有输入都必须来自**服务端状态**（数据库里的提交记录），
 * 禁止接收任何来自请求体的字符串作为判定依据。
 */
public final class AiAccessPolicy {

    private AiAccessPolicy() {
    }

    /**
     * 作业是否已被学生提交。
     *
     * <p>注意「未提交」在数据模型里由**提交行缺失**表示（没有任何一行 status=0 的记录），
     * 所以传 null 进来就是未提交，返回 false。
     *
     * @param sh 学生作业提交记录，可为 null
     * @return status &gt;= 1（已提交/待批改/已批改/已打回）时为 true
     */
    public static boolean homeworkSubmitted(StudentHomework sh) {
        Integer st = sh != null ? sh.getStatus() : null;
        return st != null && st >= 1;
    }

    /** 占位或空作答，视同「未完成本题」触发题库受限策略 */
    public static boolean looksLikePlaceholderAnswer(String raw) {
        if (raw == null) return true;
        String t = raw.trim();
        if (t.isEmpty()) return true;
        if ("未作答".equals(t)) return true;
        if ("未提交内容".equals(t)) return true;
        if ("null".equalsIgnoreCase(t)) return true;
        if ("undefined".equalsIgnoreCase(t)) return true;
        if ("{}".equals(t) || "[]".equals(t)) return true;
        return false;
    }

    /** 本题是否有可追溯的有效作答尝试 */
    public static boolean hasMeaningfulStudentAttempt(String studentAnswer) {
        return !looksLikePlaceholderAnswer(studentAnswer);
    }

    /**
     * 是否与题库对齐的受限讲解：未提交作业，或本题未作答时不透露标准答案。
     * 供 {@code AiExplainService} 使用。
     */
    public static boolean useRestrictedExplain(StudentHomework sh, String studentAnswer) {
        return !homeworkSubmitted(sh) || !hasMeaningfulStudentAttempt(studentAnswer);
    }

    /**
     * 作业辅导对话是否应处于受限（加固）状态。
     *
     * <p>对话与讲解的差别：对话没有「某一题」的概念，所以只看作业整体是否已提交。
     * 只要还没提交，学生此时索要答案即违规，必须加固。
     *
     * <p>**fail-closed**：拿不到提交记录（null）一律判定为受限。
     */
    public static boolean useRestrictedChat(StudentHomework sh) {
        return !homeworkSubmitted(sh);
    }
}
