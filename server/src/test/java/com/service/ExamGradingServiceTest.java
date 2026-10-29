package com.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 判分逻辑测试。
 *
 * 重点覆盖填空题的三档宽严：normal 与 loose 此前因为正则多写了一层转义而完全失效，
 * 学生多打一个空格或逗号就被判错，需要用例把这个行为钉住。
 */
class ExamGradingServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExamGradingService grading;

    @BeforeEach
    void setUp() {
        grading = new ExamGradingService();
    }

    /** 构造一道题与一份作答，返回是否判对 */
    private boolean judge(String questionJson, String answerJson) throws Exception {
        JsonNode question = MAPPER.readTree(questionJson);
        JsonNode answers = MAPPER.readTree("{\"q1\":" + answerJson + "}");
        return grading.checkAnswerCorrect(question, answers, question.path("type").asText());
    }

    private String blank(String answer, String policy) {
        return "{\"id\":\"q1\",\"type\":\"blank\",\"answer\":\"" + answer
                + "\",\"gradingPolicy\":\"" + policy + "\"}";
    }

    @Nested
    @DisplayName("填空题")
    class Blank {

        @Test
        @DisplayName("strict：一字不差才算对")
        void strict() throws Exception {
            assertTrue(judge(blank("int a;", "strict"), "\"int a;\""));
            assertFalse(judge(blank("int a;", "strict"), "\"INT a;\""));
            assertFalse(judge(blank("int a;", "strict"), "\"int a\""));
        }

        @Test
        @DisplayName("normal：忽略标点，保留大小写与空格")
        void normalIgnoresPunctuation() throws Exception {
            assertTrue(judge(blank("return 0;", "normal"), "\"return 0\""));
            assertTrue(judge(blank("数组，链表", "normal"), "\"数组链表\""));
            assertTrue(judge(blank("read-only", "normal"), "\"readonly\""));
            assertFalse(judge(blank("return 0;", "normal"), "\"RETURN 0\""));
        }

        @Test
        @DisplayName("loose：再忽略大小写与空格，并容忍一个字符的差异")
        void looseIgnoresCaseAndSpace() throws Exception {
            assertTrue(judge(blank("int a = 1;", "loose"), "\"int a=1\""));
            assertTrue(judge(blank("快速排序", "loose"), "\"快速 排序\""));
            assertTrue(judge(blank("HashMap", "loose"), "\"hashmap\""));
            assertTrue(judge(blank("binary", "loose"), "\"binry\""));
            assertFalse(judge(blank("binary", "loose"), "\"bnry\""));
        }

        @Test
        @DisplayName("标准答案为空时判错，交由人工处理")
        void missingCorrectAnswer() throws Exception {
            assertFalse(judge(blank("   ", "normal"), "\"任意\""));
        }
    }

    @Nested
    @DisplayName("客观题")
    class Objective {

        @Test
        @DisplayName("单选：数字与字符串下标都能比对")
        void single() throws Exception {
            String q = "{\"id\":\"q1\",\"type\":\"single\",\"answer\":2}";
            assertTrue(judge(q, "2"));
            assertTrue(judge(q, "\"2\""));
            assertFalse(judge(q, "1"));
            assertFalse(judge(q, "null"));
        }

        @Test
        @DisplayName("多选：与顺序无关，少选多选都判错")
        void multi() throws Exception {
            String q = "{\"id\":\"q1\",\"type\":\"multi\",\"answer\":[0,2]}";
            assertTrue(judge(q, "[2,0]"));
            assertFalse(judge(q, "[0]"));
            assertFalse(judge(q, "[0,1,2]"));
        }

        @Test
        @DisplayName("判断：接受布尔、数字与字符串写法")
        void judgeType() throws Exception {
            String q = "{\"id\":\"q1\",\"type\":\"judge\",\"answer\":true}";
            assertTrue(judge(q, "true"));
            assertTrue(judge(q, "\"true\""));
            assertTrue(judge(q, "1"));
            assertFalse(judge(q, "false"));
        }

        @Test
        @DisplayName("程序填空：所有空都对才算对，漏填判错")
        void progBlank() throws Exception {
            String q = "{\"id\":\"q1\",\"type\":\"prog_blank\",\"answer\":[\"int\",\"i++\"],"
                    + "\"gradingPolicy\":\"loose\"}";
            assertTrue(judge(q, "[\"int\",\"i++\"]"));
            assertFalse(judge(q, "[\"int\",\"i--\"]"));
            assertFalse(judge(q, "[\"int\"]"));
        }

        @Test
        @DisplayName("简答题不参与自动判分")
        void shortAnswerIsNotAutoGraded() throws Exception {
            assertFalse(judge("{\"id\":\"q1\",\"type\":\"short\",\"answer\":\"随便\"}", "\"随便\""));
        }
    }

    @Nested
    @DisplayName("整卷客观题判分")
    class Paper {

        private static final String PAPER = """
                {"questions":[
                  {"id":"q1","type":"single","title":"单选","answer":0,"options":["A","B"]},
                  {"id":"q2","type":"single","title":"单选二","answer":1,"options":["A","B"]},
                  {"id":"q3","type":"short","title":"简答"}
                ]}""";

        @Test
        @DisplayName("百分制按题数平均分配，只统计客观题")
        void scoresAreEvenlySplit() {
            var result = grading.gradeObjective(PAPER,
                    "{\"answers\":{\"q1\":0,\"q2\":0,\"q3\":\"作答\"}}");

            assertNotNull(result);
            assertEquals(1, result.getCorrectCount());
            assertEquals(1, result.getWrongCount());
            assertEquals(1, result.getShortAnswerCount());
            // 3 题各 33.33 分，客观题满分 66.66，答对一题
            assertEquals(0, result.getObjectiveScore().compareTo(new BigDecimal("33.33")));
            assertEquals(0, result.getObjectiveTotal().compareTo(new BigDecimal("66.66")));
        }

        @Test
        @DisplayName("错题素材带上题干与正确答案，供 AI 生成解析")
        void collectsWrongAnswerContext() {
            var result = grading.gradeObjective(PAPER,
                    "{\"answers\":{\"q1\":1,\"q2\":1}}");

            assertNotNull(result);
            assertTrue(result.getWrongAnswerInfo().contains("单选"));
            assertTrue(result.getWrongAnswerInfo().contains("正确答案: A"));
            assertTrue(result.getWrongAnswerInfo().contains("学生答案: B"));
        }

        @Test
        @DisplayName("内容无法解析时返回 null，由调用方转人工批改")
        void returnsNullOnUnparseableContent() {
            assertNull(grading.gradeObjective(null, "{}"));
            assertNull(grading.gradeObjective("不是 JSON", "{}"));
            assertNull(grading.gradeObjective("{\"questions\":[]}", "{\"answers\":{}}"));
        }
    }
}
