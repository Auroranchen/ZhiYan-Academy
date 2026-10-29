package com.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repository.LearningProfileRepository;
import com.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话式学习画像服务。
 * 摒弃传统表单，通过自然语言对话自动抽取学习者特征，构建并持续更新包含 6 个维度的动态学生画像：
 *  知识基础 / 认知风格 / 易错点偏好 / 学习目标 / 学习节奏 / 兴趣方向。
 * 画像随对话与学情数据「随学随新」，并为多智能体资源生成提供个性化依据。
 */
@Service
public class LearningProfileService {

    private static final Logger logger = LoggerFactory.getLogger(LearningProfileService.class);

    private static final int MAX_PAGE_SIZE = 100;

    /** 一次最多推 3 个相关资源，再多学生也看不过来 */
    private static final int MAX_RECOMMENDATIONS = 3;

    /** 画像 6 维度（key -> 中文名） */
    public static final String[][] DIMENSIONS = {
            {"knowledgeBase", "知识基础"},
            {"cognitiveStyle", "认知风格"},
            {"errorPreference", "易错点偏好"},
            {"learningGoal", "学习目标"},
            {"learningPace", "学习节奏"},
            {"interest", "兴趣方向"}
    };

    /** 仅用于读取学情统计；画像相关四张表的读写一律走仓储 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LearningProfileRepository learningProfileRepository;

    @Autowired
    private AiSafeCallService aiSafeCallService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 读取画像 ====================

    @SuppressWarnings("unchecked")
    public Map<String, Object> getProfile(Integer studentid) {
        Map<String, Object> out = new HashMap<>();

        Map<String, Object> row = learningProfileRepository.findProfile(studentid).orElse(null);
        if (row != null) {
            Object profileJson = row.get("profileJson");
            if (profileJson != null) {
                try {
                    out.put("dimensions", objectMapper.readValue(profileJson.toString(), Map.class));
                } catch (Exception e) {
                    // 历史数据可能不是合法 JSON，退化成空画像而不是整体失败
                    logger.warn("画像内容解析失败 student={}: {}", studentid, e.getMessage());
                }
            }
            out.put("summary", row.get("summary"));
            out.put("updateTime", String.valueOf(row.get("updateTime")));
        }

        if (!out.containsKey("dimensions")) out.put("dimensions", emptyDimensions());
        out.put("dimensionMeta", DIMENSIONS);
        return out;
    }

    private Map<String, Object> emptyDimensions() {
        Map<String, Object> dims = new HashMap<>();
        for (String[] d : DIMENSIONS) {
            Map<String, Object> v = new HashMap<>();
            v.put("name", d[1]);
            v.put("score", 0);
            v.put("label", "待完善");
            v.put("note", "");
            dims.put(d[0], v);
        }
        return dims;
    }

    // ==================== 对话式画像构建/更新 ====================

    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> chat(Integer studentid, List<Map<String, Object>> history, String message) {
        if (message == null || message.trim().isEmpty()) return Result.error("请输入内容");

        String name = strOr(queryString("SELECT studentname FROM student WHERE studentid=?", studentid), "同学");
        // 当前画像 + 学情数据，作为「随学随新」的事实依据
        Map<String, Object> current = getProfile(studentid);
        String facts = buildFacts(studentid, name);

        StringBuilder conv = new StringBuilder();
        if (history != null) {
            int from = Math.max(0, history.size() - 8); // 仅保留最近 8 轮，控制上下文
            for (int i = from; i < history.size(); i++) {
                Map<String, Object> m = history.get(i);
                String role = String.valueOf(m.get("role"));
                String content = String.valueOf(m.get("content"));
                conv.append("student".equals(role) || "user".equals(role) ? "学生：" : "助手：")
                        .append(content).append("\n");
            }
        }
        conv.append("学生：").append(message.trim()).append("\n");

        String dimDesc = "knowledgeBase=知识基础, cognitiveStyle=认知风格(如视觉型/实践型/理论型), " +
                "errorPreference=易错点偏好(常在哪类问题出错), learningGoal=学习目标, " +
                "learningPace=学习节奏(快/稳/慢, 时间投入), interest=兴趣方向";

        String system = "你是「学习画像助手 · 小枢」，通过自然、亲切的对话了解学习者，并持续完善其学习画像。\n" +
                "每轮你要做两件事：(1) 用一句到两句自然口语回应学生，并适当追问 1 个有助于完善画像的问题；" +
                "(2) 基于「已有画像 + 学情数据 + 本轮对话」更新 6 维画像。\n" +
                "6 个维度：" + dimDesc + "。\n" +
                "每个维度给出 score(0-100 的完善/掌握程度, 信息越充分或基础越好分越高)、label(4-8字概括)、note(简短依据)。\n" +
                "严格只输出 JSON：{\"reply\":\"对学生说的话(可含1个追问)\",\"summary\":\"40字内画像概述\"," +
                "\"dimensions\":{\"knowledgeBase\":{\"name\":\"知识基础\",\"score\":0,\"label\":\"\",\"note\":\"\"}," +
                "\"cognitiveStyle\":{...},\"errorPreference\":{...},\"learningGoal\":{...},\"learningPace\":{...},\"interest\":{...}}}。\n" +
                "不要编造学生没提到也无数据支撑的信息；信息不足的维度可沿用已有值或给低分并说明。";

        String user = "学习者：" + name + "\n\n【学情数据】\n" + facts +
                "\n【已有画像】\n" + safeJson(current.get("dimensions")) +
                "\n\n【最近对话】\n" + conv;

        Map<String, Object> parsed = aiSafeCallService.callJson("profile.chat", system, user);
        if (parsed == null) return Result.error("画像助手解析失败，请重试");

        String reply = AiAssistContentPolicy.filterOutput(strOr(asText(parsed.get("reply")), "我们继续聊聊你的学习情况吧～"));
        String summary = AiAssistContentPolicy.filterOutput(asText(parsed.get("summary")));
        Object dims = parsed.get("dimensions");
        if (!(dims instanceof Map)) dims = current.get("dimensions");

        // 持久化（随学随新）
        learningProfileRepository.saveProfile(studentid, safeJson(dims), summary);

        Map<String, Object> out = new HashMap<>();
        out.put("reply", reply);
        out.put("summary", summary);
        out.put("dimensions", dims);
        return Result.success(out);
    }

    public Result<Void> reset(Integer studentid) {
        return learningProfileRepository.deleteProfile(studentid)
                ? Result.success()
                : Result.error("重置失败");
    }

    // ==================== 学习效果评估 → 动态调整 ====================

    /**
     * 基于学情数据与画像评估学习效果，给出多维评估与「下一步重点 / 推荐学习动作」，
     * 并把调整后的重点写回画像，实现「评估 → 动态调整」闭环。
     */
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> evaluate(Integer studentid, Integer courseid) {
        long t0 = System.currentTimeMillis();
        String name = strOr(queryString("SELECT studentname FROM student WHERE studentid=?", studentid), "同学");
        String coursename = courseid != null
                ? strOr(queryString("SELECT coursename FROM course WHERE courseid=?", courseid), "课程") : null;
        String facts = buildFacts(studentid, name);
        Map<String, Object> current = getProfile(studentid);
        double preMastery = latestMastery(studentid, courseid);

        String system = "你是学习效果评估智能体。请基于学情数据与已有画像，对学习者近期的学习效果做多维评估，" +
                "并据此动态给出下一步学习重点与推荐动作。\n" +
                "严格只输出 JSON：{\"mastery\":0-100整体掌握度,\"trend\":\"上升|平稳|下降\"," +
                "\"dimensions\":[{\"name\":\"维度(如知识掌握/练习正确率/学习投入/错题攻克)\",\"score\":0-100,\"comment\":\"简评\"}]," +
                "\"risks\":[\"风险点\"],\"focusPoints\":[\"接下来应重点突破的知识点/方向\"]," +
                "\"actions\":[{\"action\":\"建议动作\",\"resourceType\":\"讲义|思维导图|练习题|拓展阅读|代码实操\"}]," +
                "\"summary\":\"一句话评估结论\"}。focusPoints 控制 2-4 个，actions 3-5 条。不要编造数据中没有的信息。";
        String user = "学习者：" + name + (coursename != null ? ("\n课程：" + coursename) : "") +
                "\n\n【学情数据】\n" + facts + "\n【已有画像】\n" + safeJson(current.get("dimensions"));

        Map<String, Object> parsed = aiSafeCallService.callJson("profile.evaluate", system, user);
        if (parsed == null) return Result.error("评估智能体解析失败，请重试");

        // 统一写回掌握度与重点，便于多智能体后续按新画像生成资源
        boolean profilePersisted = false;
        try {
            Object fp = parsed.get("focusPoints");
            Map<String, Object> dims = (Map<String, Object>) current.get("dimensions");
            double mastery = number(parsed.get("mastery"));
            Object kb = dims.get("knowledgeBase");
            if (kb instanceof Map) {
                Map<String, Object> knowledge = (Map<String, Object>) kb;
                double oldScore = number(knowledge.get("score"));
                double merged = oldScore <= 0 ? mastery : Math.round((oldScore * 0.7 + mastery * 0.3) * 10) / 10.0;
                knowledge.put("score", Math.round(merged));
                knowledge.put("lastEvaluationMastery", mastery);
                knowledge.put("note", "最近综合评估掌握度 " + mastery + "%");
            }
            if (fp instanceof List && !((List<?>) fp).isEmpty()) {
                Object ep = dims.get("errorPreference");
                if (ep instanceof Map) {
                    List<String> pts = new ArrayList<>();
                    for (Object o : (List<Object>) fp) pts.add(String.valueOf(o));
                    ((Map<String, Object>) ep).put("note", "评估重点：" + String.join("、", pts));
                }
            }
            profilePersisted = learningProfileRepository.saveProfile(
                    studentid, safeJson(dims), asText(parsed.get("summary")));
        } catch (Exception e) {
            logger.warn("综合评估写回画像失败: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>(parsed);
        result.put("learner", name);
        if (coursename != null) result.put("course", coursename);
        result.put("preMastery", preMastery);
        result.put("elapsedMs", System.currentTimeMillis() - t0);
        Integer evaluationId = saveEvaluation(studentid, courseid, null, "profile",
                preMastery, number(parsed.get("mastery")), null, null, result,
                parsed.get("focusPoints"), parsed.get("actions"));
        if (evaluationId == null || !profilePersisted) return Result.error("评估已生成，但结果保存失败，请稍后重试");
        result.put("evaluationId", evaluationId);
        result.put("persisted", true);
        return Result.success(result);
    }

    // ==================== 资源包练习 → 评估 → 画像/路径/推荐闭环 ====================

    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> evaluateResource(Integer studentid, Integer resourceId, List<Object> answers) {
        if (resourceId == null) return Result.error("资源包ID不能为空");
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT course_id AS courseId, title, pack_json AS packJson FROM learning_resource " +
                            "WHERE id=? AND owner_type='student' AND owner_id=?",
                    resourceId, studentid);
        } catch (Exception e) {
            return Result.error("读取资源包失败");
        }
        if (rows.isEmpty()) return Result.error(403, "资源不存在或无权评估");

        Map<String, Object> row = rows.get(0);
        Integer courseId = row.get("courseId") instanceof Number ? ((Number) row.get("courseId")).intValue() : null;
        Map<String, Object> pack;
        try {
            pack = objectMapper.readValue(String.valueOf(row.get("packJson")), Map.class);
        } catch (Exception e) {
            return Result.error("资源包数据损坏");
        }
        Object quizObj = pack.get("quiz");
        List<Map<String, Object>> questions = new ArrayList<>();
        if (quizObj instanceof Map && ((Map<?, ?>) quizObj).get("questions") instanceof List) {
            for (Object q : (List<?>) ((Map<?, ?>) quizObj).get("questions")) {
                if (q instanceof Map) questions.add((Map<String, Object>) q);
            }
        }
        if (questions.isEmpty()) return Result.error("资源包没有可评估练习");

        List<Map<String, Object>> details = new ArrayList<>();
        List<String> focusPoints = new ArrayList<>();
        int correct = 0;
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> q = questions.get(i);
            String type = asText(q.get("type"));
            String expected = normalizeAnswer(q.get("answer"), type);
            String actual = normalizeAnswer(answers != null && i < answers.size() ? answers.get(i) : null, type);
            boolean right = !expected.isEmpty() && expected.equals(actual);
            if (right) correct++;
            else {
                String point = asText(q.get("knowledge")).trim();
                if (point.isEmpty()) {
                    point = asText(q.get("title")).replaceAll("<[^>]+>", "").trim();
                    if (point.length() > 24) point = point.substring(0, 24) + "…";
                }
                if (!point.isEmpty() && !focusPoints.contains(point)) focusPoints.add(point);
            }
            Map<String, Object> detail = new HashMap<>();
            detail.put("index", i);
            detail.put("correct", right);
            detail.put("answer", actual);
            detail.put("expected", expected);
            detail.put("knowledge", q.get("knowledge"));
            detail.put("analysis", q.get("analysis"));
            details.add(detail);
        }
        double mastery = Math.round(correct * 1000.0 / questions.size()) / 10.0;
        double preMastery = latestMastery(studentid, courseId);
        List<Map<String, Object>> adaptivePath = adaptivePath(focusPoints, mastery);
        List<Map<String, Object>> recommendations = recommendResources(studentid, courseId, resourceId, focusPoints);
        Integer adaptivePlanId = persistAdaptivePlan(studentid, courseId, adaptivePath, focusPoints, mastery);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resourceId", resourceId);
        result.put("resourceTitle", row.get("title"));
        result.put("preMastery", preMastery);
        result.put("mastery", mastery);
        result.put("trend", mastery > preMastery + 3 ? "上升" : mastery < preMastery - 3 ? "下降" : "平稳");
        result.put("correctCount", correct);
        result.put("totalCount", questions.size());
        result.put("focusPoints", focusPoints);
        result.put("details", details);
        result.put("adaptivePath", adaptivePath);
        result.put("adaptivePlanId", adaptivePlanId);
        result.put("recommendations", recommendations);
        result.put("summary", focusPoints.isEmpty()
                ? "本轮练习已全部掌握，建议进入迁移应用阶段。"
                : "已根据错题自动更新画像，并重排后续学习重点。");

