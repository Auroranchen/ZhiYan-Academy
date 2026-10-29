package com.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.model.Message;
import com.repository.LearningResourceRepository;
import com.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习多智能体编排服务（基于大模型的个性化资源生成）。
 * 由若干专职智能体协作完成个性化学习资源的生成：
 *  - 诊断智能体 Diagnose：基于学情数据定位薄弱知识点
 *  - 规划智能体 Plan：制定分阶段学习路径
 *  - 资源智能体 Resource：为薄弱点生成讲义/示例
 *  - 出题智能体 Quiz：生成针对性练习题
 *  - 编排智能体 Assemble：汇总成完整资源包并入库
 * 前端按步调用各智能体接口，从而实现“协作过程可视化”。
 */
@Service
public class LearningAgentService {

    private static final Logger logger = LoggerFactory.getLogger(LearningAgentService.class);

    /** 仅用于诊断环节读取学情统计；资源包的读写一律走仓储 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LearningResourceRepository learningResourceRepository;

    @Autowired
    private GamificationService gamificationService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private CourseKnowledgeContextService courseKnowledgeContextService;

    @Autowired
    private AiSafeCallService aiSafeCallService;

    @Autowired
    private LearningProfileService learningProfileService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 诊断智能体（学生） ====================

    public Result<Map<String, Object>> diagnoseStudent(Integer studentid, Integer courseid) {
        long t0 = System.currentTimeMillis();
        String name = strOr(queryString("SELECT studentname FROM student WHERE studentid=?", studentid), "同学");
        String coursename = courseid != null
                ? strOr(queryString("SELECT coursename FROM course WHERE courseid=?", courseid), "课程") : null;

        Map<String, Object> stats = new HashMap<>();
        boolean byCourse = courseid != null;
        stats.put("homeworkAvg", round1(byCourse
                ? queryDouble("SELECT AVG(sh.score) FROM studenthomework sh JOIN homework h ON sh.homeworkid=h.homeworkid WHERE sh.studentid=? AND sh.score IS NOT NULL AND h.courseid=?", studentid, courseid)
                : queryDouble("SELECT AVG(score) FROM studenthomework WHERE studentid=? AND score IS NOT NULL", studentid)));
        stats.put("examAvg", round1(byCourse
                ? queryDouble("SELECT AVG(er.score) FROM exam_record er JOIN exam e ON er.examid=e.examid WHERE er.studentid=? AND er.score IS NOT NULL AND e.courseid=?", studentid, courseid)
                : queryDouble("SELECT AVG(score) FROM exam_record WHERE studentid=? AND score IS NOT NULL", studentid)));
        stats.put("wrongTotal", byCourse
                ? queryInt("SELECT COUNT(*) FROM student_wrong_question WHERE studentid=? AND courseid=?", studentid, courseid)
                : queryInt("SELECT COUNT(*) FROM student_wrong_question WHERE studentid=?", studentid));
        stats.put("wrongUnmastered", byCourse
                ? queryInt("SELECT COUNT(*) FROM student_wrong_question WHERE studentid=? AND courseid=? AND mastered=0", studentid, courseid)
                : queryInt("SELECT COUNT(*) FROM student_wrong_question WHERE studentid=? AND mastered=0", studentid));

        List<Map<String, Object>> wrongs = new ArrayList<>();
        try {
            if (byCourse) {
                wrongs = jdbcTemplate.queryForList(
                    "SELECT LEFT(question_title,80) AS title, question_type AS type, wrong_count AS cnt " +
                    "FROM student_wrong_question WHERE studentid=? AND courseid=? AND mastered=0 AND question_title IS NOT NULL " +
                    "ORDER BY wrong_count DESC LIMIT 12", studentid, courseid);
            } else {
                wrongs = jdbcTemplate.queryForList(
                    "SELECT LEFT(question_title,80) AS title, question_type AS type, wrong_count AS cnt, " +
                    "COALESCE((SELECT coursename FROM course c WHERE c.courseid=w.courseid),'') AS coursename " +
                    "FROM student_wrong_question w WHERE w.studentid=? AND w.mastered=0 AND w.question_title IS NOT NULL " +
                    "ORDER BY w.wrong_count DESC LIMIT 12", studentid);
            }
        } catch (Exception ignored) {}

        StringBuilder facts = new StringBuilder();
        facts.append("学习者：").append(name).append("\n");
        facts.append("范围：").append(coursename != null ? ("《" + coursename + "》") : "全部课程").append("\n");
        facts.append("作业平均分：").append(stats.get("homeworkAvg")).append("，考试平均分：").append(stats.get("examAvg")).append("\n");
        facts.append("待攻克错题：").append(stats.get("wrongUnmastered")).append(" / 共 ").append(stats.get("wrongTotal")).append("\n");
        if (!wrongs.isEmpty()) {
            facts.append("典型错题（题型·题干·累计错误）：\n");
            for (Map<String, Object> w : wrongs) {
                facts.append("- (").append(w.get("type")).append(") ").append(w.get("title"))
                     .append(" ×").append(w.get("cnt")).append("\n");
            }
        }

        String system = "你是学习诊断智能体。请根据学习者的学情数据，定位其薄弱知识点并给出深入诊断。" +
                "严格只输出 JSON：{\"summary\":\"两句话总体诊断(先结论后依据)\",\"level\":\"基础薄弱|稳步提升|优秀\"," +
                "\"strengths\":[\"优势项1\",\"优势项2\"]," +
                "\"weakPoints\":[{\"name\":\"知识点名称\",\"reason\":\"判断依据(引用具体数据)\",\"priority\":\"高|中|低\"," +
                "\"suggestion\":\"针对该薄弱点的一条具体提升建议(可执行,20-40字)\"}]}。" +
                "weakPoints 控制在 3-6 个，按优先级排序；strengths 1-3 个，若数据不足可为空数组。不要编造数据中没有的信息。";

        String profileBrief = profileBrief(studentid);
        if (!profileBrief.isEmpty()) {
            facts.append("学习画像：").append(profileBrief).append("\n");
        }

        Map<String, Object> parsed = callJson("agent.diagnose", system, "学情数据：\n" + facts);
        if (parsed == null) return Result.error("诊断智能体解析失败，请重试");

        Map<String, Object> result = new HashMap<>(parsed);
        result.put("agent", "诊断智能体");
        result.put("subject", coursename != null ? coursename : "综合");
        result.put("learner", name);
        result.put("stats", stats);
        result.put("elapsedMs", System.currentTimeMillis() - t0);
        return Result.success(result);
    }

    // ==================== 诊断智能体（教师 / 班级课程级） ====================

    public Result<Map<String, Object>> diagnoseClass(Integer courseid) {
        if (courseid == null) return Result.error("请选择课程");
        long t0 = System.currentTimeMillis();
        String coursename = strOr(queryString("SELECT coursename FROM course WHERE courseid=?", courseid), "课程");

        Map<String, Object> stats = new HashMap<>();
        stats.put("homeworkAvg", round1(queryDouble(
                "SELECT AVG(sh.score) FROM studenthomework sh JOIN homework h ON sh.homeworkid=h.homeworkid WHERE h.courseid=? AND sh.score IS NOT NULL", courseid)));
        stats.put("examAvg", round1(queryDouble(
                "SELECT AVG(er.score) FROM exam_record er JOIN exam e ON er.examid=e.examid WHERE e.courseid=? AND er.score IS NOT NULL", courseid)));
        stats.put("studentCount", queryInt(
                "SELECT COUNT(*) FROM student WHERE classid IN (SELECT classid FROM courseclass WHERE courseid=?)", courseid));

        List<Map<String, Object>> weak = new ArrayList<>();
        try {
            weak = jdbcTemplate.queryForList(
                "SELECT LEFT(question_title,80) AS title, question_type AS type, SUM(wrong_count) AS cnt, COUNT(DISTINCT studentid) AS stu " +
                "FROM student_wrong_question WHERE courseid=? AND mastered=0 AND question_title IS NOT NULL " +
                "GROUP BY question_title ORDER BY cnt DESC LIMIT 12", courseid);
        } catch (Exception ignored) {}

        StringBuilder facts = new StringBuilder();
        facts.append("课程：").append(coursename).append("\n");
        facts.append("选课学生：").append(stats.get("studentCount")).append(" 人\n");
        facts.append("作业平均分：").append(stats.get("homeworkAvg")).append("，考试平均分：").append(stats.get("examAvg")).append("\n");
        if (!weak.isEmpty()) {
            facts.append("班级高频错题（题型·题干·累计错误·涉及人数）：\n");
            for (Map<String, Object> w : weak) {
                facts.append("- (").append(w.get("type")).append(") ").append(w.get("title"))
                     .append(" ×").append(w.get("cnt")).append("，").append(w.get("stu")).append("人\n");
            }
        }

        String system = "你是班级学情诊断智能体。请根据课程整体数据，归纳班级共性薄弱知识点。" +
                "严格只输出 JSON：{\"summary\":\"两句话班级诊断(先结论后依据)\",\"level\":\"整体薄弱|良好|优秀\"," +
                "\"strengths\":[\"班级整体优势1\"]," +
                "\"weakPoints\":[{\"name\":\"知识点\",\"reason\":\"依据(引用具体数据,如错误人数)\",\"priority\":\"高|中|低\"," +
                "\"suggestion\":\"面向教学的一条改进建议(可执行,20-40字)\"}]}。" +
                "weakPoints 控制在 3-6 个；strengths 1-3 个，数据不足可为空数组。不要编造数据中没有的信息。";

        Map<String, Object> parsed = callJson("agent.diagnoseClass", system, "课程学情：\n" + facts);
        if (parsed == null) return Result.error("诊断智能体解析失败，请重试");

        Map<String, Object> result = new HashMap<>(parsed);
        result.put("agent", "班级诊断智能体");
        result.put("subject", coursename);
        result.put("stats", stats);
        result.put("elapsedMs", System.currentTimeMillis() - t0);
        return Result.success(result);
    }

    // ==================== 规划智能体 ====================

    public Result<Map<String, Object>> plan(String subject, List<String> weakPoints, boolean classLevel,
                                            Integer courseId, Integer studentId) {
        return plan(subject, weakPoints, classLevel, courseId, studentId, null);
    }

    public Result<Map<String, Object>> plan(String subject, List<String> weakPoints, boolean classLevel,
                                            Integer courseId, Integer studentId, String collaborationInstruction) {
        long t0 = System.currentTimeMillis();
        CourseKnowledgeContextService.KnowledgeContext knowledge =
                courseKnowledgeContextService.build(courseId, safe(subject) + " " + String.join(" ", nonNull(weakPoints)));
        String system = "你是学习规划智能体。请基于薄弱知识点制定一份分阶段的" + (classLevel ? "班级教学" : "个性化学习") + "路径。" +
                "严格只输出 JSON：{\"stages\":[{\"title\":\"阶段名\",\"goal\":\"阶段目标\",\"days\":\"建议天数\"," +
                "\"items\":[\"具体任务1\",\"具体任务2\",\"具体任务3\"]," +
                "\"milestone\":\"阶段验收标准(怎样算完成,一句话,可检验)\"}]}。" +
                "阶段 3-4 个，循序渐进；任务具体可执行（写明做什么、做多少，如「完成XX知识点10道练习」），" +
                "前后阶段任务不重复，难度逐级提升。若提供学习画像，路径节奏与任务形式须贴合画像中的认知风格与学习节奏。";
        String user = withKnowledge(withProfile(
                "主题：" + safe(subject) + "\n薄弱知识点：" + String.join("、", nonNull(weakPoints)) +
                        collaborationLine(collaborationInstruction),
                studentId), knowledge);

        Map<String, Object> parsed = callJson("agent.plan", system, user);
        if (parsed == null) return Result.error("规划智能体解析失败，请重试");
        Map<String, Object> result = new HashMap<>(parsed);
        result.put("agent", "规划智能体");
        putGrounding(result, knowledge);
        result.put("elapsedMs", System.currentTimeMillis() - t0);
        return Result.success(result);
    }

    // ==================== 资源生成智能体 ====================

    public Result<Map<String, Object>> resource(String subject, List<String> weakPoints, Integer courseId,
                                                Integer studentId) {
        return resource(subject, weakPoints, courseId, studentId, null);
    }

    public Result<Map<String, Object>> resource(String subject, List<String> weakPoints, Integer courseId,
                                                Integer studentId, String collaborationInstruction) {
        long t0 = System.currentTimeMillis();
        CourseKnowledgeContextService.KnowledgeContext knowledge =
                courseKnowledgeContextService.build(courseId, safe(subject) + " " + String.join(" ", nonNull(weakPoints)));
        List<String> points = nonNull(weakPoints);
        if (points.size() > 4) points = points.subList(0, 4);
        String system = "你是学习资源生成智能体。请为每个知识点生成一份高质量学习讲义。" +
                "严格只输出 JSON：{\"sections\":[{\"knowledge\":\"知识点\",\"content\":\"Markdown讲义\"}]}。" +
                "每份讲义用 Markdown，依次包含六部分：**核心要点**（2-3条）、**通俗讲解**（用生活化类比帮助理解）、" +
                "**示例**（编程类知识点必须给可运行代码块并附关键行注释；非编程类给具体实例）、" +
                "**易错提醒**（最常见的1-2个误区及纠正）、**记忆要诀**（一句口诀或记忆方法）、" +
                "**自测一题**（一道带答案的小题，用于即学即测）。每份 350-550 字。讲解准确、深入浅出、紧扣该知识点。" +
                "若提供学习画像，讲解方式须贴合画像（如视觉型学习者多用结构图与对比表述）。";
        String user = withKnowledge(withProfile(
                "主题：" + safe(subject) + "\n需要生成讲义的知识点：" + String.join("、", points) +
                        collaborationLine(collaborationInstruction),
                studentId), knowledge);

        Map<String, Object> parsed = callJson("agent.resource", system, user);
        if (parsed == null) return Result.error("资源智能体解析失败，请重试");
        Map<String, Object> result = new HashMap<>(parsed);
        result.put("agent", "资源生成智能体");
        putGrounding(result, knowledge);
        result.put("elapsedMs", System.currentTimeMillis() - t0);
        return Result.success(result);
    }

    // ==================== 出题智能体 ====================

    public Result<Map<String, Object>> quiz(String subject, List<String> weakPoints, int count, Integer courseId) {
        return quiz(subject, weakPoints, count, courseId, null);
    }

    public Result<Map<String, Object>> quiz(String subject, List<String> weakPoints, int count, Integer courseId,
                                            String collaborationInstruction) {
        long t0 = System.currentTimeMillis();
        CourseKnowledgeContextService.KnowledgeContext knowledge =
                courseKnowledgeContextService.build(courseId, safe(subject) + " " + String.join(" ", nonNull(weakPoints)));
        int n = Math.max(3, Math.min(10, count));
        String system = "你是出题智能体。请针对给定知识点出一套高质量巩固练习题。" +
                "严格只输出 JSON：{\"questions\":[{\"type\":\"single|multi|judge|blank\",\"title\":\"题干\"," +
                "\"options\":[\"A选项\",\"B选项\"],\"answer\":\"single为正确下标(0起)数字字符串;multi为多个下标逗号分隔如0,2;judge为true或false;blank为答案文本\"," +
                "\"analysis\":\"解析：先讲正确答案的依据，选择题再简要说明主要干扰项为什么错\"," +
                "\"knowledge\":\"考查知识点\",\"difficulty\":1}]}。" +
                "judge与blank的options为空数组。difficulty 取 1(基础)/2(进阶)/3(挑战)，整套题难度按 4:4:2 左右搭配并覆盖所有给定知识点。" +
                "干扰项须似是而非（对应典型误区），不出送分题。";
        String user = withKnowledge(
                "主题：" + safe(subject) + "\n知识点：" + String.join("、", nonNull(weakPoints)) +
                        "\n请出 " + n + " 道题。" + collaborationLine(collaborationInstruction),
                knowledge);

        Map<String, Object> parsed = callJson("agent.quiz", system, user);
        if (parsed == null) return Result.error("出题智能体解析失败，请重试");
        Map<String, Object> result = new HashMap<>();
        result.put("agent", "出题智能体");
        result.put("questions", parsed.get("questions") != null ? parsed.get("questions") : new ArrayList<>());
        putGrounding(result, knowledge);
        result.put("elapsedMs", System.currentTimeMillis() - t0);
        return Result.success(result);
    }

    // ==================== 思维导图智能体 ====================

    public Result<Map<String, Object>> mindmap(String subject, List<String> weakPoints, Integer courseId) {
        return mindmap(subject, weakPoints, courseId, null);
    }

    public Result<Map<String, Object>> mindmap(String subject, List<String> weakPoints, Integer courseId,
                                               String collaborationInstruction) {
        long t0 = System.currentTimeMillis();
        CourseKnowledgeContextService.KnowledgeContext knowledge =
                courseKnowledgeContextService.build(courseId, safe(subject) + " " + String.join(" ", nonNull(weakPoints)));
        String system = "你是知识思维导图智能体。请围绕主题与薄弱知识点构建一张层级清晰的思维导图。" +
                "严格只输出 JSON：{\"mindmap\":{\"name\":\"中心主题\",\"children\":[{\"name\":\"分支\"," +
                "\"children\":[{\"name\":\"子知识点\"}]}]}}。层级 2-3 层，分支 3-6 个，命名精炼准确。";
        String user = withKnowledge(
                "主题：" + safe(subject) + "\n需重点覆盖的薄弱知识点：" + String.join("、", nonNull(weakPoints)) +
                        collaborationLine(collaborationInstruction),
                knowledge);
        Map<String, Object> parsed = callJson("agent.mindmap", system, user);
        if (parsed == null) return Result.error("思维导图智能体解析失败，请重试");
        Map<String, Object> result = new HashMap<>();
        result.put("agent", "思维导图智能体");
        result.put("mindmap", parsed.get("mindmap"));
        putGrounding(result, knowledge);
        result.put("elapsedMs", System.currentTimeMillis() - t0);
        return Result.success(result);
    }

    // ==================== 拓展阅读智能体 ====================

    public Result<Map<String, Object>> reading(String subject, List<String> weakPoints, Integer courseId) {
        return reading(subject, weakPoints, courseId, null);
    }

    public Result<Map<String, Object>> reading(String subject, List<String> weakPoints, Integer courseId,
                                               String collaborationInstruction) {
        long t0 = System.currentTimeMillis();
        CourseKnowledgeContextService.KnowledgeContext knowledge =
                courseKnowledgeContextService.build(courseId, safe(subject) + " " + String.join(" ", nonNull(weakPoints)));
        String system = "你是拓展阅读推荐智能体。请为学习者推荐高质量的拓展学习材料。" +
                "严格只输出 JSON：{\"readings\":[{\"title\":\"材料标题\",\"type\":\"教材章节|经典论文|技术博客|官方文档|视频课程\"," +
                "\"level\":\"入门|进阶|深入\",\"summary\":\"一句话简介\",\"why\":\"为什么推荐给该薄弱点\"," +
                "\"takeaway\":\"读完应重点掌握什么(一句话)\"}]}。推荐 4-6 条，入门到深入梯度搭配。" +
                "只推荐真实存在、领域内公认的经典材料；不要编造不存在的标题、作者或链接，宁可给经典教材/官方文档也不要臆造。";
        String user = withKnowledge(
                "主题：" + safe(subject) + "\n薄弱知识点：" + String.join("、", nonNull(weakPoints)) +
                        collaborationLine(collaborationInstruction),
                knowledge);
        Map<String, Object> parsed = callJson("agent.reading", system, user);
        if (parsed == null) return Result.error("拓展阅读智能体解析失败，请重试");
        Map<String, Object> result = new HashMap<>();
        result.put("agent", "拓展阅读智能体");
        result.put("readings", parsed.get("readings") != null ? parsed.get("readings") : new ArrayList<>());
        putGrounding(result, knowledge);
        result.put("elapsedMs", System.currentTimeMillis() - t0);
        return Result.success(result);
    }

    // ==================== 代码实操案例智能体 ====================

    public Result<Map<String, Object>> codecase(String subject, List<String> weakPoints, Integer courseId) {
        return codecase(subject, weakPoints, courseId, null);
    }

    public Result<Map<String, Object>> codecase(String subject, List<String> weakPoints, Integer courseId,
                                                String collaborationInstruction) {
        long t0 = System.currentTimeMillis();
        CourseKnowledgeContextService.KnowledgeContext knowledge =
                courseKnowledgeContextService.build(courseId, safe(subject) + " " + String.join(" ", nonNull(weakPoints)));
        List<String> points = nonNull(weakPoints);
        if (points.size() > 3) points = points.subList(0, 3);
        String system = "你是代码实操案例智能体。请为知识点设计可动手实践的代码案例（若学科非编程类，则给出可操作的实践步骤/实验设计）。" +
                "严格只输出 JSON：{\"cases\":[{\"title\":\"案例标题\",\"language\":\"语言或工具(如C/Python/SQL/实验)\"," +
                "\"scenario\":\"应用场景说明(贴近真实开发/生活)\",\"code\":\"完整可运行代码或操作步骤(换行用\\n)\"," +
                "\"expectedOutput\":\"预期运行结果/现象(便于学习者自查)\",\"explanation\":\"关键点讲解(逐段说明设计思路)\"," +
                "\"challenge\":\"进阶挑战：在本案例基础上的一个改造任务(一句话)\"}]}。" +
                "案例 2-3 个。代码须可运行、含中文注释、逻辑自洽，不要使用不确定的库用法。";
        String user = withKnowledge(
                "主题：" + safe(subject) + "\n需实操巩固的知识点：" + String.join("、", points) +
                        collaborationLine(collaborationInstruction),
                knowledge);
        Map<String, Object> parsed = callJson("agent.codecase", system, user);
        if (parsed == null) return Result.error("代码实操智能体解析失败，请重试");
        Map<String, Object> result = new HashMap<>();
        result.put("agent", "代码实操智能体");
        result.put("cases", parsed.get("cases") != null ? parsed.get("cases") : new ArrayList<>());
        putGrounding(result, knowledge);
        result.put("elapsedMs", System.currentTimeMillis() - t0);
        return Result.success(result);
    }

    // ==================== 多模态讲解动画（分镜脚本）智能体 ====================

    /**
     * 生成「讲解动画」分镜脚本：用于前端配音 + 动效渲染的轻量多模态微课。
     */
    public Result<Map<String, Object>> explainer(String subject, String topic) {
        return explainer(subject, topic, null, null);
    }

