package com.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 提示词是产品的教学策略与合规约束，改动会直接改变 AI 的输出行为。
 * 这些用例锁住其中不能丢的硬性条款。
 */
class AiPromptsTest {

    @Nested
    @DisplayName("题目讲解的合规模式")
    class Explain {

        @Test
        @DisplayName("受限模式必须明确禁止泄露答案")
        void restrictedForbidsAnswers() {
            String prompt = AiPrompts.explain(true);

            assertTrue(prompt.contains("不得代做作业"), "缺少不得代做作业的硬性约束");
            assertTrue(prompt.contains("禁止输出或可推导出本题标准答案"), "缺少禁止推导答案的约束");
            assertTrue(prompt.contains("errorAnalysis：固定为空字符串"), "受限模式不应评析学生答案");
        }

        @Test
        @DisplayName("完整模式面向已提交作答，可以纠错")
        void fullModeAllowsFeedback() {
            String prompt = AiPrompts.explain(false);

            assertTrue(prompt.contains("已提交本题作答"));
            assertTrue(prompt.contains("errorAnalysis"));
            assertFalse(prompt.contains("不得代做作业"), "完整模式不应带受限约束");
        }

        @Test
        @DisplayName("两种模式都要求返回同一套 JSON 字段")
        void bothDeclareSameSchema() {
            for (boolean restricted : new boolean[] { true, false }) {
                String prompt = AiPrompts.explain(restricted);
                for (String field : new String[] {
                        "keyPoints", "errorAnalysis", "steps", "referenceCode", "commonMistakes", "expansion" }) {
                    assertTrue(prompt.contains(field), "模式 " + restricted + " 缺少字段 " + field);
                }
            }
        }
    }

    @Nested
    @DisplayName("自动出题")
    class GenerateQuestions {

        @Test
        @DisplayName("两种出题方式共用同一套题型取值约定")
        void shareTypeContract() {
            for (boolean fromContent : new boolean[] { true, false }) {
                String prompt = AiPrompts.generateQuestions(fromContent);
                for (String type : new String[] { "single", "multi", "judge", "blank", "short" }) {
                    assertTrue(prompt.contains('"' + type + '"'), "缺少题型 " + type);
                }
                assertTrue(prompt.contains("不要用 boolean"), "应显式禁止模型使用 boolean 作为判断题类型");
            }
        }

        @Test
        @DisplayName("按内容出题时要求不得超出给定范围")
        void contentModeStaysInScope() {
            assertTrue(AiPrompts.generateQuestions(true).contains("不要自行扩展范围"));
        }

        @Test
        @DisplayName("按主题出题时要求严格遵守题型数量")
        void topicModeEnforcesCounts() {
            assertTrue(AiPrompts.generateQuestions(false).contains("不能多也不能少"));
        }
    }

    @Nested
    @DisplayName("判分提示词")
    class Grading {

        @Test
        @DisplayName("简答题满分会写进提示词，约束模型不要超分")
        void shortAnswerCarriesMaxScore() {
            String prompt = AiPrompts.gradeShortAnswers(40, 4);

            assertTrue(prompt.contains("满分40分"));
            assertTrue(prompt.contains("共4道简答题"));
            assertTrue(prompt.contains("0-40的整数"), "缺少对分数上限的约束");
        }

        @Test
        @DisplayName("百分号不会被 String.format 吃掉")
        void percentSignsSurviveFormatting() {
            String prompt = AiPrompts.gradeShortAnswers(30, 3);

            assertTrue(prompt.contains("（40%）"));
            assertTrue(prompt.contains("（30%）"));
        }

        @Test
        @DisplayName("作业判分声明四项评分维度与 JSON 结构")
        void homeworkDeclaresRubric() {
            String prompt = AiPrompts.GRADE_HOMEWORK;

            assertTrue(prompt.contains("内容完整性"));
            assertTrue(prompt.contains("内容准确性"));
            assertTrue(prompt.contains("表达清晰度"));
            assertTrue(prompt.contains("创新性"));
            assertTrue(prompt.contains("confidence"), "缺少置信度字段，低置信时无法转人工复核");
        }
    }
}