        boolean profileUpdated = updateProfileFromPractice(studentid, mastery, focusPoints);
        boolean attemptSaved = saveAttempt(studentid, resourceId, courseId, answers, mastery, correct, questions.size(), details);
        Integer evaluationId = saveEvaluation(studentid, courseId, resourceId, "resource_quiz",
                preMastery, mastery, correct, questions.size(), result, focusPoints,
                Map.of("adaptivePath", adaptivePath, "recommendations", recommendations));
        if (!profileUpdated || !attemptSaved || evaluationId == null || adaptivePlanId == null) {
            return Result.error("评估已计算，但学习记录保存失败，请稍后重试");
        }
        result.put("evaluationId", evaluationId);
        result.put("persisted", true);
        return Result.success(result);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> evaluationHistory(Integer studentid, Integer courseid) {
        List<Map<String, Object>> history = learningProfileRepository.listEvaluations(studentid, courseid);

        // focus_points 以 JSON 字符串存储，前端要的是数组
        for (Map<String, Object> item : history) {
            Object points = item.get("focusPoints");
            if (points == null) continue;
            try {
                item.put("focusPoints", objectMapper.readValue(points.toString(), List.class));
            } catch (Exception e) {
                item.put("focusPoints", new ArrayList<>());
            }
        }
        return history;
    }