    public Result<Map<String, Object>> explainer(String subject, String topic, Integer courseId, Integer studentId) {
        return explainer(subject, topic, courseId, studentId, null);
    }

    public Result<Map<String, Object>> explainer(String subject, String topic, Integer courseId, Integer studentId,
                                                 String collaborationInstruction) {
        long t0 = System.currentTimeMillis();
        if (topic == null || topic.trim().isEmpty()) return Result.error("请输入讲解主题");
        CourseKnowledgeContextService.KnowledgeContext knowledge =
                courseKnowledgeContextService.build(courseId, safe(subject) + " " + topic.trim());
        String system = "你是一位耐心、亲切、善于启发学生的微课老师兼多模态动画编剧。请把一个知识点拆解成一段可播放的「讲解动画」分镜脚本，用于「TTS 配音 + 动效演示」的微课。" +
                "严格只输出 JSON：{\"title\":\"微课标题\",\"summary\":\"一句话简介\"," +
                "\"scenes\":[{\"title\":\"分镜小标题\",\"narration\":\"旁白(亲切自然、口语化、可直接朗读, 35-80字)\",\"keypoint\":\"本镜一句要点(≤15字)\"," +
                "\"visual\":{\"type\":\"center|bullets|steps|compare|formula|code|quiz\",\"items\":[\"要点短语\"],\"left\":\"\",\"right\":\"\",\"text\":\"\"," +
                "\"lang\":\"\",\"answer\":0,\"explain\":\"\"}}]}。" +
                "分镜 6-8 个，遵循认知节奏：生活化问题引入 → 一句话定义 → 原理拆解 → 具体示例 → 易错辨析 → 互动小测 → 回顾与鼓励。" +
                "每个分镜的 visual.type 选最贴切一种：bullets/steps 用 items(2-4 条精炼短语)；compare 用 left/right(各一短句)；formula 与 center 用 text；" +
                "code 用于编程类示例：text 放完整代码片段(换行用\\n, ≤12行, 含中文注释)，lang 放语言名，items 放 1-3 条关键行说明；" +
                "quiz 用于互动小测(倒数第二镜)：text 放题目，items 放 3 个选项，answer 放正确选项下标(0起)，explain 放一句解析，旁白只读题不读答案。" +
                "编程类知识点必须包含一个 code 分镜；每支微课必须包含一个 quiz 分镜。" +
                "旁白要求：像老师面对一位学生轻声讲解，多用“我们”“你可以这样理解”，句子长短交替并用逗号自然停顿；先讲直觉再讲术语；每镜只传达一个核心信息；" +
                "避免生硬书面语、连续反问、夸张语气和术语堆砌。开场不说套话，结尾用一句具体的学习鼓励收束。视觉文字必须短、准、易扫读，不能照抄整段旁白。";
        String user = withKnowledge(withProfile(
                "学科/主题：" + safe(subject) + "\n本次讲解的知识点：" + topic.trim() +
                        collaborationLine(collaborationInstruction),
                studentId), knowledge);

        Map<String, Object> parsed = callJson("agent.explainer", system, user);
        if (parsed == null) return Result.error("讲解动画智能体解析失败，请重试");

        Map<String, Object> result = new HashMap<>();
        result.put("agent", "讲解动画智能体");
        result.put("title", strOr((String) parsed.get("title"), topic.trim() + " · 讲解微课"));
        result.put("summary", parsed.get("summary"));
        result.put("scenes", parsed.get("scenes") != null ? parsed.get("scenes") : new ArrayList<>());
        putGrounding(result, knowledge);
        result.put("elapsedMs", System.currentTimeMillis() - t0);
        return Result.success(result);
    }

