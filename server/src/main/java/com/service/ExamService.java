package com.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mapper.ExamMapper;
import com.mapper.ExamRecordMapper;
import com.model.Exam;
import com.model.ExamRecord;
import com.util.ObjectiveGrading;
import com.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 在线考试业务层：建考、发布、学生作答、自动判分、防作弊统计。
 */
@Service
public class ExamService {

    private static final Logger logger = LoggerFactory.getLogger(ExamService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private ExamRecordMapper examRecordMapper;

    @Autowired
    private ExamGradingService examGradingService;

    @Autowired
    private GamificationService gamificationService;

    // ---------------- 教师端 ----------------

    public Result<Exam> create(Exam exam) {
        if (exam.getTitle() == null || exam.getTitle().trim().isEmpty()) {
            return Result.error("考试标题不能为空");
        }
        if (exam.getCourseid() == null) {
            return Result.error("请选择课程");
        }
        if (exam.getStatus() == null) exam.setStatus(1);
        if (exam.getAntiCheat() == null) exam.setAntiCheat(1);
        if (exam.getDuration() == null) exam.setDuration(60);
        if (exam.getTotalScore() == null) exam.setTotalScore(computeTotalScore(exam.getContent()));
        examMapper.insert(exam);
        return Result.success("考试创建成功", exam);
    }

    public Result<Exam> update(Exam exam) {
        if (exam.getExamid() == null) {
            return Result.error("缺少考试ID");
        }
        if (exam.getTotalScore() == null) exam.setTotalScore(computeTotalScore(exam.getContent()));
        examMapper.update(exam);
        return Result.success("更新成功", exam);
    }

    public Result<Void> delete(Integer examid) {
        examMapper.delete(examid);
        return Result.successMsg("删除成功");
    }

    public Result<Void> updateStatus(Integer examid, Integer status) {
        examMapper.updateStatus(examid, status);
        return Result.successMsg("状态已更新");
    }

    public Exam getById(Integer examid) {
        return examMapper.findById(examid);
    }

    public List<Exam> listByTeacher(Integer teacherid, String keyword, int page, int pageSize) {
        return examMapper.findByTeacher(teacherid, keyword, (page - 1) * pageSize, pageSize);
    }

    public Long countByTeacher(Integer teacherid, String keyword) {
        return examMapper.countByTeacher(teacherid, keyword);
    }

    public List<ExamRecord> examResults(Integer examid, String keyword) {
        return examRecordMapper.findByExam(examid, keyword);
    }

    public Map<String, Object> examStats(Integer examid) {
        return examRecordMapper.statsByExam(examid);
    }

    public List<Map<String, Object>> getUnsubmittedStudents(Integer examid) {
        return examMapper.findUnsubmittedStudents(examid);
    }

    // ---------------- 学生端 ----------------

    public List<Exam> studentExams(Integer studentid) {
        return examMapper.findAvailableForStudent(studentid);
    }

    /**
     * 开始/继续考试：校验时间窗口，创建或返回作答记录，返回去除答案的试卷。
     */
    public Result<Map<String, Object>> startExam(Integer examid, Integer studentid) {
        Exam exam = examMapper.findById(examid);
        if (exam == null) {
            return Result.error("考试不存在");
        }
        if (exam.getStatus() == null || exam.getStatus() != 1) {
            return Result.error("该考试当前不可参加");
        }
        Date now = new Date();
        if (exam.getStarttime() != null && now.before(exam.getStarttime())) {
            return Result.error("考试尚未开始");
        }

        ExamRecord record = examRecordMapper.findByExamAndStudent(examid, studentid);
        if (record != null && record.getStatus() != null && record.getStatus() >= 1) {
            return Result.error("您已提交本场考试，不能重复作答");
        }
        boolean continuing = record != null && record.getStatus() != null && record.getStatus() == 0;
        // 截止后仅允许继续已开考未交卷的记录（剩余 0 秒由客户端触发自动交卷）
        if (exam.getEndtime() != null && now.after(exam.getEndtime()) && !continuing) {
            return Result.error("考试已截止");
        }
        if (record == null) {
            record = new ExamRecord();
            record.setExamid(examid);
            record.setStudentid(studentid);
            record.setStatus(0);
            record.setStarttime(now);
            record.setSwitchCount(0);
            record.setUsedSeconds(0);
            examRecordMapper.insert(record);
        }

        // 去除答案后的试卷
        exam.setContent(sanitizeContent(exam.getContent()));

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("exam", exam);
        data.put("record", record);
        // 计算剩余秒数（基于本次开始时间 + 时长，并受截止时间约束）
        long remaining = computeRemainingSeconds(exam, record);
        data.put("remainingSeconds", remaining);
        return Result.success(data);
    }

    /**
     * 自动保存进度（不判分）。
     */
    public Result<Void> saveProgress(Integer examid, Integer studentid, String answers, Integer switchCount) {
        ExamRecord record = examRecordMapper.findByExamAndStudent(examid, studentid);
        if (record == null || (record.getStatus() != null && record.getStatus() >= 1)) {
            return Result.error("无可保存的作答记录");
        }
        record.setAnswers(answers);
        if (switchCount != null) record.setSwitchCount(switchCount);
        record.setStatus(0);
        examRecordMapper.update(record);
        return Result.success();
    }

    /**
     * 交卷并自动判分。
     */
    public Result<ExamRecord> submitExam(Integer examid, Integer studentid, String answers,
                                         Integer switchCount, Integer usedSeconds, Integer autoSubmit) {
        Exam exam = examMapper.findById(examid);
        if (exam == null) {
            return Result.error("考试不存在");
        }
        ExamRecord record = examRecordMapper.findByExamAndStudent(examid, studentid);
        if (record == null) {
            record = new ExamRecord();
            record.setExamid(examid);
            record.setStudentid(studentid);
            record.setStarttime(new Date());
            examRecordMapper.insert(record);
        }
        if (record.getStatus() != null && record.getStatus() >= 1) {
            return Result.error("请勿重复交卷");
        }

        GradeOutcome outcome = gradeObjective(exam.getContent(), answers);
        record.setAnswers(answers);
        record.setObjectiveScore(outcome.objectiveScore);
        record.setScore(outcome.objectiveScore);
        record.setStatus(2);
        record.setSubmittime(new Date());
        record.setUsedSeconds(usedSeconds != null ? usedSeconds : 0);
        record.setSwitchCount(switchCount != null ? switchCount : 0);
        record.setAutoSubmit(autoSubmit != null ? autoSubmit : 0);
        record.setAiComment(outcome.comment);
        examRecordMapper.update(record);

        // 发放积分
        try {
            gamificationService.onExamSubmit(studentid, outcome.objectiveScore.doubleValue(),
                    exam.getTotalScore() != null ? exam.getTotalScore() : 100);
        } catch (Exception ignored) {
        }

        record.setExamtitle(exam.getTitle());
        record.setExamTotalScore(exam.getTotalScore());
        return Result.success("交卷成功", record);
    }

    public ExamRecord getRecord(Integer examid, Integer studentid) {
        return examRecordMapper.findByExamAndStudent(examid, studentid);
    }

    @Autowired
    private ZhipuAIService zhipuAIService;

    @Autowired
    private AiSafeCallService aiSafeCallService;

    /**
     * 教师评阅：更新记录总分和评语。
     */
    public Result<Void> reviewRecord(Integer recordId, Double score, String comment) {
        ExamRecord record = examRecordMapper.findById(recordId);
        if (record == null) return Result.error("作答记录不存在");
        record.setScore(BigDecimal.valueOf(score));
        if (comment != null) record.setAiComment(comment);
        record.setStatus(2);
        examRecordMapper.update(record);
        return Result.successMsg("评阅成功");
    }

    /**
     * AI 辅助评阅单道主观题，返回建议评分和点评。
     */
    public Result<String> aiReviewQuestion(Map<String, String> params) {
        String title = params.getOrDefault("title", "");
        String type = params.getOrDefault("type", "short");
        String code = params.getOrDefault("code", "");
        String refAnswer = params.getOrDefault("refAnswer", "");
        String studentAnswer = params.getOrDefault("studentAnswer", "");
        String maxScore = params.getOrDefault("maxScore", "10");

        StringBuilder sb = new StringBuilder();
        sb.append("你是一位严谨的编程/学科教师，正在批改学生的考试试卷。\n");
        sb.append("请根据参考答案对学生的作答进行评阅。\n");
        sb.append("输出要求：\n");
        sb.append("1. 建议得分（满分 ").append(maxScore).append(" 分），格式：【建议得分：X分】\n");
        sb.append("2. 简短点评（2-3句话），指出优点和不足\n");
        sb.append("3. 如果代码有语法或逻辑错误，具体指出\n\n");

        sb.append("题型：").append(getTypeLabel(type)).append("\n");
        sb.append("题目：").append(title).append("\n");
        if (!code.isEmpty()) sb.append("题目代码：\n").append(code).append("\n");
        if (!refAnswer.isEmpty()) sb.append("参考答案：\n").append(refAnswer).append("\n");
        sb.append("学生作答：\n").append(studentAnswer).append("\n");

        try {
            // 走安全网关：这是计费调用，此前完全不出现在 ai_call_log 里
            String result = aiSafeCallService.callText(
                "exam.aiReview",
                "你是一位专业的编程教师，评阅时客观公正，给出具体的评分理由。用中文回答。",
                sb.toString()
            );
            return Result.success(result);
        } catch (Exception e) {
            logger.warn("AI评阅失败: {}", e.getMessage());
            return Result.error("AI评阅暂时不可用: " + e.getMessage());
        }
    }

    private String getTypeLabel(String type) {
        switch (type) {
            case "prog": return "程序题";
            case "code_explain": return "代码解释题";
            case "short": return "简答题";
            case "prog_blank": return "程序填空题";
            default: return type;
        }
    }

    // ---------------- 内部工具 ----------------

    private static class GradeOutcome {
        BigDecimal objectiveScore = BigDecimal.ZERO;
        int correct = 0;
        int total = 0;
        String comment = "";
    }

    private GradeOutcome gradeObjective(String examContent, String answersJson) {
        GradeOutcome out = new GradeOutcome();
        try {
            JsonNode questions = MAPPER.readTree(examContent).path("questions");
            JsonNode answerNodes = parseAnswers(answersJson);
            if (!questions.isArray() || answerNodes == null) {
                out.comment = "未检测到有效作答";
                return out;
            }
            int objCount = 0, objCorrect = 0, shortCount = 0;
            for (JsonNode q : questions) {
                String type = q.path("type").asText();
                BigDecimal qScore = BigDecimal.valueOf(q.path("score").asDouble(0));
                if (ObjectiveGrading.isObjective(type)) {
                    objCount++;
                    out.total++;
                    if (examGradingService.checkAnswerCorrect(q, answerNodes, type)) {
                        objCorrect++;
                        out.correct++;
                        out.objectiveScore = out.objectiveScore.add(qScore);
                    }
                } else {
                    shortCount++;
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append("客观题：答对 ").append(objCorrect).append("/").append(objCount)
              .append("，得分 ").append(out.objectiveScore.stripTrailingZeros().toPlainString());
            if (shortCount > 0) {
                sb.append("；另有 ").append(shortCount).append(" 道主观题需教师评阅。");
            }
            out.comment = sb.toString();
        } catch (Exception e) {
            logger.warn("考试判分失败: {}", e.getMessage());
            out.comment = "判分异常";
        }
        return out;
    }

    /** 兼容两种作答格式：直接的 {qid:val} 或包了一层 {"answers":{...}} */
    private JsonNode parseAnswers(String answersJson) {
        if (answersJson == null || answersJson.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(answersJson);
            if (node.has("answers") && node.get("answers").isObject()) {
                return node.get("answers");
            }
            return node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 去除试题中的标准答案与判分策略，避免泄题。 */
    private String sanitizeContent(String content) {
        if (content == null) return null;
        try {
            JsonNode root = MAPPER.readTree(content);
            JsonNode questions = root.path("questions");
            if (questions.isArray()) {
                for (JsonNode q : questions) {
                    if (q instanceof ObjectNode) {
                        ((ObjectNode) q).remove("answer");
                        ((ObjectNode) q).remove("gradingPolicy");
                    }
                }
            }
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            logger.warn("试卷脱敏失败: {}", e.getMessage());
            return content;
        }
    }

    private int computeTotalScore(String content) {
        try {
            JsonNode questions = MAPPER.readTree(content).path("questions");
            double sum = 0;
            if (questions.isArray()) {
                for (JsonNode q : questions) {
                    sum += q.path("score").asDouble(0);
                }
            }
            return (int) Math.round(sum);
        } catch (Exception e) {
            return 100;
        }
    }

    private long computeRemainingSeconds(Exam exam, ExamRecord record) {
        long byDuration = Long.MAX_VALUE;
        if (exam.getDuration() != null && record.getStarttime() != null) {
            long elapsed = (System.currentTimeMillis() - record.getStarttime().getTime()) / 1000;
            byDuration = (long) exam.getDuration() * 60 - elapsed;
        }
        long byDeadline = Long.MAX_VALUE;
        if (exam.getEndtime() != null) {
            byDeadline = (exam.getEndtime().getTime() - System.currentTimeMillis()) / 1000;
        }
        long remaining = Math.min(byDuration, byDeadline);
        return Math.max(0, remaining);
    }
}
