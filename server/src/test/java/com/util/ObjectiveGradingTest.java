package com.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客观题判分是成绩与错题率的共同依据。
 * 这里既覆盖各题型的判定，也钉住「能自动判分的题型必须能进错题统计」这条一致性约束。
 */
@DisplayName("客观题判分")
class ObjectiveGradingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String raw) throws Exception {
        return MAPPER.readTree(raw);
    }

    /** 造一道题并判定给定作答是否正确 */
    private static boolean judge(String questionJson, String answerJson) throws Exception {
        return ObjectiveGrading.isCorrect(json(questionJson), json(answerJson));
    }

    private static String blank(String answer, String policy) {
        return "{\"type\":\"blank\",\"answer\":\"" + answer + "\",\"gradingPolicy\":\"" + policy + "\"}";
    }

    @Nested
    @DisplayName("可自动判分的题型")
    class ObjectiveTypes {

        @Test
        @DisplayName("五种客观题都算，主观题不算")
        void coverage() {
            for (String type : new String[]{"single", "multi", "judge", "blank", "prog_blank"}) {
                assertTrue(ObjectiveGrading.isObjective(type), type);
            }
            for (String type : new String[]{"short", "prog", "code_explain", "", "unknown"}) {
                assertFalse(ObjectiveGrading.isObjective(type), type);
            }
        }

        @Test
        @DisplayName("程序填空题必须纳入：它能自动判分，就该出现在错题分析里")
        void programBlankCountsAsObjective() {
            assertTrue(ObjectiveGrading.isObjective("prog_blank"));
        }
    }

    @Nested
    @DisplayName("单选题")
    class Single {

        private static final String Q = "{\"type\":\"single\",\"answer\":2}";

        @Test
        @DisplayName("下标一致才算对")
        void matchesIndex() throws Exception {
            assertTrue(judge(Q, "2"));
            assertFalse(judge(Q, "1"));
        }

        @Test
        @DisplayName("作答以字符串下发时同样识别")
        void acceptsStringIndex() throws Exception {
            assertTrue(judge(Q, "\"2\""));
            assertTrue(judge(Q, "\" 2 \""));
        }

        @Test
        @DisplayName("未作答或答案不可解析都算错")
        void missingIsWrong() throws Exception {
            assertFalse(judge(Q, "null"));
            assertFalse(judge(Q, "\"\""));
            assertFalse(judge(Q, "\"B\""));
        }

        @Test
        @DisplayName("题目没有标准答案时不能判对")
        void noCorrectAnswer() throws Exception {
            assertFalse(judge("{\"type\":\"single\",\"answer\":null}", "0"));
            assertFalse(judge("{\"type\":\"single\"}", "0"));
        }
    }

    @Nested
    @DisplayName("多选题")
    class Multi {

        private static final String Q = "{\"type\":\"multi\",\"answer\":[0,2]}";

        @Test
        @DisplayName("选项集合完全一致才算对，与顺序无关")
        void exactSet() throws Exception {
            assertTrue(judge(Q, "[0,2]"));
            assertTrue(judge(Q, "[2,0]"));
            assertFalse(judge(Q, "[0]"));
            assertFalse(judge(Q, "[0,1,2]"));
        }

        @Test
        @DisplayName("重复选项不影响判定")
        void ignoresDuplicates() throws Exception {
            assertTrue(judge(Q, "[0,2,2]"));
        }

        @Test
        @DisplayName("标准答案为空集时不能判对，避免全员算对")
        void emptyCorrectSet() throws Exception {
            assertFalse(judge("{\"type\":\"multi\",\"answer\":[]}", "[]"));
        }
    }

    @Nested
    @DisplayName("判断题")
    class Judge {

        @Test
        @DisplayName("布尔、0/1 与字符串三种写法都能识别")
        void acceptsSeveralShapes() throws Exception {
            String q = "{\"type\":\"judge\",\"answer\":true}";

            assertTrue(judge(q, "true"));
            assertTrue(judge(q, "1"));
            assertTrue(judge(q, "\"true\""));
            assertFalse(judge(q, "false"));
            assertFalse(judge(q, "0"));
        }

        @Test
        @DisplayName("答错误的题同样能判对")
        void falseAnswer() throws Exception {
            assertTrue(judge("{\"type\":\"judge\",\"answer\":false}", "false"));
        }

        @Test
        @DisplayName("无法判定真假的作答算错")
        void unparsable() throws Exception {
            assertFalse(judge("{\"type\":\"judge\",\"answer\":true}", "\"大概吧\""));
        }
    }

    @Nested
    @DisplayName("填空题三档宽严")
    class Blank {

        @Test
        @DisplayName("strict：一字不差")
        void strict() throws Exception {
            assertTrue(judge(blank("int a;", "strict"), "\"int a;\""));
            assertFalse(judge(blank("int a;", "strict"), "\"INT a;\""));
            assertFalse(judge(blank("int a;", "strict"), "\"int a\""));
        }

        @Test
        @DisplayName("normal：忽略标点，保留大小写")
        void normal() throws Exception {
            assertTrue(judge(blank("return 0;", "normal"), "\"return 0\""));
            assertTrue(judge(blank("数组，链表", "normal"), "\"数组链表\""));
            assertFalse(judge(blank("return", "normal"), "\"RETURN\""));
        }

        @Test
        @DisplayName("loose：再忽略大小写与空格，并容忍一个字符的增删改")
        void loose() throws Exception {
            assertTrue(judge(blank("public static", "loose"), "\"PublicStatic\""));
            assertTrue(judge(blank("length", "loose"), "\"lengh\""));
            assertTrue(judge(blank("length", "loose"), "\"lengthh\""));
            assertTrue(judge(blank("length", "loose"), "\"lengtn\""));
            assertFalse(judge(blank("length", "loose"), "\"len\""));
        }

        @Test
        @DisplayName("已知局限：相邻字符打反算两处编辑，loose 也判错")
        void looseRejectsTransposition() throws Exception {
            // 普通编辑距离把换位记为一删一增；要容忍这类手误得换成
            // Damerau-Levenshtein，但那会放宽所有历史试卷的判分口径
            assertFalse(judge(blank("length", "loose"), "\"lenght\""));
            assertEquals(2, ObjectiveGrading.levenshtein("length", "lenght"));
        }

        @Test
        @DisplayName("标准答案为空时不能判对")
        void emptyExpected() throws Exception {
            assertFalse(judge(blank("", "loose"), "\"\""));
            assertFalse(judge(blank("   ", "normal"), "\"   \""));
        }

        @Test
        @DisplayName("缺省判分策略按 normal 处理")
        void defaultPolicy() throws Exception {
            assertTrue(judge("{\"type\":\"blank\",\"answer\":\"return 0;\"}", "\"return 0\""));
        }

        @Test
        @DisplayName("反斜杠被一并归一化，编程类答案不会因转义符判错")
        void stripsBackslash() throws Exception {
            // 统计侧原先的实现不去反斜杠，同一份作答会与成绩打架
            assertTrue(judge(blank("a\\\\b", "normal"), "\"ab\""));
        }
    }

    @Nested
    @DisplayName("程序填空题")
    class ProgramBlank {

        private static final String Q =
                "{\"type\":\"prog_blank\",\"answer\":[\"int\",\"i++\"],\"gradingPolicy\":\"normal\"}";

        @Test
        @DisplayName("每个空都对才算对")
        void allBlanksMustMatch() throws Exception {
            assertTrue(judge(Q, "[\"int\",\"i++\"]"));
            assertFalse(judge(Q, "[\"int\",\"i--\"]"));
        }

        @Test
        @DisplayName("少填一个空算错，不能按已填部分给分")
        void missingBlank() throws Exception {
            assertFalse(judge(Q, "[\"int\"]"));
            assertFalse(judge(Q, "[]"));
        }

        @Test
        @DisplayName("单空题允许直接给字符串作答")
        void singleBlankAcceptsScalar() throws Exception {
            String q = "{\"type\":\"prog_blank\",\"answer\":[\"int\"],\"gradingPolicy\":\"normal\"}";

            assertTrue(judge(q, "\"int\""));
        }

        @Test
        @DisplayName("标准答案不是数组或为空时不能判对")
        void malformedCorrectAnswer() throws Exception {
            assertFalse(judge("{\"type\":\"prog_blank\",\"answer\":[]}", "[]"));
            assertFalse(judge("{\"type\":\"prog_blank\",\"answer\":\"int\"}", "\"int\""));
        }
    }

    @Nested
    @DisplayName("边界与工具")
    class Edges {

        @Test
        @DisplayName("主观题一律不自动判对，交给人工或 AI")
        void subjectiveNeverAutoCorrect() throws Exception {
            assertFalse(judge("{\"type\":\"short\",\"answer\":\"随便\"}", "\"随便\""));
            assertFalse(judge("{\"type\":\"prog\",\"answer\":\"code\"}", "\"code\""));
        }

        @Test
        @DisplayName("题目或作答缺失时安全返回错，不抛异常")
        void nullSafe() throws Exception {
            assertFalse(ObjectiveGrading.isCorrect(null, json("1")));
            assertFalse(ObjectiveGrading.isCorrect(json("{\"type\":\"single\",\"answer\":1}"), null));
        }

        @Test
        @DisplayName("编辑距离对称且识别增删改")
        void levenshtein() {
            assertEquals(0, ObjectiveGrading.levenshtein("abc", "abc"));
            assertEquals(1, ObjectiveGrading.levenshtein("abc", "abd"));
            assertEquals(1, ObjectiveGrading.levenshtein("abc", "ab"));
            assertEquals(3, ObjectiveGrading.levenshtein("", "abc"));
            assertEquals(
                    ObjectiveGrading.levenshtein("kitten", "sitting"),
                    ObjectiveGrading.levenshtein("sitting", "kitten"));
        }

        @Test
        @DisplayName("归一化按开关逐项生效")
        void normalize() {
            assertEquals("Abc", ObjectiveGrading.normalize(" Abc ", false, false, false));
            assertEquals("abc", ObjectiveGrading.normalize("Abc", true, false, false));
            assertEquals("ab", ObjectiveGrading.normalize("a b", false, true, false));
            assertEquals("ab", ObjectiveGrading.normalize("a,b", false, false, true));
            assertEquals("", ObjectiveGrading.normalize(null, true, true, true));
        }
    }
}