    // ==================== 协调 / 评审 / 返工智能体 ====================

    public Result<Map<String, Object>> coordinate(String subject, List<String> weakPoints,
                                                   boolean classLevel, Integer courseId, Integer studentId) {
        long t0 = System.currentTimeMillis();
        CourseKnowledgeContextService.KnowledgeContext knowledge =
                courseKnowledgeContextService.build(courseId, safe(subject) + " " + String.join(" ", nonNull(weakPoints)));
        String system = "你是多智能体协调智能体。负责把学习目标拆成可协作任务，并明确各智能体的职责、输入重点和质量标准。" +
                "严格只输出 JSON：{\"strategy\":\"总体协作策略\",\"assignments\":[" +
                "{\"agent\":\"plan|resource|mindmap|quiz|reading|codecase|explainer\",\"focusPoints\":[\"知识点\"]," +
                "\"instruction\":\"该智能体的具体任务与和其他产物的衔接要求\"}]," +
                "\"qualityCriteria\":[\"可验证质量标准\"]," +
                "\"handoffs\":[{\"from\":\"plan|resource|mindmap|quiz|reading|codecase|explainer\"," +
                "\"to\":\"同上七个之一\",\"artifact\":\"交接的具体产物\",\"reason\":\"为什么前者必须先于后者\"}]}。" +
                "【handoffs 的语义】一条 {from,to} 表示：to 必须**在 from 之后**执行，且 from 的产物会被摘要后提供给 to。" +
                "因此只声明确实存在依赖的交接——多余的交接会让执行顺序变得莫名其妙。" +
                "没有强依赖关系时可以给空数组，系统会按默认顺序执行。" +
                "注意 from/to 只能取上面七个值，不要写智能体中文名，也不要写诊断/协调/评审/编排（它们的位置由系统固定）。" +
                "assignments 必须覆盖全部7个资源智能体；每个知识点至少被两类资源交叉覆盖；" +
                "明确讲义与微课、练习与路径、代码与思维导图之间如何互相印证，避免各自独立生成。";
        String user = withKnowledge(withProfile(
                "模式：" + (classLevel ? "班级教学" : "学生个性化学习") +
                        "\n主题：" + safe(subject) +
                        "\n诊断薄弱点：" + String.join("、", nonNull(weakPoints)),
                studentId), knowledge);
        Map<String, Object> parsed = callJson("agent.coordinate", system, user);
        if (parsed == null) return Result.error("协调智能体解析失败，请重试");
        Map<String, Object> result = new HashMap<>(parsed);
        result.put("agent", "协调智能体");
        putGrounding(result, knowledge);
        result.put("elapsedMs", System.currentTimeMillis() - t0);
        return Result.success(result);
    }

