package com.controller;

import com.config.RequireRole;
import com.model.Homework;
import com.model.Course;
import com.model.StudentHomework;
import com.response.Result;
import com.service.AIService;
import com.service.AiAnswerGuard;
import com.service.AiAssistContentPolicy;
import com.service.FileService;
import com.service.HomeworkService;
import com.service.HomeworkChatPolicyResolver;
import com.service.AiSafeCallService;
import com.service.PPTService;
import com.service.StudentHomeworkService;
import com.service.ZhipuAIService;
import com.service.CourseKnowledgeContextService;
import com.service.CourseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpSession;
import jakarta.annotation.PreDestroy;

import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI控制器 - 处理AI相关请求
 */
@RestController
@RequestMapping("/ai")
public class AIController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AIController.class);

    @Value("${app.ai-chat.max-system-chars:16000}")
    private int maxSystemChars;

    @Value("${app.ai-chat.max-message-chars:24000}")
    private int maxMessageChars;

    @Value("${app.ai-chat.max-concurrent-per-user:2}")
    private int maxConcurrentPerUser;

    @Autowired
    private AIService aiService;

    @Autowired
    private ZhipuAIService zhipuAIService;

    @Autowired
    private CourseKnowledgeContextService courseKnowledgeContextService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private FileService fileService;

    @Autowired
    private PPTService pptService;

    @Autowired
    private StudentHomeworkService studentHomeworkService;

    @Autowired
    private HomeworkService homeworkService;

    @Autowired
    private HomeworkChatPolicyResolver homeworkChatPolicyResolver;

    @Autowired
    private AiSafeCallService aiSafeCallService;

    /**
     * AI连通性诊断（服务器排查用）。
     *
     * <p>限管理员：响应里含掩码后的 API Key、接口地址与模型名，且<b>每次调用都是一次
     * 真实的计费请求</b>——此前无角色限制，任何已登录学生都能拿它当免费的 AI 探针用。
     */
    @GetMapping("/diagnose")
    @RequireRole("admin")
    public Result<Map<String, Object>> diagnose() {
        Map<String, Object> result = zhipuAIService.diagnose();
        return Result.success(result);
    }

    /**
     * AI识别试题
     * @param params 包含text字段的请求体
     * @return 解析后的试题JSON
     */
    @PostMapping("/parseQuestions")
    @RequireRole({"teacher", "admin"})
    public Result<String> parseQuestions(@RequestBody Map<String, String> params) {
        String text = params.get("text");
        if (text == null || text.trim().isEmpty()) {
            return Result.error("请输入试题内容");
        }

        try {
            String result = aiService.parseQuestions(text);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("AI识别失败: " + e.getMessage());
        }
    }

    /**
     * AI解析错题
     * @param params 包含题目信息的请求体
     * @return 解析内容
     */
    @PostMapping("/analyzeWrongAnswer")
    @RequireRole({"student", "teacher", "admin"})
    public Result<String> analyzeWrongAnswer(@RequestBody Map<String, String> params) {
        String questionTitle = params.get("questionTitle");
        String questionType = params.get("questionType");
        String options = params.get("options");
        String correctAnswer = params.get("correctAnswer");
        String studentAnswer = params.get("studentAnswer");

        if (questionTitle == null || questionTitle.trim().isEmpty()) {
            return Result.error("题目内容不能为空");
        }

        try {
            String result = zhipuAIService.analyzeWrongAnswer(questionTitle, questionType, options, correctAnswer, studentAnswer);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("AI解析失败: " + e.getMessage());
        }
    }

    /**
     * AI自动出题
     * @param params 包含出题参数的请求体
     * @return 生成的试题JSON
     */
    @PostMapping("/generateQuestions")
    @RequireRole({"teacher", "admin"})
    public Result<String> generateQuestions(@RequestBody Map<String, Object> params) {
        String subject = (String) params.get("subject");
        Integer singleCount = params.get("singleCount") != null ? Integer.parseInt(params.get("singleCount").toString()) : 0;
        Integer multiCount = params.get("multiCount") != null ? Integer.parseInt(params.get("multiCount").toString()) : 0;
        Integer judgeCount = params.get("judgeCount") != null ? Integer.parseInt(params.get("judgeCount").toString()) : 0;
        Integer blankCount = params.get("blankCount") != null ? Integer.parseInt(params.get("blankCount").toString()) : 0;
        Integer shortCount = params.get("shortCount") != null ? Integer.parseInt(params.get("shortCount").toString()) : 0;
        String difficulty = (String) params.get("difficulty");

        int totalCount = singleCount + multiCount + judgeCount + blankCount + shortCount;
        if (subject == null || subject.trim().isEmpty()) {
            return Result.error("请输入出题主题/科目");
        }
        if (totalCount <= 0) {
            return Result.error("请至少设置一种题型的数量");
        }
        if (totalCount > 20) {
            return Result.error("单次出题数量不能超过20道");
        }

        try {
            String result = zhipuAIService.generateQuestions(subject, totalCount, singleCount, multiCount, judgeCount, blankCount, shortCount, difficulty);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("AI出题失败: " + e.getMessage());
        }
    }

    @PostMapping("/generateQuestionsFromPPT")
    @RequireRole({"teacher", "admin"})
    public Result<String> generateQuestionsFromPPT(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "中等") String difficulty,
            @RequestParam(required = false, defaultValue = "3") Integer singleCount,
            @RequestParam(required = false, defaultValue = "2") Integer multiCount,
            @RequestParam(required = false, defaultValue = "2") Integer judgeCount,
            @RequestParam(required = false, defaultValue = "0") Integer blankCount,
            @RequestParam(required = false, defaultValue = "0") Integer shortCount
    ) {
        if (file == null || file.isEmpty()) {
            return Result.error("请上传PPT文件");
        }
        String name = file.getOriginalFilename();
        if (!fileService.isPptFile(name)) {
            return Result.error("仅支持上传PPT文件（.ppt或.pptx）");
        }
        int totalCount = (singleCount == null ? 0 : singleCount)
                + (multiCount == null ? 0 : multiCount)
                + (judgeCount == null ? 0 : judgeCount)
                + (blankCount == null ? 0 : blankCount)
                + (shortCount == null ? 0 : shortCount);
        if (totalCount <= 0) {
            return Result.error("请至少设置一种题型的数量");
        }
        if (totalCount > 20) {
            return Result.error("单次出题数量不能超过20道");
        }
        try {
            String saved = fileService.saveFile(file);
            String abs = fileService.getAbsolutePath(saved);
            String content = pptService.readLimitedContent(abs, 20, 12);
            if (content == null || content.trim().isEmpty()) {
                return Result.error("PPT内容为空，无法出题");
            }
            // 规整和截断内容，避免提示词过长导致AI接口失败
            content = content.replaceAll("\\r\\n", "\n").replaceAll("\\t", " ").replaceAll(" +", " ").trim();
            int maxLen = 3000;
            if (content.length() > maxLen) {
                content = content.substring(0, maxLen);
            }
            String result = zhipuAIService.generateQuestionsFromContent(content, totalCount,
                    singleCount, multiCount, judgeCount, blankCount, shortCount, difficulty);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("PPT出题失败: " + e.getMessage());
        }
    }

    /**
     * AI作业情况分析（学生端）
     * 根据学生ID和作业ID，查询数据库获取作业详情和学生答题情况，交给AI分析
     */
    @PostMapping("/analyzeHomework")
    @RequireRole("student")
    public Result<String> analyzeHomework(@RequestBody Map<String, Object> params, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        Integer homeworkid = params.get("homeworkid") != null ? Integer.parseInt(params.get("homeworkid").toString()) : null;

        if (studentid == null || homeworkid == null) {
            return Result.error("参数不完整");
        }

        try {
            // 查询作业基本信息
            Homework homework = homeworkService.getById(homeworkid);
            if (homework == null) {
                return Result.error("作业不存在");
            }

            // 查询学生的提交记录
            StudentHomework sh = studentHomeworkService.getByHomeworkAndStudent(homeworkid, studentid);
            if (sh == null) {
                return Result.error("未找到该学生的作业记录");
            }

            // 构建数据上下文
            StringBuilder context = new StringBuilder();
            context.append("作业标题：").append(homework.getTitle()).append("\n");
            context.append("课程：").append(sh.getCoursename() != null ? sh.getCoursename() : "未知").append("\n");
            context.append("作业类型：").append(homework.getType() == 1 ? "在线答题" : "文档上传").append("\n");
            if (homework.getDeadline() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                context.append("截止时间：").append(sdf.format(homework.getDeadline())).append("\n");
            }
            context.append("提交状态：").append(sh.getStatus() == 0 ? "未提交" : sh.getStatus() == 1 ? "已提交" : sh.getStatus() == 2 ? "已批改" : "已打回").append("\n");
            if (sh.getScore() != null) {
                context.append("成绩：").append(sh.getScore()).append("\n");
            }
            if (sh.getComment() != null && !sh.getComment().trim().isEmpty()) {
                context.append("教师评语：").append(sh.getComment()).append("\n");
            }

            // 如果是在线答题，附上试题和学生答案
            if (homework.getType() == 1) {
                if (homework.getContent() != null && !homework.getContent().trim().isEmpty()) {
                    String hwContent = homework.getContent();
                    if (hwContent.length() > 2000) hwContent = hwContent.substring(0, 2000);
                    context.append("\n试题内容（JSON）：\n").append(hwContent).append("\n");
                }
                if (sh.getContent() != null && !sh.getContent().trim().isEmpty()) {
                    String stuContent = sh.getContent();
                    if (stuContent.length() > 2000) stuContent = stuContent.substring(0, 2000);
                    context.append("\n学生答案（JSON）：\n").append(stuContent).append("\n");
                }
            }

            String systemPrompt = "你是一位专业的教学助手，负责帮助学生分析作业完成情况。\n" +
                    "请根据提供的作业数据，给出以下分析：\n" +
                    "1. 作业完成概况（提交状态、得分情况）\n" +
                    "2. 如果是在线答题，逐题分析对错，指出错误原因和正确思路\n" +
                    "3. 薄弱知识点总结\n" +
                    "4. 针对性的学习建议\n\n" +
                    "要求：\n" +
                    "- 语气友好、鼓励为主\n" +
                    "- 分析要具体，不要泛泛而谈\n" +
                    "- 用中文回答\n" +
                    "- 使用 Markdown 格式排版";

            String userPrompt = "请分析以下学生的作业情况：\n\n" + context.toString();

            String result = aiSafeCallService.callText("chat.analyzeHomework", systemPrompt, userPrompt);
            return Result.success(result);
        } catch (Exception e) {
            log.error("AI 分析失败", e);
            return Result.error("AI分析失败: " + e.getMessage());
        }
    }

    /**
     * AI助手聊天（前端代理，避免CORS和API Key暴露）
     */
    // 作业辅导的防线（提示词约束 + 输出侧答案特征检测）已抽到 com.service.AiAnswerGuard。
    // 抽出去的直接原因：安全评测必须用**与生产完全相同**的检测器去测泄露率，
    // 若评测另写一套判定，测的就是副本而不是真正的防线。
    // 用法：AiAnswerGuard.HOMEWORK_GUARD / AiAnswerGuard.GUARD_USER_PREFIX / AiAnswerGuard.BLOCKED_REPLY / AiAnswerGuard.containsAnswer(...)

    /** 在受限（作业辅导）模式下统一走这里：先过提示词约束，再对输出做答案拦截 */
    private Result<String> answer(String systemPrompt, String userMessage,
                                  Integer courseId, boolean restricted) {
        String effectiveSystem = (courseId == null)
                ? systemPrompt
                : appendCourseKnowledge(systemPrompt, courseId, userMessage);
        String effectiveUser = userMessage;
        if (restricted) {
            effectiveSystem += AiAnswerGuard.HOMEWORK_GUARD;
            effectiveUser = AiAnswerGuard.GUARD_USER_PREFIX + userMessage;
        }
        try {
            // 走安全网关：防幻觉提示词、输入消洗、输出过滤与 ai_call_log 统一在网关内完成，
            // 不必在这里重复那套 withAntiHallucination + sanitizeModelInput + filterOutput。
            String reply = aiSafeCallService.callText(
                    restricted ? "homework_chat" : "chat", effectiveSystem, effectiveUser);
            if (restricted && AiAnswerGuard.containsAnswer(reply)) {
                // 作业模式下 AI 直接给答案属于违规输出，记录前 100 字便于回溯 prompt 问题
                log.warn("[AI-GUARD] 拦截到答案类回复，已替换。原回复前100字: {}",
                        reply.length() > 100 ? reply.substring(0, 100) : reply);
                return Result.success(AiAnswerGuard.BLOCKED_REPLY);
            }
            return Result.success(reply);
        } catch (Exception e) {
            return Result.error("AI助手暂时无法响应: " + e.getMessage());
        }
    }

    private static final String GENERAL_SYSTEM_PROMPT =
            "你是智学云课堂的AI助手\"小智\"。请用友好、专业的语气回答问题，回答要简洁明了。";

    /**
     * 作业辅导对话固定的系统提示词。
     *
     * <p>作业辅导**不接受**客户端提供的 systemPrompt——那是绕过防线最直接的入口
     * （原实现正是靠读它来判断要不要加固）。
     */
    private static final String HOMEWORK_CHAT_SYSTEM_PROMPT =
            "你是智枢学堂的AI学习助手。学生正在完成作业，请引导他独立思考，" +
            "讲解通用知识点与解题方法，但绝不给出题目答案。";

    /**
     * 通用 AI 助手（非作业语义）。
     *
     * <p><b>安全要点：</b>客户端传来的 systemPrompt 只当作角色/语气前缀，
     * <b>绝不参与策略判定</b>。此前的实现用「systemPrompt 是否包含某个中文短语」
     * 来决定是否启用作业加固，而那两个短语只有前端会拼——等于把防线交给它本该
     * 约束的一方来开关，省略一个子串即可完全绕过。需要作业加固请走
     * {@link #homeworkChat}。
     */
    @PostMapping("/chat")
    public Result<String> chat(@RequestBody Map<String, String> params, HttpSession session) {
        if (!isLoggedIn(session)) return Result.error(401, "请先登录");
        String userMessage = params.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Result.error("消息不能为空");
        }
        String clientSystemPrompt = params.get("systemPrompt");
        if (exceedsChatLimits(clientSystemPrompt, userMessage)) {
            return Result.error(413, "对话内容过长，请缩短问题或清空部分历史后重试");
        }
        String systemPrompt = (clientSystemPrompt == null || clientSystemPrompt.trim().isEmpty())
                ? GENERAL_SYSTEM_PROMPT
                : clientSystemPrompt;
        Integer courseId = parseCourseId(params.get("courseId"));
        if (courseId != null && !hasCourseAccess(courseId, session)) {
            return Result.error(403, "无权访问该课程资料");
        }
        return answer(systemPrompt, userMessage, courseId, false);
    }

    /**
     * 作业辅导对话（学生专用）。
     *
     * <p>与通用助手的根本区别：<b>客户端完全不参与策略判定</b>。加固与否由服务端查库
     * 决定（见 {@link HomeworkChatPolicyResolver}）。作业 ID 必填，缺失即 400——
     * 而不是静默降级到无加固的通用模式，那正是原先漏洞的成因。
     */
    @PostMapping("/homework-chat")
    public Result<String> homeworkChat(@RequestBody Map<String, String> params, HttpSession session) {
        if (!isLoggedIn(session)) return Result.error(401, "请先登录");

        HomeworkChatPolicyResolver.HomeworkChatPolicy policy = homeworkChatPolicyResolver.resolve(
                parsePositiveInt(params.get("homeworkId")),
                asInteger(session.getAttribute("studentid")));
        if (!policy.allowed()) {
            return Result.error(400, policy.rejectionReason());
        }

        String userMessage = params.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Result.error("消息不能为空");
        }
        if (exceedsChatLimits(null, userMessage)) {
            return Result.error(413, "对话内容过长，请缩短问题后重试");
        }
        return answer(HOMEWORK_CHAT_SYSTEM_PROMPT, userMessage,
                policy.courseId(), policy.restricted());
    }

    /** 有界线程池，防止大量 SSE 长连接无限创建工作线程。 */
    private final ThreadPoolExecutor streamExecutor = new ThreadPoolExecutor(
            4, 16, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), r -> {
                Thread thread = new Thread(r, "ai-chat-stream");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final Map<String, AtomicInteger> activeStreams = new ConcurrentHashMap<>();
    private final ObjectMapper streamMapper = new ObjectMapper();

    private static final String GENERAL_STREAM_SYSTEM_PROMPT =
            "你是智枢学堂的AI助手\"小枢\"。请用友好、专业的语气回答问题，回答要简洁明了。";

    /**
     * AI 助手「真·流式输出」(SSE)：逐 token 推送。
     * 每个事件 data 为 JSON 编码后的字符串片段；结束推送 "[DONE]"。
     *
     * <p>通用接口，无作业语义——需要加固请走 {@link #homeworkChatStream}。
     */
    @PostMapping("/chat-stream")
    public SseEmitter chatStream(@RequestBody Map<String, String> params, HttpSession session) {
        SseEmitter emitter = new SseEmitter(180000L);
        if (!isLoggedIn(session)) return failStream(emitter);

        String sp = params.get("systemPrompt");
        String systemPrompt = (sp == null || sp.trim().isEmpty()) ? GENERAL_STREAM_SYSTEM_PROMPT : sp;
        Integer courseId = parseCourseId(params.get("courseId"));
        if (courseId != null && !hasCourseAccess(courseId, session)) {
            completeStreamWithMessage(emitter, "无权访问该课程资料");
            return emitter;
        }
        return streamAnswer(emitter, systemPrompt, params.get("message"), courseId, false, session);
    }

    /**
     * 作业辅导流式对话（学生专用）。
     *
     * <p>策略同样完全由服务端决定：客户端既不能提供 systemPrompt，也不能通过漏传参数
     * 退回通用模式——作业 ID 缺失会走到 {@code allowed()==false} 并直接下发送拒绝信息。
     */
    @PostMapping("/homework-chat-stream")
    public SseEmitter homeworkChatStream(@RequestBody Map<String, String> params, HttpSession session) {
        SseEmitter emitter = new SseEmitter(180000L);
        if (!isLoggedIn(session)) return failStream(emitter);

        HomeworkChatPolicyResolver.HomeworkChatPolicy policy = homeworkChatPolicyResolver.resolve(
                parsePositiveInt(params.get("homeworkId")),
                asInteger(session.getAttribute("studentid")));
        if (!policy.allowed()) {
            completeStreamWithMessage(emitter, policy.rejectionReason());
            return emitter;
        }
        return streamAnswer(emitter, HOMEWORK_CHAT_SYSTEM_PROMPT, params.get("message"),
                policy.courseId(), policy.restricted(), session);
    }

    private SseEmitter failStream(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().data(streamMapper.writeValueAsString("[ERROR]")));
            emitter.complete();
        } catch (Exception ignored) {
            // 客户端可能已断开，无可补偿动作
        }
        return emitter;
    }

    /**
     * 两个流式端点共用的应答流程。
     *
     * @param restricted true 时不逐 token 下发，而是整段生成后做答案拦截再一次性发出——
     *                   答案特征只能在整个回复生成之后才能判定，逐 token 放行做不到拦截。
     */
    private SseEmitter streamAnswer(SseEmitter emitter, String systemPrompt, String userMessage,
                                    Integer courseId, boolean restricted, HttpSession session) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            completeStreamWithMessage(emitter, "消息不能为空");
            return emitter;
        }
        if (exceedsChatLimits(systemPrompt, userMessage)) {
            completeStreamWithMessage(emitter, "对话内容过长，请缩短问题或清空部分历史后重试");
            return emitter;
        }
        final String effectiveSystem = (courseId == null)
                ? systemPrompt
                : appendCourseKnowledge(systemPrompt, courseId, userMessage);
        final String effectiveUser = AiAssistContentPolicy.sanitizeModelInput(userMessage);

        final String streamUserKey = userKey(session);
        if (!tryAcquireStream(streamUserKey)) {
            completeStreamWithMessage(emitter, "已有较多 AI 回答正在生成，请等待完成后再试");
            return emitter;
        }

        try {
            streamExecutor.submit(() -> {
                try {
                    if (restricted) {
                        // 走安全网关而不是裸调用：受限分支同样要进 ai_call_log、同样要过输出过滤。
                        // callText 内部已包含防幻觉提示词、输入消洗与输出过滤，故传原始文本。
                        String reply = aiSafeCallService.callText(
                                "homework_chat", effectiveSystem, userMessage);
                        if (AiAnswerGuard.containsAnswer(reply)) {
                            log.warn("[AI-GUARD] 流式受限分支拦截到答案类回复，已替换。原回复前100字: {}",
                                    reply.length() > 100 ? reply.substring(0, 100) : reply);
                            reply = AiAnswerGuard.BLOCKED_REPLY;
                        }
                        emitter.send(SseEmitter.event().data(streamMapper.writeValueAsString(reply)));
                    } else {
                        aiSafeCallService.callStreamText("chat.stream", effectiveSystem, effectiveUser, delta -> {
                            try {
                                emitter.send(SseEmitter.event().data(streamMapper.writeValueAsString(delta)));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                    emitter.send(SseEmitter.event().data(streamMapper.writeValueAsString("[DONE]")));
                    emitter.complete();
                } catch (Exception e) {
                    try {
                        emitter.send(SseEmitter.event().data(streamMapper.writeValueAsString("[ERROR]")));
                        emitter.complete();
                    } catch (Exception ignored) {
                        emitter.completeWithError(e);
                    }
                } finally {
                    releaseStream(streamUserKey);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            releaseStream(streamUserKey);
            completeStreamWithMessage(emitter, "AI 服务繁忙，请稍后再试");
        }
        return emitter;
    }

    boolean exceedsChatLimits(String systemPrompt, String userMessage) {
        return (systemPrompt != null && systemPrompt.length() > Math.max(1, maxSystemChars))
                || (userMessage != null && userMessage.length() > Math.max(1, maxMessageChars));
    }

    boolean tryAcquireStream(String key) {
        if (key == null) return false;
        AtomicInteger count = activeStreams.computeIfAbsent(key, ignored -> new AtomicInteger());
        int current = count.incrementAndGet();
        if (current <= Math.max(1, maxConcurrentPerUser)) return true;
        if (count.decrementAndGet() == 0) activeStreams.remove(key, count);
        return false;
    }

    void releaseStream(String key) {
        if (key == null) return;
        AtomicInteger count = activeStreams.get(key);
        if (count != null && count.decrementAndGet() <= 0) activeStreams.remove(key, count);
    }

    private void completeStreamWithMessage(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().data(streamMapper.writeValueAsString(message)));
            emitter.send(SseEmitter.event().data(streamMapper.writeValueAsString("[DONE]")));
            emitter.complete();
        } catch (Exception ignored) {
            emitter.complete();
        }
    }

    private String userKey(HttpSession session) {
        if (session == null) return null;
        if (session.getAttribute("studentid") != null) return "student:" + session.getAttribute("studentid");
        if (session.getAttribute("teacherid") != null) return "teacher:" + session.getAttribute("teacherid");
        if (session.getAttribute("adminid") != null) return "admin:" + session.getAttribute("adminid");
        return null;
    }

    /** 解析一个正整数 ID；非法或非正数一律返回 null，由调用方决定是拒绝还是忽略。 */
    Integer parsePositiveInt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            int id = Integer.parseInt(value.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    Integer parseCourseId(String value) {
        return parsePositiveInt(value);
    }

    /** 把 session 属性统一转成 Integer（session 里可能是 Integer，也可能是 String）。 */
    static Integer asInteger(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    boolean hasCourseAccess(Integer courseId, HttpSession session) {
        if (courseId == null || session == null) return false;
        Course course = courseService.getById(courseId);
        if (course == null) return false;
        if (session.getAttribute("adminid") != null) return true;
        if (session.getAttribute("teacherid") instanceof Number teacherId) {
            return course.getTeacherid() != null
                    && course.getTeacherid().intValue() == teacherId.intValue();
        }
        if (session.getAttribute("classid") instanceof Number classId) {
            List<Course> courses = courseService.getByClassId(classId.intValue(), null);
            return courses != null && courses.stream()
                    .anyMatch(item -> item.getCourseid() != null && item.getCourseid().equals(courseId));
        }
        return false;
    }

    private String appendCourseKnowledge(String systemPrompt, Integer courseId, String query) {
        if (courseId == null) return systemPrompt;
        CourseKnowledgeContextService.KnowledgeContext context =
                courseKnowledgeContextService.build(courseId, query);
        if (context.citations().isEmpty()) {
            return systemPrompt + "\n【课程资料检索】本次问题未命中相关课程资料；不要声称答案来自课程资料。";
        }
        return systemPrompt + "\n\n【课程私有知识库】\n" + context.text();
    }

    @PreDestroy
    public void shutdownStreamExecutor() {
        streamExecutor.shutdownNow();
    }

    private boolean isLoggedIn(HttpSession session) {
        return session != null && (session.getAttribute("studentid") != null
                || session.getAttribute("teacherid") != null
                || session.getAttribute("adminid") != null);
    }
}
