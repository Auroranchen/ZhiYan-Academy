package com.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mapper.LearningPlanMapper;
import com.model.LearningPlan;
import com.model.WrongQuestion;
import com.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI 学习路径推荐业务层。
 * 汇集学生错题与知识点掌握画像，调用 AI 生成个性化学习计划；
 * 当 AI 不可用时降级为基于学情数据的本地规则计划，保证功能稳定可用。
 */
@Service
public class LearningPathService {

    private static final Logger logger = LoggerFactory.getLogger(LearningPathService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LearningPlanMapper learningPlanMapper;
    private final WrongBookService wrongBookService;
    private final AiSafeCallService aiSafeCallService;
    private final LearningProfileService learningProfileService;
    private final MessageService messageService;
    private final JdbcTemplate jdbcTemplate;

    public LearningPathService(LearningPlanMapper learningPlanMapper,
                               WrongBookService wrongBookService,
                               AiSafeCallService aiSafeCallService,
                               LearningProfileService learningProfileService,
                               MessageService messageService,
                               JdbcTemplate jdbcTemplate) {
        this.learningPlanMapper = learningPlanMapper;
        this.wrongBookService = wrongBookService;
        this.aiSafeCallService = aiSafeCallService;
        this.learningProfileService = learningProfileService;
        this.messageService = messageService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 基于当前学情生成学习路径计划。
     */
    public Result<LearningPlan> generate(Integer studentid) {
        // 先归集最新错题，保证学情数据新鲜
        try {
            wrongBookService.collect(studentid);
        } catch (Exception ignored) {
        }

        List<Map<String, Object>> mastery = wrongBookService.masteryByCourse(studentid);
        List<WrongQuestion> wrongList = wrongBookService.list(studentid, null, 0);

        if (mastery.isEmpty() && wrongList.isEmpty()) {
            return Result.error("暂无学情数据，请先完成并提交在线作业后再生成学习路径");
        }

        String summary = buildSummary(mastery, wrongList);
        String planJson = tryAiPlan(studentid, summary, mastery, wrongList);
        if (planJson == null) {
            planJson = buildFallbackPlan(mastery, wrongList);
        }

        String title = "个性化学习路径 · " + new java.text.SimpleDateFormat("MM-dd").format(new Date());
        Integer focusCourseId = mastery.isEmpty() ? null : toInt(weakestCourse(mastery).get("courseid"));

        LearningPlan plan = new LearningPlan();
        plan.setStudentid(studentid);
        plan.setCourseid(focusCourseId);
        plan.setTitle(title);
        plan.setSummary(summary);
        plan.setContent(planJson);
        plan.setStatus(1);
        learningPlanMapper.insert(plan);
        notifyGenerated(studentid, plan);
        return Result.success("学习路径已生成", plan);
    }

    /** 任务完成进度写入服务端，跨设备保留学习闭环数据 */
    public Result<Void> updateProgress(Integer studentid, Integer planId, String progressJson) {
        if (planId == null) return Result.error("缺少计划ID");
        try {
            // 校验为合法 JSON，防止脏数据入库
            MAPPER.readTree(progressJson == null ? "{}" : progressJson);
        } catch (Exception e) {
            return Result.error("进度数据格式错误");
        }
        int n = learningPlanMapper.updateProgress(planId, studentid, progressJson == null ? "{}" : progressJson);
        return n > 0 ? Result.success() : Result.error("保存失败或无权操作");
    }

    private void notifyGenerated(Integer studentid, LearningPlan plan) {
        try {
            // 以真实管理员账号作为系统消息发送者，避免消息列表联查发送者姓名为空
            Integer adminId = null;
            try {
                adminId = jdbcTemplate.queryForObject("SELECT MIN(adminid) FROM admin", Integer.class);
            } catch (Exception ignored) {}
            if (adminId == null) return;

            com.model.Message msg = new com.model.Message();
            msg.setTitle("个性化学习路径已生成");
            msg.setContent("你的《" + plan.getTitle()
                    + "》已生成，请在「AI学习路径」中查看并按阶段推进，完成任务可获得积分。\n[planid:"
                    + plan.getId() + "]");
            msg.setSendertype(1);
            msg.setSenderid(adminId);
            msg.setReceivertype(1);
            msg.setReceiverid(studentid);
            messageService.send(msg);
        } catch (Exception e) {
            logger.warn("学习路径生成通知发送失败: {}", e.getMessage());
        }
    }

    public List<LearningPlan> list(Integer studentid) {
        return learningPlanMapper.findByStudent(studentid);
    }

    public LearningPlan getById(Integer id) {
        return learningPlanMapper.findById(id);
    }

    public LearningPlan getById(Integer id, Integer studentid) {
        return learningPlanMapper.findByIdForStudent(id, studentid);
    }

    public boolean archive(Integer id, Integer studentid) {
        return learningPlanMapper.archive(id, studentid) > 0;
    }

    public boolean delete(Integer id, Integer studentid) {
        return learningPlanMapper.delete(id, studentid) > 0;
    }

    // ---------------- 内部 ----------------

    private String buildSummary(List<Map<String, Object>> mastery, List<WrongQuestion> wrongList) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前待攻克错题 ").append(wrongList.size()).append(" 道。");
        if (!mastery.isEmpty()) {
            List<Map<String, Object>> sorted = new ArrayList<>(mastery);
            sorted.sort(Comparator.comparingDouble(m -> toDouble(m.get("accuracy"))));
            sb.append("各课程掌握度：");
            for (Map<String, Object> m : sorted) {
                sb.append(m.get("coursename")).append(" ").append(m.get("accuracy")).append("%；");
            }
        }
        return sb.toString();
    }

    private String tryAiPlan(Integer studentid, String summary, List<Map<String, Object>> mastery,
                             List<WrongQuestion> wrongList) {
        try {
            StringBuilder wrongDigest = new StringBuilder();
            int limit = Math.min(wrongList.size(), 12);
            for (int i = 0; i < limit; i++) {
                WrongQuestion w = wrongList.get(i);
                wrongDigest.append("- [").append(w.getCoursename()).append("] ")
                        .append(trim(w.getQuestionTitle(), 50))
                        .append("（正确答案：").append(trim(w.getCorrectAnswer(), 30)).append("）\n");
            }
            String system = "你是一名资深学习规划老师。请根据学生的学情数据，制定个性化、可执行的学习路径。"
                    + "若提供学习画像，阶段节奏与任务形式须贴合画像中的认知风格与学习节奏。"
                    + "必须只返回一个JSON对象，不要包含多余文字。JSON格式："
                    + "{\"title\":\"标题\",\"summary\":\"一句话学情总结\","
                    + "\"stages\":[{\"name\":\"阶段名\",\"days\":天数,\"goal\":\"目标\",\"knowledgePoints\":[\"知识点\"],\"tasks\":[\"具体任务\"]}],"
                    + "\"suggestions\":[\"学习建议\"]}";
            StringBuilder user = new StringBuilder("学情概述：" + summary + "\n\n典型错题：\n" + wrongDigest);
            String profile = profileBrief(studentid);
            if (!profile.isEmpty()) {
                user.append("\n学习画像：").append(profile).append("\n");
            }
            user.append("\n请输出包含3-4个阶段、每阶段2-4个任务的学习路径JSON。");

            Map<String, Object> parsed = aiSafeCallService.callJson("learnpath.generate", system, user.toString());
            if (parsed == null || !parsed.containsKey("stages")) {
                return null;
            }
            return MAPPER.writeValueAsString(parsed);
        } catch (Exception ex) {
            logger.warn("AI 学习路径生成失败，降级本地方案: {}", ex.getMessage());
            return null;
        }
    }

    /** 学生画像摘要（summary + 各维度标签），无画像时返回空串 */
    private String profileBrief(Integer studentid) {
        try {
            Map<String, Object> profile = learningProfileService.getProfile(studentid);
            StringBuilder sb = new StringBuilder();
            Object summary = profile.get("summary");
            if (summary != null && !String.valueOf(summary).isBlank() && !"null".equals(String.valueOf(summary))) {
                sb.append(summary);
            }
            Object dims = profile.get("dimensions");
            if (dims instanceof Map) {
                for (Object v : ((Map<?, ?>) dims).values()) {
                    if (!(v instanceof Map)) continue;
                    Object name = ((Map<?, ?>) v).get("name");
                    Object label = ((Map<?, ?>) v).get("label");
                    if (name == null || label == null) continue;
                    String labelStr = String.valueOf(label);
                    if (labelStr.isBlank() || "待完善".equals(labelStr)) continue;
                    if (sb.length() > 0) sb.append("；");
                    sb.append(name).append("：").append(labelStr);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** AI 不可用时的本地规则计划，确保功能可用。 */
    private String buildFallbackPlan(List<Map<String, Object>> mastery, List<WrongQuestion> wrongList) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("title", "个性化学习路径");
        root.put("summary", "根据你的错题与掌握度，按薄弱优先的顺序进行巩固训练。");

        ArrayNode stages = root.putArray("stages");

        // 按掌握度从低到高排序，弱项优先
        List<Map<String, Object>> sorted = new ArrayList<>(mastery);
        sorted.sort(Comparator.comparingDouble(m -> toDouble(m.get("accuracy"))));

        // 按课程聚合错题
        Map<Integer, List<WrongQuestion>> byCourse = new LinkedHashMap<>();
        for (WrongQuestion w : wrongList) {
            byCourse.computeIfAbsent(w.getCourseid(), k -> new ArrayList<>()).add(w);
        }

        int stageDays = 3;
        int count = 0;
        for (Map<String, Object> m : sorted) {
            if (count >= 4) break;
            double acc = toDouble(m.get("accuracy"));
            String course = String.valueOf(m.get("coursename"));
            Integer cid = toInt(m.get("courseid"));
            ObjectNode stage = stages.addObject();
            stage.put("name", "巩固《" + course + "》");
            stage.put("days", stageDays);
            stage.put("goal", "将《" + course + "》客观题正确率从 " + acc + "% 提升至 85% 以上");
            ArrayNode kps = stage.putArray("knowledgePoints");
            ArrayNode tasks = stage.putArray("tasks");
            List<WrongQuestion> cw = byCourse.getOrDefault(cid, Collections.emptyList());
            int t = Math.min(cw.size(), 4);
            for (int i = 0; i < t; i++) {
                kps.add(trim(cw.get(i).getQuestionTitle(), 30));
                tasks.add("重做错题：" + trim(cw.get(i).getQuestionTitle(), 40)
                        + "（正确答案：" + trim(cw.get(i).getCorrectAnswer(), 20) + "）");
            }
            if (t == 0) {
                tasks.add("复习本课程核心知识点，完成一套自测题");
            }
            tasks.add("在错题本中标记已掌握的题目");
            count++;
        }

        if (stages.size() == 0) {
            ObjectNode stage = stages.addObject();
            stage.put("name", "全面复习");
            stage.put("days", 3);
            stage.put("goal", "梳理近期作业知识点，巩固基础");
            stage.putArray("knowledgePoints").add("近期作业核心知识点");
            ArrayNode tasks = stage.putArray("tasks");
            tasks.add("回顾近一周作业，整理笔记");
            tasks.add("完成一套综合自测");
        }

        ArrayNode suggestions = root.putArray("suggestions");
        suggestions.add("坚持每日打卡，保持学习节奏");
        suggestions.add("优先攻克掌握度最低的课程");
        suggestions.add("每攻克一道错题，及时在错题本标记已掌握");

        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> weakestCourse(List<Map<String, Object>> mastery) {
        return mastery.stream()
                .min(Comparator.comparingDouble(m -> toDouble(m.get("accuracy"))))
                .orElse(mastery.get(0));
    }

    private String trim(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private double toDouble(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