    public Result<Map<String, Object>> reviewArtifacts(String subject, List<String> weakPoints,
                                                       Map<String, Object> artifacts, boolean finalReview) {
        long t0 = System.currentTimeMillis();
        String system = "你是独立质量评审智能体，不参与内容生成。请交叉检查资源包各产物的一致性、正确性、个性化程度和相互衔接。" +
                "严格只输出 JSON：{\"approved\":true,\"score\":0,\"summary\":\"评审结论\"," +
                "\"issues\":[{\"agent\":\"plan|resource|mindmap|quiz|reading|codecase|explainer\"," +
                "\"severity\":\"high|medium|low\",\"problem\":\"问题\",\"suggestion\":\"可执行修订要求\"}]," +
                "\"retryAgents\":[\"需要返工的智能体key\"],\"checks\":{" +
                "\"consistency\":\"通过|需改进\",\"coverage\":\"通过|需改进\",\"accuracy\":\"通过|需改进\",\"personalization\":\"通过|需改进\"}}。" +
                "评分0-100；只有 score>=80 且不存在 high 问题时 approved=true。" +
                (finalReview
                        ? "这是返工后的最终复审，只报告剩余问题，retryAgents 必须为空。"
                        : "retryAgents 最多2个，只选择最影响学习效果的产物，避免无效返工。");
        String artifactJson = compactJson(artifacts, 14000);
        String user = "主题：" + safe(subject) + "\n目标薄弱点：" + String.join("、", nonNull(weakPoints)) +
                "\n待评审共享任务状态与产物：\n" + artifactJson;
        Map<String, Object> parsed = callJson(finalReview ? "agent.reviewFinal" : "agent.review", system, user);
        if (parsed == null) return Result.error("评审智能体解析失败，请重试");
        Map<String, Object> result = new HashMap<>(parsed);
        result.put("agent", finalReview ? "复审智能体" : "质量评审智能体");
        result.put("round", finalReview ? 2 : 1);
        result.put("elapsedMs", System.currentTimeMillis() - t0);
        return Result.success(result);
    }