    /** 资源包练习作答历史（分页，仅当前学生） */
    public Map<String, Object> practiceAttemptHistory(Integer studentid, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        Map<String, Object> result = new HashMap<>();
        result.put("page", safePage);
        result.put("pageSize", safeSize);
        result.put("total", learningProfileRepository.countAttempts(studentid));
        result.put("list", learningProfileRepository.listAttempts(studentid, safeSize, (safePage - 1) * safeSize));
        return result;
    }

    @SuppressWarnings("unchecked")
    private boolean updateProfileFromPractice(Integer studentid, double mastery, List<String> focusPoints) {
        try {
            Map<String, Object> profile = getProfile(studentid);
            Map<String, Object> dims = (Map<String, Object>) profile.get("dimensions");
            Object kb = dims.get("knowledgeBase");
            if (kb instanceof Map) {
                Map<String, Object> knowledge = (Map<String, Object>) kb;
                double oldScore = number(knowledge.get("score"));
                double mergedScore = oldScore <= 0 ? mastery : Math.round((oldScore * 0.7 + mastery * 0.3) * 10) / 10.0;
                knowledge.put("score", Math.round(mergedScore));
                knowledge.put("lastPracticeMastery", mastery);
                knowledge.put("label", mergedScore >= 85 ? "掌握良好" : mergedScore >= 60 ? "稳步提升" : "需要巩固");
                knowledge.put("note", "最近资源包练习正确率 " + mastery + "%");
            }
            Object ep = dims.get("errorPreference");
            if (ep instanceof Map) {
                ((Map<String, Object>) ep).put("note", focusPoints.isEmpty()
                        ? "最近练习未发现新增薄弱点"
                        : "当前重点：" + String.join("、", focusPoints));
            }
            String summary = focusPoints.isEmpty()
                    ? "近期练习掌握良好，建议进行迁移应用"
                    : "已根据最近练习更新重点：" + String.join("、", focusPoints);
            return learningProfileRepository.saveProfile(studentid, safeJson(dims), summary);
        } catch (Exception e) {
            logger.warn("练习后更新学习画像失败: {}", e.getMessage());
            return false;
        }
    }

