package com.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repository.AiReportRepository;
import com.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 学情洞察服务。
 * 提供四类基于 DeepSeek 文本接口的能力：
 *  1. 学生个人学习周报（成长报告）
 *  2. 教师班级/课程学情周报
 *  3. 基于错题的个性化智能练习题生成
 *  4. 知识点思维导图（层级树）生成
 * 学情类报告生成后写入 ai_report 表缓存，避免重复消耗 AI 额度。
 */
@Service
public class AiInsightService {

    private static final Logger logger = LoggerFactory.getLogger(AiInsightService.class);

    /** 报告生成时间戳，返回给前端展示 */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 仅用于读取学情统计；报告缓存的读写走仓储 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiReportRepository aiReportRepository;

    @Autowired
    private AiSafeCallService aiSafeCallService;

    @Autowired
    private GamificationService gamificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 学生学习周报 ====================

    public Map<String, Object> getCachedStudentReport(Integer studentid) {
        return latestReport("student", studentid);
    }

    /** 学生有学习记录（作业/考试/错题）的课程列表，用于按课程生成周报 */
    public List<Map<String, Object>> studentCourses(Integer studentid) {
        try {
            return jdbcTemplate.queryForList(
                "SELECT c.courseid AS courseid, c.coursename AS coursename FROM course c WHERE c.courseid IN (" +
                "  SELECT h.courseid FROM studenthomework sh JOIN homework h ON sh.homeworkid=h.homeworkid WHERE sh.studentid=? " +
                "  UNION SELECT e.courseid FROM exam_record er JOIN exam e ON er.examid=e.examid WHERE er.studentid=? " +
                "  UNION SELECT courseid FROM student_wrong_question WHERE studentid=? AND courseid IS NOT NULL" +
                ") ORDER BY c.coursename", studentid, studentid, studentid);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public Result<Map<String, Object>> studentReport(Integer studentid, Integer courseid) {
        Map<String, Object> stats = collectStudentStats(studentid, courseid);
        String name = strOr(queryString("SELECT studentname FROM student WHERE studentid=?", studentid), "同学");
        String coursename = courseid != null
                ? strOr(queryString("SELECT coursename FROM course WHERE courseid=?", courseid), "课程") : null;
        String scope = coursename != null ? ("《" + coursename + "》") : "全部课程";
        String title = name + " · " + (coursename != null ? (coursename + " 学习周报") : "学习周报");

        StringBuilder facts = new StringBuilder();
        facts.append("学生姓名：").append(name).append("\n");
        facts.append("统计范围：").append(scope).append("\n");
        facts.append("已提交作业数：").append(stats.get("homeworkCount")).append("，作业平均分：").append(stats.get("homeworkAvg")).append("\n");
        facts.append("参加考试数：").append(stats.get("examCount")).append("，考试平均分：").append(stats.get("examAvg")).append("\n");
        facts.append("错题总数：").append(stats.get("wrongTotal")).append("，其中已掌握：").append(stats.get("wrongMastered")).append("\n");
        facts.append("累计积分：").append(stats.get("points")).append("，连续打卡：").append(stats.get("checkinDays")).append("天\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> weak = (List<Map<String, Object>>) stats.get("weakCourses");
        if (weak != null && !weak.isEmpty()) {
            facts.append(courseid != null ? "高频薄弱知识点（按未掌握错题）：" : "薄弱知识点分布（按未掌握错题数）：");
            for (Map<String, Object> w : weak) {
                facts.append(w.get("name")).append("(").append(w.get("count")).append(") ");
            }
            facts.append("\n");
        }

        String system = "你是一位资深的学习成长导师。请根据学生的学习数据，生成一份温暖、客观、有针对性的个人学习周报。" +
                "用 Markdown 输出，包含这几个部分：## 总体表现、## 亮点、## 需要加强、## 下周行动建议（3-5条可执行的小目标）。" +
                "语气积极鼓励，建议要具体、可落地，避免空话套话。不要编造数据中没有的信息。" +
                (courseid != null ? "本报告聚焦于" + scope + "这一门课程，请围绕该课程给出针对性分析与建议。" : "");
        String user = "以下是该学生的学习数据：\n" + facts;

        String content;
        try {
            content = aiSafeCallService.callText("insight.studentReport", system, user);
        } catch (Exception e) {
            logger.error("生成学生学习周报失败: {}", e.getMessage());
            return Result.error("AI 生成失败：" + e.getMessage());
        }
        // 仅缓存“全部课程”的总览周报，避免按课程缓存时的键冲突
        if (courseid == null) {
            saveReport("student", studentid, title, content, stats);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("title", title);
        result.put("content", content);
        result.put("stats", stats);
        result.put("generatedAt", LocalDateTime.now().format(TS));
        return Result.success(result);
    }

    private Map<String, Object> collectStudentStats(Integer studentid, Integer courseid) {
        Map<String, Object> stats = new HashMap<>();
        boolean byCourse = courseid != null;
        if (byCourse) {
            stats.put("homeworkCount", queryInt(
                    "SELECT COUNT(*) FROM studenthomework sh JOIN homework h ON sh.homeworkid=h.homeworkid " +
                    "WHERE sh.studentid=? AND sh.status>0 AND h.courseid=?", studentid, courseid));
            stats.put("homeworkAvg", round1(queryDouble(
                    "SELECT AVG(sh.score) FROM studenthomework sh JOIN homework h ON sh.homeworkid=h.homeworkid " +
                    "WHERE sh.studentid=? AND sh.score IS NOT NULL AND h.courseid=?", studentid, courseid)));
            stats.put("examCount", queryInt(
                    "SELECT COUNT(*) FROM exam_record er JOIN exam e ON er.examid=e.examid " +
                    "WHERE er.studentid=? AND er.status>=1 AND e.courseid=?", studentid, courseid));
            stats.put("examAvg", round1(queryDouble(
                    "SELECT AVG(er.score) FROM exam_record er JOIN exam e ON er.examid=e.examid " +
                    "WHERE er.studentid=? AND er.score IS NOT NULL AND e.courseid=?", studentid, courseid)));
            stats.put("wrongTotal", queryInt(
                    "SELECT COUNT(*) FROM student_wrong_question WHERE studentid=? AND courseid=?", studentid, courseid));
            stats.put("wrongMastered", queryInt(
                    "SELECT COUNT(*) FROM student_wrong_question WHERE studentid=? AND courseid=? AND mastered=1", studentid, courseid));
        } else {
            stats.put("homeworkCount", queryInt(
                    "SELECT COUNT(*) FROM studenthomework WHERE studentid=? AND status>0", studentid));
            stats.put("homeworkAvg", round1(queryDouble(
                    "SELECT AVG(score) FROM studenthomework WHERE studentid=? AND score IS NOT NULL", studentid)));
            stats.put("examCount", queryInt(
                    "SELECT COUNT(*) FROM exam_record WHERE studentid=? AND status>=1", studentid));
            stats.put("examAvg", round1(queryDouble(
                    "SELECT AVG(score) FROM exam_record WHERE studentid=? AND score IS NOT NULL", studentid)));
            stats.put("wrongTotal", queryInt(
                    "SELECT COUNT(*) FROM student_wrong_question WHERE studentid=?", studentid));
            stats.put("wrongMastered", queryInt(
                    "SELECT COUNT(*) FROM student_wrong_question WHERE studentid=? AND mastered=1", studentid));
        }
        // 积分/打卡为账号级，始终取全局
        stats.put("points", queryInt(
                "SELECT total_points FROM student_points WHERE studentid=?", studentid));
        stats.put("checkinDays", queryInt(
                "SELECT continuous_days FROM student_points WHERE studentid=?", studentid));

        List<Map<String, Object>> weak = new ArrayList<>();
        try {
            if (byCourse) {
                weak.addAll(jdbcTemplate.queryForList(
                    "SELECT LEFT(question_title,40) AS name, SUM(wrong_count) AS count " +
                    "FROM student_wrong_question WHERE studentid=? AND courseid=? AND mastered=0 AND question_title IS NOT NULL " +
                    "GROUP BY question_title ORDER BY count DESC LIMIT 6", studentid, courseid));
            } else {
                weak.addAll(jdbcTemplate.queryForList(
                    "SELECT COALESCE(c.coursename,'未分类') AS name, COUNT(*) AS count " +
                    "FROM student_wrong_question w LEFT JOIN course c ON w.courseid=c.courseid " +
                    "WHERE w.studentid=? AND w.mastered=0 GROUP BY w.courseid ORDER BY count DESC LIMIT 6", studentid));
            }
        } catch (Exception ignored) {}
        stats.put("weakCourses", weak);
        return stats;
    }

    // ==================== 教师班级/课程学情周报 ====================

    public Map<String, Object> getCachedClassReport(Integer courseid) {
        return latestReport("class", courseid);
    }

    public Result<Map<String, Object>> classReport(Integer courseid) {
        if (courseid == null) {
            return Result.error("请选择课程");
        }
        String coursename = strOr(queryString("SELECT coursename FROM course WHERE courseid=?", courseid), "课程");
        Map<String, Object> stats = collectClassStats(courseid);

        StringBuilder facts = new StringBuilder();
        facts.append("课程名称：").append(coursename).append("\n");
        facts.append("作业提交次数：").append(stats.get("submitCount")).append("，作业平均分：").append(stats.get("homeworkAvg")).append("\n");
        facts.append("考试参与人次：").append(stats.get("examCount")).append("，考试平均分：").append(stats.get("examAvg")).append("\n");
        facts.append("成绩分布：").append(stats.get("distribution")).append("\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> low = (List<Map<String, Object>>) stats.get("lowPerformers");
        if (low != null && !low.isEmpty()) {
            facts.append("成绩偏低的学生：");
            for (Map<String, Object> s : low) {
                facts.append(s.get("studentname")).append("(").append(s.get("avg")).append("分) ");
            }
            facts.append("\n");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> weak = (List<Map<String, Object>>) stats.get("commonWeak");
        if (weak != null && !weak.isEmpty()) {
            facts.append("班级高频错题/薄弱点：");
            for (Map<String, Object> w : weak) {
                facts.append("[").append(w.get("title")).append("] ");
            }
            facts.append("\n");
        }

        String system = "你是一位经验丰富的教学督导。请根据课程的整体学情数据，为任课教师生成一份班级学情周报。" +
                "用 Markdown 输出，包含：## 整体概况、## 共性薄弱点分析、## 需重点关注的学生、## 教学改进建议（3-5条）。" +
                "分析要基于数据，建议要具体可操作，面向教师视角，避免空话。不要编造数据中没有的信息。";
        String user = "以下是该课程的学情数据：\n" + facts;

        String content;
        try {
            content = aiSafeCallService.callText("insight.classReport", system, user);
        } catch (Exception e) {
            logger.error("生成班级学情周报失败: {}", e.getMessage());
            return Result.error("AI 生成失败：" + e.getMessage());
        }
        saveReport("class", courseid, coursename + " · 班级学情周报", content, stats);

        Map<String, Object> result = new HashMap<>();
        result.put("title", coursename + " · 班级学情周报");
        result.put("content", content);
        result.put("stats", stats);
        result.put("generatedAt", LocalDateTime.now().format(TS));
        return Result.success(result);
    }

    private Map<String, Object> collectClassStats(Integer courseid) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("submitCount", queryInt(
                "SELECT COUNT(*) FROM studenthomework sh JOIN homework h ON sh.homeworkid=h.homeworkid " +
                "WHERE h.courseid=? AND sh.status>0", courseid));
        stats.put("homeworkAvg", round1(queryDouble(
                "SELECT AVG(sh.score) FROM studenthomework sh JOIN homework h ON sh.homeworkid=h.homeworkid " +
                "WHERE h.courseid=? AND sh.score IS NOT NULL", courseid)));
        stats.put("examCount", queryInt(
                "SELECT COUNT(*) FROM exam_record er JOIN exam e ON er.examid=e.examid " +
                "WHERE e.courseid=? AND er.status>=1", courseid));
        stats.put("examAvg", round1(queryDouble(
                "SELECT AVG(er.score) FROM exam_record er JOIN exam e ON er.examid=e.examid " +
                "WHERE e.courseid=? AND er.score IS NOT NULL", courseid)));

        List<Map<String, Object>> perStudent = new ArrayList<>();
        try {
            perStudent = jdbcTemplate.queryForList(
                "SELECT s.studentname AS studentname, ROUND(AVG(sh.score),1) AS avg " +
                "FROM studenthomework sh JOIN homework h ON sh.homeworkid=h.homeworkid " +
                "JOIN student s ON sh.studentid=s.studentid " +
                "WHERE h.courseid=? AND sh.score IS NOT NULL GROUP BY sh.studentid ORDER BY avg ASC", courseid);
        } catch (Exception ignored) {}

        // 成绩分布桶
        int[] buckets = new int[5]; // <60, 60-69, 70-79, 80-89, 90-100
        List<Map<String, Object>> low = new ArrayList<>();
        for (Map<String, Object> r : perStudent) {
            double avg = toDouble(r.get("avg"));
            if (avg < 60) buckets[0]++;
            else if (avg < 70) buckets[1]++;
            else if (avg < 80) buckets[2]++;
            else if (avg < 90) buckets[3]++;
            else buckets[4]++;
            if (low.size() < 8 && avg < 70) low.add(r);
        }
        Map<String, Object> dist = new HashMap<>();
        dist.put("lt60", buckets[0]);
        dist.put("b60", buckets[1]);
        dist.put("b70", buckets[2]);
        dist.put("b80", buckets[3]);
        dist.put("b90", buckets[4]);
        stats.put("distribution", dist);
        stats.put("lowPerformers", low);
        stats.put("studentCount", perStudent.size());

        List<Map<String, Object>> weak = new ArrayList<>();
        try {
            weak = jdbcTemplate.queryForList(
                "SELECT LEFT(question_title,60) AS title, SUM(wrong_count) AS cnt " +
                "FROM student_wrong_question WHERE courseid=? AND mastered=0 AND question_title IS NOT NULL " +
                "GROUP BY question_title ORDER BY cnt DESC LIMIT 8", courseid);
        } catch (Exception ignored) {}
        stats.put("commonWeak", weak);
        return stats;
    }

    // ==================== 个性化智能练习 ====================

    /** 学生存在未掌握错题的课程列表（用于练习时按课程筛选） */
    public List<Map<String, Object>> practiceCourses(Integer studentid) {
        try {
            return jdbcTemplate.queryForList(
                "SELECT w.courseid AS courseid, COALESCE(c.coursename,'未分类') AS coursename, COUNT(*) AS count " +
                "FROM student_wrong_question w LEFT JOIN course c ON w.courseid=c.courseid " +
                "WHERE w.studentid=? AND w.mastered=0 AND w.courseid IS NOT NULL " +
                "GROUP BY w.courseid ORDER BY count DESC", studentid);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public Result<Map<String, Object>> smartPractice(Integer studentid, Integer courseid, int count) {
        // 题目数量以用户选择为准（3~10），不得被错题条数截断
        int n = Math.max(3, Math.min(10, count <= 0 ? 5 : count));
        List<Map<String, Object>> weak;
        try {
            if (courseid != null) {
                weak = jdbcTemplate.queryForList(
                    "SELECT w.id AS id, question_type, LEFT(question_title,120) AS title, " +
                    "COALESCE((SELECT coursename FROM course c WHERE c.courseid=w.courseid),'') AS coursename " +
                    "FROM student_wrong_question w WHERE w.studentid=? AND w.courseid=? AND w.mastered=0 " +
                    "ORDER BY w.wrong_count DESC LIMIT ?", studentid, courseid, n);
            } else {
                weak = jdbcTemplate.queryForList(
                    "SELECT w.id AS id, question_type, LEFT(question_title,120) AS title, " +
                    "COALESCE((SELECT coursename FROM course c WHERE c.courseid=w.courseid),'') AS coursename " +
                    "FROM student_wrong_question w WHERE w.studentid=? AND w.mastered=0 " +
                    "ORDER BY w.wrong_count DESC LIMIT ?", studentid, n);
            }
        } catch (Exception e) {
            weak = new ArrayList<>();
        }

        String system;
        String user;
        if (weak.isEmpty()) {
            String topic = strOr(courseid != null ? queryString("SELECT coursename FROM course WHERE courseid=?", courseid) : null, "");
            String kb = topic.isEmpty() ? "该学生暂无错题记录，请围绕计算机/编程基础知识出综合练习题。"
                    : ("围绕课程《" + topic + "》的核心基础知识出综合练习题。");
            system = practiceSystemPrompt(false, n);
            user = "请生成恰好 " + n + " 道练习题，questions 数组长度必须为 " + n + "。\n" + kb;
        } else {
            StringBuilder kb = new StringBuilder();
            kb.append("以下是该学生尚未掌握的错题（共 ").append(weak.size()).append(" 道），作为出题参考：\n");
            int idx = 1;
            for (Map<String, Object> w : weak) {
                kb.append(idx++).append(". [").append(strOr((String) w.get("coursename"), "通用")).append("] ")
                  .append("(").append(w.get("question_type")).append(") ")
                  .append(w.get("title")).append("\n");
            }
            system = practiceSystemPrompt(true, n);
            user = "请生成恰好 " + n + " 道练习题，questions 数组长度必须为 " + n + "。\n"
                    + "规则：\n"
                    + "1. 优先为每道错题出考查相同知识点的【变式题】（题干不同，题型尽量与原题一致）；\n"
                    + "2. 若错题数量少于 " + n + "，请对同一薄弱知识点继续出不同变式，或补充同课程相关巩固题，必须凑满 "
                    + n + " 道；\n"
                    + "3. 不要输出少于或多于 " + n + " 道题。\n"
                    + kb;
        }

        List<Map<String, Object>> questions = generatePracticeQuestions(system, user);
        if (questions.isEmpty()) {
            return Result.error("AI 返回内容解析失败，请重试");
        }

        // AI 偶发少题：再补一次差额
        if (questions.size() < n) {
            int need = n - questions.size();
            String topUpUser = "请再生成恰好 " + need + " 道练习题（与已有题目考查点相近但题干不同），"
                    + "questions 数组长度必须为 " + need + "。不要重复已出过的题干。";
            if (!weak.isEmpty()) {
                StringBuilder ref = new StringBuilder();
                for (Map<String, Object> w : weak) {
                    if (ref.length() > 0) ref.append("；");
                    ref.append(w.get("title"));
                }
                topUpUser += "\n薄弱参考：" + ref;
            }
            List<Map<String, Object>> extra = generatePracticeQuestions(practiceSystemPrompt(!weak.isEmpty(), need), topUpUser);
            for (Map<String, Object> q : extra) {
                if (questions.size() >= n) break;
                questions.add(q);
            }
        }
        if (questions.size() > n) {
            questions = new ArrayList<>(questions.subList(0, n));
        }

        // 回填来源错题ID（轮询映射，多道变式可对应同一错题，答对即可归档）
        if (!weak.isEmpty()) {
            for (int i = 0; i < questions.size(); i++) {
                questions.get(i).put("sourceWrongId", weak.get(i % weak.size()).get("id"));
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("questions", questions);
        result.put("basedOnWrong", !weak.isEmpty());
        result.put("requestedCount", n);
        result.put("actualCount", questions.size());
        return Result.success(result);
    }

    private List<Map<String, Object>> generatePracticeQuestions(String system, String user) {
        try {
            String raw = aiSafeCallService.callText("insight.smartPractice", system, user);
            return parseQuestions(raw);
        } catch (Exception e) {
            logger.error("生成智能练习失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String practiceSystemPrompt(boolean variant, int count) {
        return "你是一位命题专家。" +
                (variant ? "请针对给定错题的薄弱知识点出变式练习题，帮助学生举一反三。"
                         : "请针对学生的薄弱知识点，出一套巩固练习题，帮助学生针对性提升。") +
                "严格只输出 JSON，不要任何额外文字。questions 数组必须恰好包含 " + count + " 道题，结构如下：" +
                "{\"questions\":[{\"type\":\"single|multi|judge|blank\",\"title\":\"题干\"," +
                "\"options\":[\"A选项\",\"B选项\"],\"answer\":\"single为正确选项下标(从0开始)的数字字符串;multi为多个下标用逗号分隔如0,2;judge为true或false;blank为正确答案文本\"," +
                "\"analysis\":\"简明解析\",\"knowledge\":\"考查的知识点\"}]}。" +
                "judge与blank题的options为空数组。";
    }

    /** 智能练习答对后归档对应错题（标记已掌握并发放攻克积分） */
    public int markPracticeMastered(Integer studentid, List<Integer> wrongIds) {
        if (wrongIds == null || wrongIds.isEmpty()) return 0;
        int n = 0;
        for (Integer id : wrongIds) {
            if (id == null) continue;
            try {
                int updated = jdbcTemplate.update(
                    "UPDATE student_wrong_question SET mastered=1 WHERE id=? AND studentid=? AND mastered=0", id, studentid);
                if (updated > 0) {
                    n++;
                    gamificationService.onMasterWrongQuestion(studentid);
                }
            } catch (Exception e) {
                logger.warn("归档错题失败 id={}: {}", id, e.getMessage());
            }
        }
        return n;
    }

    private List<Map<String, Object>> parseQuestions(String raw) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            String json = extractJson(raw);
            JsonNode root = objectMapper.readTree(json);
            JsonNode qs = root.has("questions") ? root.get("questions") : root;
            if (qs.isArray()) {
                for (JsonNode q : qs) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("type", q.path("type").asText("single"));
                    m.put("title", q.path("title").asText(""));
                    List<String> opts = new ArrayList<>();
                    if (q.has("options") && q.get("options").isArray()) {
                        for (JsonNode o : q.get("options")) opts.add(o.asText());
                    }
                    m.put("options", opts);
                    m.put("answer", q.path("answer").asText(""));
                    m.put("analysis", q.path("analysis").asText(""));
                    m.put("knowledge", q.path("knowledge").asText(""));
                    if (!((String) m.get("title")).isEmpty()) list.add(m);
                }
            }
        } catch (Exception e) {
            logger.warn("解析练习题JSON失败: {}", e.getMessage());
        }
        return list;
    }

    public void rewardPractice(Integer studentid, int correct, int total) {
        try {
            int award = 2 + (total > 0 && correct * 2 >= total ? 3 : 0); // 及格额外+3
            gamificationService.addPoints(studentid, award, "PRACTICE", "完成AI智能练习 +" + award);
        } catch (Exception e) {
            logger.warn("智能练习积分发放失败: {}", e.getMessage());
        }
    }

    // ==================== 知识点思维导图 ====================

    public Result<Map<String, Object>> mindmap(String topic) {
        if (topic == null || topic.trim().isEmpty()) {
            return Result.error("请输入主题");
        }
        String t = topic.trim();
        String system = "你是一位学科知识体系专家。请为给定主题构建一张结构化知识点思维导图。" +
                "严格只输出 JSON，不要任何额外文字，结构为层级树：" +
                "{\"name\":\"根主题\",\"children\":[{\"name\":\"一级分支\",\"children\":[{\"name\":\"二级要点\"}]}]}。" +
                "要求：根节点为主题本身；一级分支 4-7 个，覆盖该主题的主要模块；每个一级分支下 2-5 个关键知识点；" +
                "层级不超过 3 层；节点名称简洁（不超过 18 字）。";
        String user = "主题：" + t;

        String raw;
        try {
            raw = aiSafeCallService.callText("insight.mindmap", system, user);
        } catch (Exception e) {
            logger.error("生成思维导图失败: {}", e.getMessage());
            return Result.error("AI 生成失败：" + e.getMessage());
        }
        try {
            String json = extractJson(raw);
            JsonNode root = objectMapper.readTree(json);
            Map<String, Object> tree = objectMapper.convertValue(root, Map.class);
            Map<String, Object> result = new HashMap<>();
            result.put("tree", tree);
            result.put("topic", t);
            return Result.success(result);
        } catch (Exception e) {
            logger.warn("解析思维导图JSON失败: {}", e.getMessage());
            return Result.error("AI 返回内容解析失败，请重试");
        }
    }

    /** 思维导图节点的二级 AI 详解 */
    public Result<Map<String, Object>> mindmapNodeDetail(String topic, String node, String path) {
        if (node == null || node.trim().isEmpty()) {
            return Result.error("缺少知识点");
        }
        String system = "你是一位学科讲解专家。请针对给定知识点做一段精炼讲解，用 Markdown 输出，包含：" +
                "**核心定义/要点**、**典型用法或示例**、**常见易错点**三个小标题，总字数控制在 350 字以内，" +
                "示例可用代码块。讲解要准确、好懂，紧扣该知识点本身。";
        StringBuilder user = new StringBuilder();
        if (topic != null && !topic.trim().isEmpty()) user.append("所属主题：").append(topic.trim()).append("\n");
        if (path != null && !path.trim().isEmpty()) user.append("知识点路径：").append(path.trim()).append("\n");
        user.append("请讲解知识点：").append(node.trim());

        String content;
        try {
            content = aiSafeCallService.callText("insight.mindmapNode", system, user.toString());
        } catch (Exception e) {
            logger.error("生成节点详解失败: {}", e.getMessage());
            return Result.error("AI 生成失败：" + e.getMessage());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("node", node.trim());
        result.put("content", content);
        return Result.success(result);
    }

    // ==================== 通用工具 ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> latestReport(String type, Integer refId) {
        Map<String, Object> row = aiReportRepository.findLatest(type, refId).orElse(null);
        if (row == null) return null;

        Map<String, Object> report = new HashMap<>();
        report.put("title", row.get("title"));
        report.put("content", row.get("content"));
        report.put("generatedAt", String.valueOf(row.get("createTime")));

        Object statsJson = row.get("statsJson");
        if (statsJson != null) {
            try {
                report.put("stats", objectMapper.readValue(statsJson.toString(), Map.class));
            } catch (Exception e) {
                // 统计块损坏时仍然把正文展示出来
                logger.warn("AI 报告统计数据解析失败 type={} ref={}: {}", type, refId, e.getMessage());
            }
        }
        return report;
    }

    private void saveReport(String type, Integer refId, String title, String content, Map<String, Object> stats) {
        String statsJson = null;
        if (stats != null) {
            try {
                statsJson = objectMapper.writeValueAsString(stats);
            } catch (Exception e) {
                logger.warn("AI 报告统计数据序列化失败: {}", e.getMessage());
            }
        }
        aiReportRepository.replaceLatest(type, refId, title, content, statsJson);
    }

    private String extractJson(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        // 去除 ```json ``` 包裹
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl >= 0) s = s.substring(nl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
            s = s.trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }

    private Integer queryInt(String sql, Object... args) {
        try {
            Integer v = jdbcTemplate.queryForObject(sql, Integer.class, args);
            return v == null ? 0 : v;
        } catch (Exception e) {
            return 0;
        }
    }

    private Double queryDouble(String sql, Object... args) {
        try {
            Double v = jdbcTemplate.queryForObject(sql, Double.class, args);
            return v == null ? 0d : v;
        } catch (Exception e) {
            return 0d;
        }
    }

    private String queryString(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, String.class, args);
        } catch (Exception e) {
            return null;
        }
    }

    private double round1(Double v) {
        if (v == null) return 0;
        return Math.round(v * 10) / 10.0;
    }

    private double toDouble(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0; }
    }

    private String strOr(String v, String def) {
        return (v == null || v.trim().isEmpty()) ? def : v;
    }
}