    public Result<Map<String, Object>> reviseArtifact(String agentKey, String subject, List<String> weakPoints,
                                                      Map<String, Object> previous, Map<String, Object> review,
                                                      Integer courseId, Integer studentId) {
        long t0 = System.currentTimeMillis();
        CourseKnowledgeContextService.KnowledgeContext knowledge = courseKnowledgeContextService.build(
                courseId, safe(subject) + " " + String.join(" ", nonNull(weakPoints)) + " " + compactJson(review, 1500));
        String system = "你是返工修订智能体。请针对质量评审意见修订指定产物，保持原有 JSON 顶层结构和字段类型，" +
                "只输出修订后的完整 JSON，不要输出解释、Markdown围栏或新增无关字段。必须修复 high/medium 问题，" +
                "并保持与主题、薄弱点及其他资源的一致性。";
        String user = withKnowledge(withProfile("返工对象：" + safe(agentKey) + "\n主题：" + safe(subject) +
                "\n薄弱点：" + String.join("、", nonNull(weakPoints)) +
                "\n原产物：" + compactJson(previous, 10000) +
                "\n评审意见：" + compactJson(review, 5000), studentId), knowledge);
        Map<String, Object> parsed = callJson("agent.revise." + safe(agentKey), system, user);
        if (parsed == null) return Result.error(agentKey + " 返工解析失败");
        Map<String, Object> result = new HashMap<>(parsed);
        result.put("agent", agentKey + "修订智能体");
        result.put("revised", true);
        putGrounding(result, knowledge);
        result.put("elapsedMs", System.currentTimeMillis() - t0);
        return Result.success(result);
    }

