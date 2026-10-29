package com.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.util.ObjectiveGrading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 在线答题判分。
 *
 * 从 StudentHomeworkService 拆出：判分与作业记录的增删改查是两件事，
 * 而且原先「同步提交判分」和「异步补判分」各写了一份几乎相同的流程，
 * 改一处漏一处。这里统一成 客观题判分 → AI 补主观题 → 组装评语 三步。
 */
@Service
public class ExamGradingService {

    private static final Logger logger = LoggerFactory.getLogger(ExamGradingService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 试卷统一按百分制，各题平均分配 */
    private static final BigDecimal PAPER_TOTAL = new BigDecimal("100");
    private static final int SCORE_SCALE = 2;

    @Autowired
    private ZhipuAIService zhipuAIService;

    /* ==================== 结果对象 ==================== */

    /** 客观题判分结果，同时带上供 AI 使用的简答题与错题素材 */
    public static class ObjectiveGradeResult {
        private BigDecimal objectiveScore = BigDecimal.ZERO;
        private BigDecimal objectiveTotal = BigDecimal.ZERO;
        private BigDecimal subjectiveTotal = BigDecimal.ZERO;
        private int correctCount;
        private int wrongCount;
        private int shortAnswerCount;
        private String shortAnswerInfo = "";
        private String wrongAnswerInfo = "";

        public BigDecimal getObjectiveScore() { return objectiveScore; }
        public BigDecimal getObjectiveTotal() { return objectiveTotal; }
        public BigDecimal getSubjectiveTotal() { return subjectiveTotal; }
        public int getCorrectCount() { return correctCount; }
        public int getWrongCount() { return wrongCount; }
        public int getShortAnswerCount() { return shortAnswerCount; }
        public String getShortAnswerInfo() { return shortAnswerInfo; }
        public String getWrongAnswerInfo() { return wrongAnswerInfo; }
        public boolean hasShortAnswer() { return shortAnswerCount > 0; }
    }

    /** 完整判分结果 */
    public static class ExamGradeResult {
        private final BigDecimal totalScore;
        private final String comment;
        private final boolean requiresReview;

        public ExamGradeResult(BigDecimal totalScore, String comment, boolean requiresReview) {
            this.totalScore = totalScore;
            this.comment = comment;
            this.requiresReview = requiresReview;
        }

        public BigDecimal getTotalScore() { return totalScore; }
        public String getComment() { return comment; }
        public boolean isRequiresReview() { return requiresReview; }
    }

    /* ==================== 对外入口 ==================== */

    /**
     * 完整判分：客观题自动判分 + 简答题交给 AI + 生成评语。
     *
     * @return 解析失败时返回 null，由调用方决定是留待人工批改还是报错
     */
    public ExamGradeResult gradeExam(String homeworkContent, String studentContent) {
        ObjectiveGradeResult objective = gradeObjective(homeworkContent, studentContent);
        return objective == null ? null : gradeWithAi(objective);
    }

    /**
     * 只判客观题，不调用 AI。
     * 同步提交时先出这一步的分，AI 部分可以异步补。
     */
    public ObjectiveGradeResult gradeObjective(String homeworkContent, String studentContent) {
        if (homeworkContent == null || studentContent == null) return null;

        try {
            JsonNode questions = OBJECT_MAPPER.readTree(homeworkContent).path("questions");
            JsonNode answers = OBJECT_MAPPER.readTree(studentContent).path("answers");
            if (!questions.isArray() || !answers.isObject() || questions.isEmpty()) return null;

            BigDecimal perQuestion = PAPER_TOTAL.divide(
                    new BigDecimal(questions.size()), SCORE_SCALE, RoundingMode.HALF_UP);

            ObjectiveGradeResult result = new ObjectiveGradeResult();
            StringBuilder shortAnswers = new StringBuilder();
            StringBuilder wrongAnswers = new StringBuilder();

            for (JsonNode question : questions) {
                String type = question.path("type").asText();
                JsonNode studentAnswer = answers.get(question.path("id").asText());

                if (ObjectiveGrading.isObjective(type)) {
                    result.objectiveTotal = result.objectiveTotal.add(perQuestion);
                    if (isAnswerCorrect(question, answers, type)) {
                        result.objectiveScore = result.objectiveScore.add(perQuestion);
                        result.correctCount++;
                    } else {
                        result.wrongCount++;
                        appendWrongAnswer(wrongAnswers, question, studentAnswer, type);
                    }
                } else if ("short".equals(type)) {
                    result.subjectiveTotal = result.subjectiveTotal.add(perQuestion);
                    result.shortAnswerCount++;
                    appendShortAnswer(shortAnswers, question, studentAnswer,
                            perQuestion, result.shortAnswerCount);
                }
            }

            result.shortAnswerInfo = shortAnswers.toString();
            result.wrongAnswerInfo = wrongAnswers.toString();
            return result;
        } catch (Exception e) {
            logger.error("客观题判分失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 在客观题结果之上补 AI 判分与评语。
     * AI 任一步失败都只降级为「待教师复核」，不影响已算出的客观题得分。
     */
    public ExamGradeResult gradeWithAi(ObjectiveGradeResult objective) {
        BigDecimal subjectiveScore = BigDecimal.ZERO;
        String aiComment = "";
        boolean requiresReview = false;

        if (objective.hasShortAnswer() && objective.subjectiveTotal.signum() > 0) {
            try {
                ZhipuAIService.GradeResult ai = zhipuAIService.gradeShortAnswers(
                        objective.shortAnswerInfo,
                        objective.subjectiveTotal.intValue(),
                        objective.shortAnswerCount);
                subjectiveScore = ai.getScore();
                aiComment = ai.getComment();
                requiresReview = ai.isRequiresReview();
            } catch (Exception e) {
                logger.error("AI 判简答题失败: {}", e.getMessage());
                aiComment = "简答题待老师批改";
                requiresReview = true;
            }
        }

        BigDecimal totalScore = objective.objectiveScore.add(subjectiveScore);

        String overview = null;
        try {
            overview = zhipuAIService.generateExamComment(totalScore.intValue(),
                    objective.correctCount, objective.wrongCount, objective.wrongAnswerInfo);
        } catch (Exception e) {
            logger.error("AI 生成评语失败: {}", e.getMessage());
        }

        String comment = buildComment(objective, subjectiveScore, totalScore,
                overview != null && !overview.isEmpty() ? overview : aiComment);

        return new ExamGradeResult(totalScore,
                requiresReview ? "[AI预评分·待教师复核] " + comment : comment,
                requiresReview);
    }

    /** 供错题本与考试模块复用的单题判分 */
    public boolean checkAnswerCorrect(JsonNode questionNode, JsonNode answerNodes, String type) {
        return isAnswerCorrect(questionNode, answerNodes, type);
    }

    /* ==================== 评语组装 ==================== */

    private String buildComment(ObjectiveGradeResult objective, BigDecimal subjectiveScore,
                                BigDecimal totalScore, String aiComment) {
        StringBuilder comment = new StringBuilder("[AI自动评分] ");
        comment.append("得分: ").append(scale(totalScore)).append("/100\n");

        if (objective.objectiveTotal.signum() > 0) {
            comment.append("客观题: ").append(scale(objective.objectiveScore))
                    .append("/").append(scale(objective.objectiveTotal))
                    .append(" (对").append(objective.correctCount)
                    .append("错").append(objective.wrongCount).append(")\n");
        }
        if (objective.hasShortAnswer()) {
            comment.append("简答题: ").append(scale(subjectiveScore))
                    .append("/").append(scale(objective.subjectiveTotal)).append("\n");
        }
        if (aiComment != null && !aiComment.isEmpty()) {
            comment.append(aiComment);
        }
        return comment.toString();
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    private void appendWrongAnswer(StringBuilder target, JsonNode question,
                                   JsonNode studentAnswer, String type) {
        target.append("题目: ").append(question.path("title").asText()).append("\n");
        target.append("题型: ").append(typeLabel(type)).append("\n");

        JsonNode options = question.path("options");
        if (options.isArray()) {
            target.append("选项: ");
            for (int i = 0; i < options.size(); i++) {
                target.append((char) ('A' + i)).append(".").append(options.get(i).asText()).append(" ");
            }
            target.append("\n");
        }

        target.append("正确答案: ").append(formatAnswer(question.get("answer"), type, "无")).append("\n");
        target.append("学生答案: ").append(formatAnswer(studentAnswer, type, "未作答")).append("\n\n");
    }

    private void appendShortAnswer(StringBuilder target, JsonNode question, JsonNode studentAnswer,
                                   BigDecimal score, int index) {
        target.append("题目").append(index).append("(").append(score).append("分): ")
                .append(question.path("title").asText()).append("\n");

        String reference = question.path("answer").asText("");
        if (!reference.isEmpty() && !"null".equals(reference)) {
            target.append("参考答案: ").append(reference).append("\n");
        }

        String text = studentAnswer != null && !studentAnswer.isNull() ? studentAnswer.asText() : "";
        target.append("学生答案: ").append(text).append("\n\n");
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "single" -> "单选题";
            case "multi" -> "多选题";
            case "judge" -> "判断题";
            case "blank" -> "填空题";
            case "short" -> "简答题";
            case "prog_blank" -> "程序填空题";
            case "prog" -> "程序题";
            case "code_explain" -> "代码解释题";
            default -> type;
        };
    }

    /** 标准答案与学生答案的展示格式一致，合并成一个方法 */
    private String formatAnswer(JsonNode answer, String type, String emptyLabel) {
        if (answer == null || answer.isNull()) return emptyLabel;

        switch (type) {
            case "single": {
                int index = answer.asInt(-1);
                return index >= 0 ? String.valueOf((char) ('A' + index)) : emptyLabel;
            }
            case "multi": {
                if (!answer.isArray()) return emptyLabel;
                StringBuilder text = new StringBuilder();
                for (JsonNode item : answer) {
                    if (text.length() > 0) text.append(",");
                    text.append((char) ('A' + item.asInt()));
                }
                return text.length() > 0 ? text.toString() : emptyLabel;
            }
            case "judge":
                return answer.asBoolean() ? "正确" : "错误";
            case "prog_blank": {
                if (!answer.isArray()) return answer.asText(emptyLabel);
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < answer.size(); i++) {
                    if (text.length() > 0) text.append("；");
                    text.append("空").append(i + 1).append(": ").append(answer.get(i).asText(""));
                }
                return text.length() > 0 ? text.toString() : emptyLabel;
            }
            default:
                return answer.asText(emptyLabel);
        }
    }

    /* ==================== 单题判分 ==================== */

    /** 判定细节统一收在 ObjectiveGrading，保证成绩与错题统计口径一致 */
    private boolean isAnswerCorrect(JsonNode question, JsonNode answers, String type) {
        String questionId = question.path("id").asText();
        if (questionId.isEmpty()) return false;

        return ObjectiveGrading.isCorrect(question, answers.get(questionId));
    }
}
