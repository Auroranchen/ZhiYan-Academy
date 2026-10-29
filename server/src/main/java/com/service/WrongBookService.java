package com.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.WrongQuestionMapper;
import com.model.WrongQuestion;
import com.util.ObjectiveGrading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 错题本与知识点掌握画像业务层。
 * 复用 {@link ExamGradingService#checkAnswerCorrect} 的判分逻辑，
 * 自动从已提交的在线作业中归集客观题错题。
 */
@Service
public class WrongBookService {

    private static final Logger logger = LoggerFactory.getLogger(WrongBookService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private WrongQuestionMapper wrongQuestionMapper;

    @Autowired
    private ExamGradingService examGradingService;

    /**
     * 扫描学生全部在线作业提交，归集客观题错题。
     *
     * @return 本次归集到的错题数量
     */
    public int collect(Integer studentid) {
        // 先按题目聚合全部历史提交，再一次性 upsert。
        // 这样 wrong_count 是确定性的历史错误次数，重复刷新不会无限自增。
        Map<String, WrongQuestion> grouped = new LinkedHashMap<>();
        List<Map<String, Object>> submissions = wrongQuestionMapper.findGradableSubmissions(studentid);
        for (Map<String, Object> sub : submissions) {
            try {
                String homeworkContent = str(sub.get("homeworkcontent"));
                String studentContent = str(sub.get("studentcontent"));
                if (homeworkContent == null || studentContent == null) {
                    continue;
                }
                Integer homeworkid = toInt(sub.get("homeworkid"));
                Integer courseid = toInt(sub.get("courseid"));

                JsonNode hwNode = MAPPER.readTree(homeworkContent);
                JsonNode questions = hwNode.path("questions");
                if (!questions.isArray()) {
                    continue;
                }
                JsonNode stuNode = MAPPER.readTree(studentContent);
                JsonNode answers = stuNode.path("answers");
                if (!answers.isObject()) {
                    continue;
                }

                for (JsonNode q : questions) {
                    String type = q.path("type").asText();
                    if (!ObjectiveGrading.isObjective(type)) {
                        continue;
                    }
                    boolean correct = examGradingService.checkAnswerCorrect(q, answers, type);
                    if (correct) {
                        continue;
                    }
                    String questionId = q.path("id").asText();
                    if (questionId == null || questionId.isBlank()) {
                        questionId = "auto-" + Integer.toUnsignedString(
                                Objects.hash(courseid, q.path("title").asText(), type));
                    }
                    String key = "homework:" + questionId;
                    WrongQuestion wq = grouped.get(key);
                    if (wq != null) {
                        wq.setWrongCount((wq.getWrongCount() == null ? 1 : wq.getWrongCount()) + 1);
                        wq.setSourceid(homeworkid);
                        wq.setStudentAnswer(formatStudent(answers.get(questionId), type));
                        wq.setLastWrongTime(new Date());
                        continue;
                    }
                    wq = new WrongQuestion();
                    wq.setStudentid(studentid);
                    wq.setCourseid(courseid);
                    wq.setSourceid(homeworkid);
                    wq.setSource("homework");
                    wq.setQuestionId(questionId);
                    wq.setQuestionType(type);
                    wq.setQuestionTitle(q.path("title").asText(""));
                    wq.setOptionsJson(q.has("options") ? q.get("options").toString() : null);
                    wq.setCorrectAnswer(formatCorrect(q, type));
                    JsonNode stuAns = answers.get(questionId);
                    wq.setStudentAnswer(formatStudent(stuAns, type));
                    wq.setWrongCount(1);
                    wq.setLastWrongTime(new Date());
                    grouped.put(key, wq);
                }
            } catch (Exception e) {
                logger.warn("归集错题失败: {}", e.getMessage());
            }
        }
        for (WrongQuestion wq : grouped.values()) {
            wrongQuestionMapper.upsert(wq);
        }
        return grouped.size();
    }

    public List<WrongQuestion> list(Integer studentid, Integer courseid, Integer mastered) {
        return wrongQuestionMapper.findByStudent(studentid, courseid, mastered);
    }

    public long countWrong(Integer studentid) {
        return wrongQuestionMapper.countWrong(studentid);
    }

    public long countMastered(Integer studentid) {
        return wrongQuestionMapper.countMastered(studentid);
    }

    public boolean setMastered(Long id, Integer studentid, int mastered) {
        return wrongQuestionMapper.updateMastered(id, studentid, mastered) > 0;
    }

    public boolean remove(Long id, Integer studentid) {
        return wrongQuestionMapper.delete(id, studentid) > 0;
    }

    /**
     * 知识点掌握画像：按课程统计客观题正确率（雷达图数据源）。
     */
    public List<Map<String, Object>> masteryByCourse(Integer studentid) {
        // courseid -> [total, correct, coursename]
        Map<Integer, int[]> stat = new LinkedHashMap<>();
        Map<Integer, String> names = new HashMap<>();
        List<Map<String, Object>> submissions = wrongQuestionMapper.findGradableSubmissions(studentid);
        for (Map<String, Object> sub : submissions) {
            try {
                String homeworkContent = str(sub.get("homeworkcontent"));
                String studentContent = str(sub.get("studentcontent"));
                Integer courseid = toInt(sub.get("courseid"));
                if (homeworkContent == null || studentContent == null || courseid == null) {
                    continue;
                }
                names.putIfAbsent(courseid, str(sub.get("coursename")));
                JsonNode questions = MAPPER.readTree(homeworkContent).path("questions");
                JsonNode answers = MAPPER.readTree(studentContent).path("answers");
                if (!questions.isArray() || !answers.isObject()) {
                    continue;
                }
                int[] arr = stat.computeIfAbsent(courseid, k -> new int[2]);
                for (JsonNode q : questions) {
                    String type = q.path("type").asText();
                    if (!ObjectiveGrading.isObjective(type)) {
                        continue;
                    }
                    arr[0]++;
                    if (examGradingService.checkAnswerCorrect(q, answers, type)) {
                        arr[1]++;
                    }
                }
            } catch (Exception e) {
                logger.warn("计算掌握度失败: {}", e.getMessage());
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Integer, int[]> e : stat.entrySet()) {
            int total = e.getValue()[0];
            int correct = e.getValue()[1];
            if (total == 0) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("courseid", e.getKey());
            item.put("coursename", names.get(e.getKey()));
            item.put("total", total);
            item.put("correct", correct);
            item.put("accuracy", Math.round(correct * 1000.0 / total) / 10.0);
            result.add(item);
        }
        return result;
    }

    /**
     * 掌握度热力图数据：各课程掌握度 + 待攻克错题数 + 高频错题 TopN。
     * 供前端渲染「课程 × 掌握度」矩阵，点击可跳转 AI 智能练习针对性攻克。
     */
    public List<Map<String, Object>> heatmap(Integer studentid) {
        List<Map<String, Object>> mastery = new ArrayList<>(masteryByCourse(studentid));
        Map<Integer, Map<String, Object>> byCourse = new LinkedHashMap<>();
        for (Map<String, Object> item : mastery) {
            byCourse.put(toInt(item.get("courseid")), item);
        }

        Map<Integer, List<WrongQuestion>> wrongsByCourse = new LinkedHashMap<>();
        for (WrongQuestion wrong : list(studentid, null, 0)) {
            Integer cid = wrong.getCourseid();
            wrongsByCourse.computeIfAbsent(cid, k -> new ArrayList<>()).add(wrong);
            // 仅有错题、暂时没有客观题正确率统计的课程也必须出现在热力图中
            byCourse.computeIfAbsent(cid, k -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("courseid", cid);
                item.put("coursename", wrong.getCoursename());
                item.put("total", 0);
                item.put("correct", 0);
                item.put("accuracy", 0);
                mastery.add(item);
                return item;
            });
        }

        for (Map<String, Object> m : mastery) {
            Integer cid = toInt(m.get("courseid"));
            List<WrongQuestion> wrongs = wrongsByCourse.getOrDefault(cid, new ArrayList<>());
            wrongs.sort((a, b) -> Integer.compare(
                    b.getWrongCount() == null ? 0 : b.getWrongCount(),
                    a.getWrongCount() == null ? 0 : a.getWrongCount()));
            m.put("wrongUnmastered", wrongs.size());
            List<Map<String, Object>> top = new ArrayList<>();
            for (int i = 0; i < Math.min(5, wrongs.size()); i++) {
                WrongQuestion w = wrongs.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                String title = w.getQuestionTitle() == null ? "" : w.getQuestionTitle();
                item.put("title", title.length() > 60 ? title.substring(0, 60) + "…" : title);
                item.put("type", w.getQuestionType());
                item.put("cnt", w.getWrongCount());
                top.add(item);
            }
            m.put("topWrong", top);
        }
        return mastery;
    }

    /** 包级可见以便单测直接验证渲染结果 */
    String formatCorrect(JsonNode q, String type) {
        JsonNode answer = q.get("answer");
        if (answer == null || answer.isNull()) {
            return "无";
        }
        if ("single".equals(type)) {
            return optionText(q, answer.asInt(-1));
        }
        if ("multi".equals(type)) {
            StringBuilder sb = new StringBuilder();
            if (answer.isArray()) {
                for (JsonNode item : answer) {
                    if (sb.length() > 0) sb.append("；");
                    sb.append(optionText(q, item.asInt(-1)));
                }
            }
            return sb.toString();
        }
        if ("judge".equals(type)) {
            return answer.asBoolean() ? "正确" : "错误";
        }
        if ("prog_blank".equals(type)) {
            return formatBlanks(answer);
        }
        return answer.asText();
    }

    /** 包级可见以便单测直接验证渲染结果 */
    String formatStudent(JsonNode ans, String type) {
        if (ans == null || ans.isNull()) {
            return "未作答";
        }
        if ("single".equals(type)) {
            int idx = ans.asInt(-1);
            return idx >= 0 ? "选项" + (char) ('A' + idx) : "未作答";
        }
        if ("multi".equals(type)) {
            StringBuilder sb = new StringBuilder();
            if (ans.isArray()) {
                for (JsonNode item : ans) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append((char) ('A' + item.asInt()));
                }
            }
            return sb.length() > 0 ? sb.toString() : "未作答";
        }
        if ("judge".equals(type)) {
            return ans.asBoolean() ? "正确" : "错误";
        }
        if ("prog_blank".equals(type)) {
            String blanks = formatBlanks(ans);
            return blanks.isEmpty() ? "未作答" : blanks;
        }
        String t = ans.asText();
        return (t == null || t.trim().isEmpty()) ? "未作答" : t;
    }

    /**
     * 程序填空题的答案是逐空的数组，按空位编号展开。
     * 直接 asText() 对数组节点会得到空串，错题本里就会显示成空白。
     */
    String formatBlanks(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (!node.isArray()) {
            return node.asText("");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < node.size(); i++) {
            if (sb.length() > 0) sb.append("；");
            sb.append("空").append(i + 1).append("：").append(node.get(i).asText(""));
        }
        return sb.toString();
    }

    private String optionText(JsonNode q, int idx) {
        if (idx < 0) {
            return "?";
        }
        JsonNode options = q.get("options");
        String letter = String.valueOf((char) ('A' + idx));
        if (options != null && options.isArray() && idx < options.size()) {
            return letter + ". " + options.get(idx).asText();
        }
        return "选项" + letter;
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
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