    // ==================== 编排/汇总智能体 + 入库 ====================

    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> assemble(String ownerType, Integer ownerId, String scopeType,
                                                Integer courseId, Map<String, Object> pack) {
        long t0 = System.currentTimeMillis();
        String subject = safe((String) pack.get("subject"));
        List<String> weakPoints = new ArrayList<>();
        Object wp = pack.get("weakPoints");
        if (wp instanceof List) {
            for (Object o : (List<Object>) wp) weakPoints.add(String.valueOf(o));
        }

        String system = "你是编排智能体，负责把诊断、规划、讲义、思维导图、练习、拓展阅读、代码实操、多模态微课与质量评审整合成一份个性化学习资源包的导读。" +
                "用 Markdown 输出一段 250 字内的导读，包含：本资源包面向的薄弱点、包含的内容、" +
                "推荐的使用顺序（先看什么后做什么）、预计学习总时长、以及一句针对性的学习方法建议。简洁、有条理、有针对性。";
        String user = "主题：" + subject + "\n薄弱知识点：" + String.join("、", weakPoints) +
                "\n包含：分阶段学习路径、知识点讲义、知识思维导图、针对性练习题、拓展阅读材料、代码实操案例、多模态动画微课。" +
                "\n质量评审：" + compactJson(pack.get("qualityReview"), 2000);

        String overview;
        try {
            overview = aiSafeCallService.callText("agent.assemble", system, user);
        } catch (Exception e) {
            logger.error("编排智能体失败: {}", e.getMessage());
            return Result.error("编排智能体生成失败：" + e.getMessage());
        }

        String coursename = courseId != null
                ? strOr(queryString("SELECT coursename FROM course WHERE courseid=?", courseId), "课程") : null;
        String title = strOr((String) pack.get("title"),
                (coursename != null ? coursename : (subject.isEmpty() ? "综合" : subject)) + " · 个性化学习资源包");

        pack.put("overview", overview);
        pack.put("title", title);

        Integer savedId = null;
        try {
            savedId = learningResourceRepository.insert(new LearningResourceRepository.NewResource(
                    ownerType, ownerId, scopeType, courseId, coursename, title,
                    objectMapper.writeValueAsString(pack), null)).orElse(null);
        } catch (Exception e) {
            logger.warn("序列化资源包失败: {}", e.getMessage());
        }

        // 学生生成资源包给予积分激励
        if ("student".equals(ownerType) && ownerId != null) {
            try { gamificationService.addPoints(ownerId, 5, "AGENT", "生成个性化学习资源包 +5"); } catch (Exception ignored) {}
        }

        Map<String, Object> result = new HashMap<>();
        result.put("agent", "编排智能体");
        result.put("overview", overview);
        result.put("title", title);
        result.put("id", savedId);
        result.put("elapsedMs", System.currentTimeMillis() - t0);
        return Result.success(result);
    }