    private List<Map<String, Object>> adaptivePath(List<String> focusPoints, double mastery) {
        List<Map<String, Object>> path = new ArrayList<>();
        if (!focusPoints.isEmpty()) {
            path.add(pathStage("回看讲解", "优先复习 " + String.join("、", focusPoints), "讲义/动画微课"));
            path.add(pathStage("针对训练", "每个薄弱点完成3-5道同类题并订正", "练习题"));
        }
        path.add(pathStage(mastery >= 85 ? "迁移应用" : "再次测评",
                mastery >= 85 ? "完成一个综合案例，检验知识迁移能力" : "完成下一轮短测，目标正确率达到80%",
                mastery >= 85 ? "代码实操/实践项目" : "练习题"));
        return path;
    }

    private Map<String, Object> pathStage(String title, String action, String resourceType) {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("title", title);
        stage.put("action", action);
        stage.put("resourceType", resourceType);
        return stage;
    }

    private Integer persistAdaptivePlan(Integer studentid, Integer courseid,
                                        List<Map<String, Object>> adaptivePath,
                                        List<String> focusPoints, double mastery) {
        List<Map<String, Object>> stages = new ArrayList<>();
        for (Map<String, Object> item : adaptivePath) {
            Map<String, Object> stage = new LinkedHashMap<>();
            stage.put("name", item.get("title"));
            stage.put("days", 1);
            stage.put("goal", item.get("action"));
            stage.put("knowledgePoints", focusPoints);
            stage.put("tasks", List.of(item.get("action")));
            stage.put("resourceType", item.get("resourceType"));
            stages.add(stage);
        }

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("title", "动态调整路径");
        content.put("summary", "基于最近资源练习（掌握度 " + mastery + "%）自动重排");
        content.put("stages", stages);
        content.put("suggestions", List.of("完成每个阶段后再次进行短测，系统会继续调整路径"));

        String summary = "练习后自动更新：" + (focusPoints.isEmpty() ? "迁移应用" : String.join("、", focusPoints));
        return learningProfileRepository.replaceAdaptivePlan(studentid, courseid,
                LearningProfileRepository.adaptivePlanTitle(), summary, safeJson(content)).orElse(null);
    }

