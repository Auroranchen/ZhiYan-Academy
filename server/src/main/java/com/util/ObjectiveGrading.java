package com.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Set;

/**
 * 客观题判分。
 *
 * 这段判定原先在 ExamGradingService 与 StatisticsService 里各有一份：
 * 前者决定学生的实际得分，后者决定教师看到的错题率。两份实现已经漂移——
 * 判分侧的标点归一化会去掉反斜杠而统计侧不会，编程类填空题会出现
 * 「判分算对、错题分析算错」；程序填空题更是被错题分析整个漏掉。
 *
 * 判分是唯一事实来源，统计必须与它一致，所以两边统一走这里。
 * 任何改动都会同时影响成绩与报表，务必先补测试。
 */
public final class ObjectiveGrading {

    private ObjectiveGrading() {
    }

    /** loose 档容忍的编辑距离 */
    public static final int LOOSE_EDIT_DISTANCE = 1;

    /** 可自动判分的题型；简答、程序题、代码解释题需要人工或 AI 评阅 */
    private static final Set<String> OBJECTIVE_TYPES =
            Set.of("single", "multi", "judge", "blank", "prog_blank");

    public static boolean isObjective(String type) {
        return OBJECTIVE_TYPES.contains(type);
    }

    public static String gradingPolicy(JsonNode question) {
        return question.path("gradingPolicy").asText("normal");
    }

    /**
     * 判定单题是否作答正确。
     *
     * @param question      题目节点，需含 type 与 answer
     * @param studentAnswer 该题的学生作答，为空视为未作答
     * @return 仅在明确判定正确时返回 true
     */
    public static boolean isCorrect(JsonNode question, JsonNode studentAnswer) {
        if (question == null || studentAnswer == null || studentAnswer.isNull()) {
            return false;
        }
        JsonNode correctAnswer = question.get("answer");
        if (correctAnswer == null || correctAnswer.isNull()) {
            return false;
        }

        String type = question.path("type").asText();
        switch (type) {
            case "single": {
                Integer correct = asInteger(correctAnswer);
                return correct != null && correct.equals(asInteger(studentAnswer));
            }
            case "judge": {
                Boolean correct = asBoolean(correctAnswer);
                return correct != null && correct.equals(asBoolean(studentAnswer));
            }
            case "multi": {
                Set<Integer> correct = toIntegerSet(correctAnswer);
                return !correct.isEmpty() && correct.equals(toIntegerSet(studentAnswer));
            }
            case "blank":
                return compareBlank(correctAnswer.asText(""), studentAnswer.asText(""),
                        gradingPolicy(question));
            case "prog_blank":
                return compareProgramBlanks(correctAnswer, studentAnswer, gradingPolicy(question));
            default:
                return false;
        }
    }

    /** 程序填空逐空比对，全部正确才算对 */
    public static boolean compareProgramBlanks(JsonNode correctAnswer, JsonNode studentAnswer, String policy) {
        if (correctAnswer == null || !correctAnswer.isArray() || correctAnswer.isEmpty()) {
            return false;
        }

        for (int i = 0; i < correctAnswer.size(); i++) {
            String expected = correctAnswer.get(i).asText("");
            String actual = "";
            if (studentAnswer != null && studentAnswer.isArray() && i < studentAnswer.size()) {
                actual = studentAnswer.get(i).asText("");
            } else if (studentAnswer != null && !studentAnswer.isArray() && i == 0) {
                actual = studentAnswer.asText("");
            }
            if (!compareBlank(expected, actual, policy)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 填空判分，三档宽严：
     * strict 完全一致；normal 忽略标点；loose 再忽略大小写与空格，并容忍一个字符的编辑距离。
     */
    public static boolean compareBlank(String expected, String actual, String policy) {
        if (expected == null || expected.trim().isEmpty()) {
            return false;
        }

        if ("strict".equals(policy)) {
            return normalize(expected, false, false, false)
                    .equals(normalize(actual, false, false, false));
        }
        if ("normal".equals(policy)) {
            return normalize(expected, false, false, true)
                    .equals(normalize(actual, false, false, true));
        }

        String left = normalize(expected, true, true, true);
        String right = normalize(actual, true, true, true);
        return left.equals(right) || levenshtein(left, right) <= LOOSE_EDIT_DISTANCE;
    }

    /**
     * 归一化文本。
     *
     * 这里的正则此前多写过一层转义（Java 字面量里的 \\\\s+ 实际匹配的是反斜杠加字母 s，
     * 标点字符类还因为 \\] 提前闭合而整体失效），导致 normal 与 loose 两档从未真正生效，
     * 学生多打一个空格或逗号就会被判错。改动前请先跑测试。
     */
    public static String normalize(String value, boolean ignoreCase, boolean ignoreSpace, boolean ignorePunct) {
        String text = value == null ? "" : value;
        if (ignoreCase) {
            text = text.toLowerCase();
        }
        if (ignorePunct) {
            text = text.replaceAll("[\\\\.,;:!?\\-()\\[\\]{}'\"，。；：！？、—（）【】]", "");
        }
        if (ignoreSpace) {
            text = text.replaceAll("\\s+", "");
        }
        return text.trim();
    }

    /** 两个字符串的编辑距离 */
    public static int levenshtein(String a, String b) {
        int m = a.length();
        int n = b.length();
        if (m == 0) return n;
        if (n == 0) return m;

        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[m][n];
    }

    /* ==================== JSON 取值 ==================== */

    /** 选项下标可能以数字或字符串下发，统一取整；取不出返回 null */
    public static Integer asInteger(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isNumber()) return node.asInt();
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 判断题的对错可能是布尔、0/1 或 "true"/"false"；无法判定返回 null */
    public static Boolean asBoolean(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isBoolean()) return node.asBoolean();
        if (node.isNumber()) return node.asInt() != 0;
        if (node.isTextual()) {
            String value = node.asText().trim().toLowerCase();
            if ("true".equals(value) || "1".equals(value)) return true;
            if ("false".equals(value) || "0".equals(value)) return false;
        }
        return null;
    }

    /** 多选答案可能是数组，也可能是单个值 */
    public static Set<Integer> toIntegerSet(JsonNode node) {
        Set<Integer> result = new HashSet<>();
        if (node == null || node.isNull()) return result;

        if (node.isArray()) {
            for (JsonNode item : node) {
                Integer value = asInteger(item);
                if (value != null) result.add(value);
            }
            return result;
        }

        Integer value = asInteger(node);
        if (value != null) result.add(value);
        return result;
    }
}