    // ==================== 资源库 ====================

    public List<Map<String, Object>> library(String ownerType, Integer ownerId) {
        return learningResourceRepository.listByOwner(ownerType, ownerId);
    }

    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> libraryDetail(String ownerType, Integer ownerId, Integer id) {
        Map<String, Object> row = learningResourceRepository.findOwned(ownerType, ownerId, id).orElse(null);
        if (row == null) return Result.error("资源不存在或无权访问");

        Map<String, Object> pack = new HashMap<>();
        Object packJson = row.get("packJson");
        if (packJson != null) {
            try {
                pack = objectMapper.readValue(packJson.toString(), Map.class);
            } catch (Exception e) {
                // 历史数据可能存了非法 JSON，退化成只显示标题与时间
                logger.warn("资源包内容解析失败 id={}: {}", id, e.getMessage());
            }
        }
        pack.put("title", row.get("title"));
        pack.put("createTime", String.valueOf(row.get("createTime")));
        return Result.success(pack);
    }

    /** 教师把班级资源包一键推送给课程内全部学生（复制进各自资源库 + 站内信通知） */
    public Result<Map<String, Object>> pushToStudents(Integer teacherid, Integer id) {
        Map<String, Object> source = learningResourceRepository.findTeacherResource(teacherid, id).orElse(null);
        if (source == null) return Result.error("资源不存在或无权操作");

        Integer courseId = source.get("courseId") != null
                ? ((Number) source.get("courseId")).intValue() : null;
        if (courseId == null) return Result.error("该资源未关联课程，无法推送");

        List<Integer> studentIds = learningResourceRepository.findStudentIdsByCourse(courseId);
        if (studentIds.isEmpty()) return Result.error("该课程暂无学生");

        String courseName = (String) source.get("courseName");
        String baseTitle = (String) source.get("title");
        String packJson = String.valueOf(source.get("packJson"));
        String pushTitle = "【老师推送】" + baseTitle;

        int pushed = 0;
        for (Integer studentId : studentIds) {
            learningResourceRepository.deletePushedCopy(studentId, id);

            Integer copyId = learningResourceRepository.insert(new LearningResourceRepository.NewResource(
                    "student", studentId, "course", courseId, courseName, pushTitle, packJson, id)).orElse(null);
            if (copyId == null) {
                logger.warn("推送资源给学生 {} 失败：未能写入副本", studentId);
                continue;
            }

            pushed++;
            notifyPushed(teacherid, studentId, baseTitle, courseName, copyId);
        }

        Map<String, Object> out = new HashMap<>();
        out.put("pushed", pushed);
        out.put("total", studentIds.size());
        return Result.success(out);
    }