    private List<Map<String, Object>> recommendResources(Integer studentid, Integer courseid,
                                                          Integer currentResourceId, List<String> focusPoints) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        if (courseid != null) {
            List<Map<String, Object>> candidates =
                    learningProfileRepository.listRecommendCandidates(studentid, courseid, currentResourceId);

            // 按薄弱点在资源内容里的命中次数排序，命中越多越相关
            for (Map<String, Object> candidate : candidates) {
                String json = String.valueOf(candidate.remove("packJson"));
                long matches = focusPoints.stream()
                        .filter(point -> !point.isBlank() && json.contains(point))
                        .count();
                candidate.put("_matches", (int) matches);
            }
            candidates.sort((a, b) -> Integer.compare(
                    ((Number) b.get("_matches")).intValue(), ((Number) a.get("_matches")).intValue()));

            for (Map<String, Object> candidate : candidates) {
                // 有薄弱点时只推命中的，一个都没命中的推了也没意义
                if (!focusPoints.isEmpty() && ((Number) candidate.get("_matches")).intValue() == 0) continue;
                candidate.remove("_matches");
                recommendations.add(candidate);
                if (recommendations.size() >= MAX_RECOMMENDATIONS) break;
            }
        }
        String reason = focusPoints.isEmpty() ? "同课程迁移巩固资源" : "内容覆盖薄弱点：" + String.join("、", focusPoints);
        for (Map<String, Object> item : recommendations) item.put("reason", reason);
        return recommendations;
    }

    private boolean saveAttempt(Integer studentid, Integer resourceId, Integer courseid, List<Object> answers,
                             double score, int correct, int total, List<Map<String, Object>> details) {
        return learningProfileRepository.insertAttempt(new LearningProfileRepository.NewAttempt(
                studentid, resourceId, courseid, safeJson(answers), score, correct, total, safeJson(details)));
    }

    private Integer saveEvaluation(Integer studentid, Integer courseid, Integer resourceId, String sourceType,
                                   double preMastery, double postMastery, Integer correct, Integer total,
                                   Object result, Object focusPoints, Object recommendations) {
        return learningProfileRepository.insertEvaluation(new LearningProfileRepository.NewEvaluation(
                studentid, courseid, resourceId, sourceType, preMastery, postMastery, correct, total,
                safeJson(focusPoints), safeJson(result), safeJson(recommendations))).orElse(null);
    }

    private double latestMastery(Integer studentid, Integer courseid) {
        return learningProfileRepository.latestMastery(studentid, courseid);
    }

    private String normalizeAnswer(Object answer, String type) {
        if (answer == null) return "";
        String value;
        if (answer instanceof List) {
            List<String> parts = new ArrayList<>();
            for (Object item : (List<?>) answer) parts.add(String.valueOf(item).trim());
            parts.sort(String::compareTo);
            value = String.join(",", parts);
        } else {
            value = String.valueOf(answer).trim();
        }
        if ("judge".equalsIgnoreCase(type)) {
            if (value.matches("(?i)^(true|正确|对|是|t|1)$")) return "true";
            if (value.matches("(?i)^(false|错误|错|否|f|0)$")) return "false";
        }
        if ("single".equalsIgnoreCase(type)) {
            String token = value.trim().toUpperCase();
            if (token.matches("[A-Z]")) return String.valueOf(token.charAt(0) - 'A');
            try { return new java.math.BigDecimal(token).stripTrailingZeros().toPlainString(); }
            catch (Exception ignored) { return token.toLowerCase(); }
        }
        if ("multi".equalsIgnoreCase(type) || value.contains(",")) {
            List<String> parts = new ArrayList<>();
            for (String raw : value.replace(" ", "").split(",")) {
                String token = raw.toUpperCase();
                if (token.matches("[A-Z]")) token = String.valueOf(token.charAt(0) - 'A');
                else {
                    try { token = new java.math.BigDecimal(token).stripTrailingZeros().toPlainString(); }
                    catch (Exception ignored) {}
                }
                parts.add(token);
            }
            parts.sort(String::compareTo);
            return String.join(",", parts);
        }
        return "blank".equalsIgnoreCase(type)
                ? value.replaceAll("\\s+", "")
                : value.replaceAll("\\s+", "").toLowerCase();
    }

    private double number(Object value) {
        if (value instanceof Number) return clamp(((Number) value).doubleValue());
        try { return clamp(Double.parseDouble(String.valueOf(value).replace("%", "").trim())); }
        catch (Exception e) { return 0; }
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    // ==================== 工具 ====================

    private String buildFacts(Integer studentid, String name) {
        StringBuilder facts = new StringBuilder();
        Double hwAvg = round1(queryDouble("SELECT AVG(score) FROM studenthomework WHERE studentid=? AND score IS NOT NULL", studentid));
        Double exAvg = round1(queryDouble("SELECT AVG(score) FROM exam_record WHERE studentid=? AND score IS NOT NULL", studentid));
        Integer wrong = queryInt("SELECT COUNT(*) FROM student_wrong_question WHERE studentid=? AND mastered=0", studentid);
        Integer wrongTotal = queryInt("SELECT COUNT(*) FROM student_wrong_question WHERE studentid=?", studentid);
        facts.append("作业平均分：").append(hwAvg).append("，考试平均分：").append(exAvg).append("\n");
        facts.append("待攻克错题：").append(wrong).append(" / 共 ").append(wrongTotal).append("\n");
        List<Map<String, Object>> courses = new ArrayList<>();
        try {
            courses = jdbcTemplate.queryForList(
                "SELECT DISTINCT c.coursename AS cn FROM student_wrong_question w JOIN course c ON c.courseid=w.courseid " +
                "WHERE w.studentid=? LIMIT 6", studentid);
        } catch (Exception ignored) {}
        if (!courses.isEmpty()) {
            List<String> cs = new ArrayList<>();
            for (Map<String, Object> c : courses) if (c.get("cn") != null) cs.add(String.valueOf(c.get("cn")));
            if (!cs.isEmpty()) facts.append("涉及课程：").append(String.join("、", cs)).append("\n");
        }
        return facts.toString();
    }

    private String safeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }

    private String asText(Object o) { return o == null ? "" : String.valueOf(o); }
    private String strOr(String v, String def) { return (v == null || v.trim().isEmpty()) ? def : v; }

    private Integer queryInt(String sql, Object... args) {
        try { Integer v = jdbcTemplate.queryForObject(sql, Integer.class, args); return v == null ? 0 : v; }
        catch (Exception e) { return 0; }
    }

    private Double queryDouble(String sql, Object... args) {
        try { Double v = jdbcTemplate.queryForObject(sql, Double.class, args); return v == null ? 0d : v; }
        catch (Exception e) { return 0d; }
    }

    private String queryString(String sql, Object... args) {
        try { return jdbcTemplate.queryForObject(sql, String.class, args); }
        catch (Exception e) { return null; }
    }

    private double round1(Double v) { return v == null ? 0 : Math.round(v * 10) / 10.0; }
}