    /** 站内信通知（含 WebSocket 实时推送）；通知失败不影响资源已推送的事实 */
    private void notifyPushed(Integer teacherid, Integer studentId, String title,
                              String courseName, Integer resourceId) {
        try {
            Message msg = new Message();
            msg.setTitle("老师推送了学习资源包");
            msg.setContent("老师推送了个性化学习资源包《" + title + "》"
                    + (courseName != null ? ("（课程：" + courseName + "）") : "")
                    + "，请在「多智能体工作台 → 资源库」中查看。\n[resourceid:" + resourceId + "]");
            msg.setSendertype(2);
            msg.setSenderid(teacherid);
            msg.setReceivertype(1);
            msg.setReceiverid(studentId);
            messageService.send(msg);
        } catch (Exception e) {
            logger.warn("推送资源通知失败 student={}: {}", studentId, e.getMessage());
        }
    }

    public Result<Void> deleteResource(String ownerType, Integer ownerId, Integer id) {
        return learningResourceRepository.deleteOwned(ownerType, ownerId, id)
                ? Result.success()
                : Result.error("删除失败或无权操作");
    }

    // ==================== 工具 ====================

    private Map<String, Object> callJson(String scene, String system, String user) {
        // 统一走安全网关：防幻觉约束 + 输入清洗 + 输出过滤 + 调用指标记录
        return aiSafeCallService.callJson(scene, system, user);
    }

    private String compactJson(Object value, int maxLength) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return json.length() <= maxLength ? json : json.substring(0, maxLength) + "…";
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String collaborationLine(String instruction) {
        return instruction == null || instruction.isBlank()
                ? ""
                : "\n协调智能体交接要求：" + instruction.trim();
    }

    /** 学生 6 维学习画像摘要，注入个性化生成的提示词 */
    private String profileBrief(Integer studentId) {
        if (studentId == null) return "";
        try {
            Map<String, Object> profile = learningProfileService.getProfile(studentId);
            Object summary = profile.get("summary");
            StringBuilder sb = new StringBuilder();
            if (summary != null && !String.valueOf(summary).isBlank() && !"null".equals(String.valueOf(summary))) {
                sb.append(summary);
            }
            Object dims = profile.get("dimensions");
            if (dims instanceof Map) {
                for (Object v : ((Map<?, ?>) dims).values()) {
                    if (!(v instanceof Map)) continue;
                    Map<?, ?> dim = (Map<?, ?>) v;
                    Object name = dim.get("name");
                    Object label = dim.get("label");
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

    private String withProfile(String task, Integer studentId) {
        String brief = profileBrief(studentId);
        if (brief.isEmpty()) return task;
        return task + "\n\n【学习者画像】" + brief;
    }

    private List<String> nonNull(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in != null) for (String s : in) if (s != null && !s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    private String withKnowledge(String task, CourseKnowledgeContextService.KnowledgeContext knowledge) {
        if (knowledge == null || knowledge.sourceCount() == 0) return task;
        return task + "\n\n【课程知识库（共 " + knowledge.sourceCount() + " 份资料）】\n" + knowledge.text();
    }

    private void putGrounding(Map<String, Object> result,
                              CourseKnowledgeContextService.KnowledgeContext knowledge) {
        if (knowledge == null) return;
        result.put("groundingSourceCount", knowledge.sourceCount());
        result.put("groundingSources", knowledge.sources());
        result.put("citations", knowledge.citations());
        String agent = String.valueOf(result.getOrDefault("agent", ""));
        boolean required = agent.contains("资源生成") || agent.contains("拓展阅读")
                || agent.contains("代码实操") || agent.contains("讲解动画");
        result.put("citationValidation",
                courseKnowledgeContextService.validateCitations(result, knowledge, required));
    }

    private String safe(String s) { return s == null ? "" : s; }

    private Integer queryInt(String sql, Object... args) {
        try {
            Integer v = jdbcTemplate.queryForObject(sql, Integer.class, args);
            return v == null ? 0 : v;
        } catch (Exception e) { return 0; }
    }

    private Double queryDouble(String sql, Object... args) {
        try {
            Double v = jdbcTemplate.queryForObject(sql, Double.class, args);
            return v == null ? 0d : v;
        } catch (Exception e) { return 0d; }
    }

    private String queryString(String sql, Object... args) {
        try { return jdbcTemplate.queryForObject(sql, String.class, args); }
        catch (Exception e) { return null; }
    }

    private double round1(Double v) { return v == null ? 0 : Math.round(v * 10) / 10.0; }

    private String strOr(String v, String def) { return (v == null || v.trim().isEmpty()) ? def : v; }
}
