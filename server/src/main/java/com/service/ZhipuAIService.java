package com.service;

import com.ai.AiPrompts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 智谱AI服务
 * 调用智谱AI接口对作业内容进行自动评分和评语生成
 */
@Service
public class ZhipuAIService {

    private static final Logger logger = LoggerFactory.getLogger(ZhipuAIService.class);

    @org.springframework.beans.factory.annotation.Value("${ai.zhipu.api-url:https://api.deepseek.com/chat/completions}")
    private String zhipuApiUrl;

    @org.springframework.beans.factory.annotation.Value("${ai.zhipu.api-key:}")
    private String apiKey;

    @org.springframework.beans.factory.annotation.Value("${ai.zhipu.model:deepseek-v4-pro}")
    private String modelName;

    @org.springframework.beans.factory.annotation.Value("${app.ai-retry.max-retries:2}")
    private int maxRetries;

    @org.springframework.beans.factory.annotation.Value("${app.ai-retry.base-delay-ms:1000}")
    private long retryBaseDelayMs;

    @org.springframework.beans.factory.annotation.Value("${app.ai-retry.max-delay-ms:10000}")
    private long retryMaxDelayMs;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ThreadLocal<CallMetrics> lastCallMetrics = new ThreadLocal<>();

    public record CallMetrics(String model, int promptTokens, int completionTokens, int totalTokens) {}

    static class AIHttpException extends Exception {
        private final int statusCode;
        private final long retryAfterMs;

        AIHttpException(int statusCode, String message, long retryAfterMs) {
            super(message);
            this.statusCode = statusCode;
            this.retryAfterMs = retryAfterMs;
        }

        int statusCode() { return statusCode; }
        long retryAfterMs() { return retryAfterMs; }
    }

    static class RetryableAIException extends Exception {
        RetryableAIException(String message, Throwable cause) { super(message, cause); }
    }

    static class AIConfigurationException extends Exception {
        AIConfigurationException(String message) { super(message); }
    }

    public ZhipuAIService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("AI_API_KEY 未配置：AI 讲解、智能体、学习路径等功能将不可用。"
                    + "请设置环境变量 AI_API_KEY 后重启后端。");
            return;
        }
        String maskedKey = apiKey != null && apiKey.length() > 10
                ? apiKey.substring(0, 8) + "****" + apiKey.substring(apiKey.length() - 4) : "未配置";
        logger.info("智谱AI配置 => api={}, model={}, key={}", zhipuApiUrl, modelName, maskedKey);
    }

    /**
     * AI评分结果类
     */
    public static class GradeResult {
        private BigDecimal score;
        private String comment;
        private double confidence;
        private boolean requiresReview;

        public GradeResult(BigDecimal score, String comment) {
            this(score, comment, 1.0, false);
        }

        public GradeResult(BigDecimal score, String comment, double confidence, boolean requiresReview) {
            this.score = score;
            this.comment = comment;
            this.confidence = confidence;
            this.requiresReview = requiresReview;
        }

        public BigDecimal getScore() {
            return score;
        }

        public String getComment() {
            return comment;
        }

        public double getConfidence() {
            return confidence;
        }

        public boolean isRequiresReview() {
            return requiresReview;
        }
    }

    /**
     * 调用智谱AI对作业内容进行评分
     * 
     * @param homeworkTitle 作业标题
     * @param homeworkRequirement 作业要求
     * @param studentContent 学生提交的内容
     * @return 评分结果（包含分数和评语）
     */
    public GradeResult gradeHomework(String homeworkTitle, String homeworkRequirement, String studentContent) {
        try {
            String systemPrompt = AiPrompts.GRADE_HOMEWORK;

            String userPrompt = String.format(
                    "作业标题：%s\n\n作业要求：%s\n\n学生提交的内容：\n%s\n\n请对以上作业进行评分和点评。",
                    AiAssistContentPolicy.sanitizeModelInput(homeworkTitle != null ? homeworkTitle : "无标题"),
                    AiAssistContentPolicy.sanitizeModelInput(homeworkRequirement != null ? homeworkRequirement : "无具体要求"),
                    AiAssistContentPolicy.sanitizeModelInput(studentContent != null ? studentContent : "无内容")
            );

            String response = callZhipuAIWithRetry(
                    AiAssistContentPolicy.withStudentFacingTone(systemPrompt),
                    userPrompt);
            return parseGradeResult(response);
        } catch (Exception e) {
            logger.error("AI作业评分失败: {}", e.getMessage(), e);
            throw new AIGradingException("AI评分暂时不可用，请教师手动批改", e);
        }
    }

    public static class AIGradingException extends RuntimeException {
        public AIGradingException(String message, Throwable cause) {
            super(message, cause);
        }

        public AIGradingException(String message) {
            super(message);
        }
    }

    /**
     * 生成题目讲解（默认：完整讲解）
     */
    public String explainQuestion(String questionJson) {
        return explainQuestion(questionJson, false);
    }

    /**
     * 生成题目讲解（支持「题库合规」受限模式）
     *
     * @param questionJson 题目信息 JSON（可由上游附加 explainPolicy 等字段）
     * @param restricted     true：作业尚未提交或本题尚无有效作答，启用题库受限策略（不向学生透露可当作业答案抄写的内容）
     * @return 讲解结果 JSON
     */
    public String explainQuestion(String questionJson, boolean restricted) {
        try {
            String systemPrompt = AiAssistContentPolicy.withStudentFacingTone(AiPrompts.explain(restricted));
            String sanitizedPayload = AiAssistContentPolicy.sanitizeModelInput(questionJson);
            String userPrompt = restricted
                    ? "【合规模式 restricted】以下内容仅用于判断是否属于题库习题情境，你只可按受限策略回复，禁止透出本题答案。\n题目信息：" + sanitizedPayload
                    : "题目信息（学生已作答，可作完整答疑）：\n" + sanitizedPayload;

            String response = callZhipuAIWithRetry(systemPrompt, userPrompt);

            // 提取JSON：使用括号匹配找到完整的JSON对象
            String json = extractJson(response);
            if (json != null) {
                try {
                    JsonNode parsed = objectMapper.readTree(json);
                    if (parsed.isObject()) {
                        AiAssistContentPolicy.sanitizeExplainObjectFields((ObjectNode) parsed);
                        return objectMapper.writeValueAsString(parsed);
                    }
                    return json;
                } catch (Exception e) {
                    logger.warn("AI返回的JSON无效，尝试修复: {}", e.getMessage());
                    String repaired = fixBrokenJson(json);
                    if (repaired != null) {
                        try {
                            JsonNode parsed2 = objectMapper.readTree(repaired);
                            if (parsed2.isObject()) {
                                AiAssistContentPolicy.sanitizeExplainObjectFields((ObjectNode) parsed2);
                                return objectMapper.writeValueAsString(parsed2);
                            }
                            return repaired;
                        } catch (Exception ignored) {
                            return repaired;
                        }
                    }
                }
            }

            // 降级：用简单的首尾花括号提取
            int start = response.indexOf("{");
            int end = response.lastIndexOf("}");
            if (start != -1 && end != -1) {
                String sub = response.substring(start, end + 1);
                try {
                    JsonNode parsed = objectMapper.readTree(sub);
                    if (parsed.isObject()) {
                        AiAssistContentPolicy.sanitizeExplainObjectFields((ObjectNode) parsed);
                        return objectMapper.writeValueAsString(parsed);
                    }
                } catch (Exception ignored) { /* noop */ }
                return sub;
            }
            return response;
        } catch (Exception e) {
            logger.error("AI题目讲解失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从AI响应中提取完整的JSON对象（括号匹配）
     */
    private String extractJson(String text) {
        int start = text.indexOf("{");
        if (start == -1) return null;

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 尝试修复损坏的JSON（移除referenceCode字段中的问题内容）
     */
    private String fixBrokenJson(String json) {
        try {
            // 如果referenceCode导致JSON损坏，将其替换为空字符串
            String fixed = json.replaceAll("\"referenceCode\"\\s*:\\s*\"[^\"]*(?:\"[^,}]*)*\"", "\"referenceCode\": \"\"");
            objectMapper.readTree(fixed);
            return fixed;
        } catch (Exception e) {
            return null;
        }
    }



    /**
     * AI连通性诊断：发送简单请求测试API是否可达
     */
    public Map<String, Object> diagnose() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("apiUrl", zhipuApiUrl);
        result.put("model", modelName);
        String maskedKey = apiKey != null && apiKey.length() > 10
                ? apiKey.substring(0, 8) + "****" + apiKey.substring(apiKey.length() - 4) : "未配置";
        result.put("apiKey", maskedKey);

        long t0 = System.currentTimeMillis();
        try {
            String reply = callZhipuAI("回复OK两个字母即可，不要说别的。", "ping");
            long elapsed = System.currentTimeMillis() - t0;
            result.put("status", "OK");
            result.put("reply", reply);
            result.put("elapsedMs", elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - t0;
            result.put("status", "FAIL");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            result.put("elapsedMs", elapsed);
            logger.error("[AI诊断] 连通失败, 耗时={}ms, 错误={}", elapsed, e.getMessage());
        }
        return result;
    }

    /**
     * 调用智谱AI接口
     */
    private String callZhipuAI(String systemPrompt, String userPrompt) throws Exception {
        return callZhipuAI(systemPrompt, userPrompt, null);
    }

    private String callZhipuAI(String systemPrompt, String userPrompt, String modelOverride) throws Exception {
        lastCallMetrics.remove();
        if (apiKey == null || apiKey.isBlank()) {
            throw new AIConfigurationException("AI 服务未配置：请设置环境变量 AI_API_KEY（值应以 sk- 开头）并重启后端");
        }
        if (!apiKey.startsWith("sk-")) {
            throw new AIConfigurationException("AI_API_KEY 格式错误：当前密钥不是 sk- 开头，请检查环境变量后重启后端");
        }
        String effective = effectiveModel(modelOverride);
        long t0 = System.currentTimeMillis();
        String promptPreview = userPrompt != null && userPrompt.length() > 60
                ? userPrompt.substring(0, 60) + "..." : userPrompt;
        logger.info("[AI调用] 开始, model={}, prompt={}", effective, promptPreview);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", effective);
        
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);
        
        java.util.List<Map<String, String>> messages = new java.util.ArrayList<>();
        messages.add(systemMessage);
        messages.add(userMessage);
        requestBody.put("messages", messages);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
                .url(zhipuApiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            long elapsed = System.currentTimeMillis() - t0;
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                String safeBody = errBody == null ? "" : errBody.replaceAll("\\s+", " ");
                if (safeBody.length() > 300) safeBody = safeBody.substring(0, 300);
                logger.error("[AI调用] 失败, code={}, 耗时={}ms, body={}", response.code(), elapsed, safeBody);
                throw new AIHttpException(response.code(),
                        "AI接口调用失败: " + response.code() + " " + safeBody,
                        parseRetryAfterMillis(response.header("Retry-After")));
            }
            
            String responseBody = response.body().string();
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            JsonNode choices = jsonNode.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                logger.error("[AI调用] 响应格式异常, 耗时={}ms, body={}", elapsed, responseBody.length() > 200 ? responseBody.substring(0, 200) : responseBody);
                throw new Exception("AI接口返回格式异常: choices为空");
            }
            String content = choices.get(0).path("message").path("content").asText();
            JsonNode usage = jsonNode.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            int totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);
            // 回落用 effective 而不是 modelName：否则命中路由时，响应缺 model 字段会把
            // 实际是 flash 的调用记成默认模型，ai_call_log 就失去了验证路由的能力
            String responseModel = jsonNode.path("model").asText(effective);
            lastCallMetrics.set(new CallMetrics(responseModel, promptTokens, completionTokens, totalTokens));
            logger.info("[AI调用] 成功, 耗时={}ms, 响应长度={}", elapsed, content != null ? content.length() : 0);
            return content;
        } catch (java.net.SocketTimeoutException e) {
            long elapsed = System.currentTimeMillis() - t0;
            logger.error("[AI调用] 超时, 耗时={}ms, 错误={}", elapsed, e.getMessage());
            throw new RetryableAIException("AI接口超时(" + elapsed + "ms): " + e.getMessage(), e);
        } catch (java.net.UnknownHostException e) {
            logger.error("[AI调用] DNS解析失败, 无法连接到 {}", zhipuApiUrl);
            throw new RetryableAIException("AI接口DNS解析失败，无法连接到: " + zhipuApiUrl, e);
        } catch (javax.net.ssl.SSLException e) {
            logger.error("[AI调用] SSL/TLS错误: {}", e.getMessage());
            throw new Exception("AI接口SSL连接失败: " + e.getMessage());
        } catch (java.net.ConnectException e) {
            logger.error("[AI调用] 连接被拒绝: {}", e.getMessage());
            throw new RetryableAIException("AI接口连接被拒绝: " + e.getMessage(), e);
        }
    }

    public String callPublic(String systemPrompt, String userPrompt) throws Exception {
        return callZhipuAIWithRetry(systemPrompt, userPrompt);
    }

    /**
     * 带模型覆盖的调用。{@code modelOverride} 为空时行为与上面的重载完全一致。
     *
     * <p>由 {@link AiSafeCallService} 依场景传入（见 {@code com.ai.AiModelRouter}）：
     * 流水线场景走快模型，其余场景沿用默认模型。
     */
    public String callPublic(String systemPrompt, String userPrompt, String modelOverride) throws Exception {
        return callZhipuAIWithRetry(systemPrompt, userPrompt, modelOverride);
    }

    /** 生效模型：显式覆盖优先，否则用 {@code ai.zhipu.model} 配置的默认模型。 */
    String effectiveModel(String modelOverride) {
        return modelOverride != null && !modelOverride.isBlank() ? modelOverride.trim() : modelName;
    }

    /** 每个调用线程读取一次最近成功请求的用量，读取后立即清除，避免串到下一次调用。 */
    public CallMetrics consumeLastCallMetrics() {
        CallMetrics metrics = lastCallMetrics.get();
        lastCallMetrics.remove();
        return metrics;
    }

    public String getConfiguredModel() {
        return modelName;
    }

    /**
     * 流式调用（SSE）：逐 token 回调 onDelta，用于前端「真·流式输出」。
     */
    public void callStream(String systemPrompt, String userPrompt,
                           java.util.function.Consumer<String> onDelta) throws Exception {
        callStream(systemPrompt, userPrompt, onDelta, null);
    }

    public void callStream(String systemPrompt, String userPrompt,
                           java.util.function.Consumer<String> onDelta, String modelOverride) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", effectiveModel(modelOverride));
        requestBody.put("stream", true);

        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);
        java.util.List<Map<String, String>> messages = new java.util.ArrayList<>();
        messages.add(systemMessage);
        messages.add(userMessage);
        requestBody.put("messages", messages);

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        Request request = new Request.Builder()
                .url(zhipuApiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                String errBody = response.body() != null ? response.body().string() : "";
                throw new Exception("AI流式接口失败: " + response.code() + " " + errBody);
            }
            okio.BufferedSource source = response.body().source();
            while (!source.exhausted()) {
                String line = source.readUtf8Line();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty() || !line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    if ("[DONE]".equals(data)) break;
                    continue;
                }
                try {
                    JsonNode node = objectMapper.readTree(data);
                    JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                    if (!delta.isMissingNode() && !delta.isNull()) {
                        String c = delta.asText();
                        if (c != null && !c.isEmpty()) onDelta.accept(c);
                    }
                } catch (Exception ignored) { /* 跳过无法解析的分片 */ }
            }
        }
    }

    /**
     * 单次调用，无重试（适用于PPT等批量并发场景，快速失败+降级）
     */
    public String callOnce(String systemPrompt, String userPrompt) throws Exception {
        return callZhipuAI(systemPrompt, userPrompt);
    }

    /** 仅对限流、服务端故障和临时网络错误执行指数退避重试。 */
    private String callZhipuAIWithRetry(String systemPrompt, String userPrompt) throws Exception {
        return callZhipuAIWithRetry(systemPrompt, userPrompt, null);
    }

    private String callZhipuAIWithRetry(String systemPrompt, String userPrompt, String modelOverride)
            throws Exception {
        int retries = Math.max(0, maxRetries);
        Exception lastException = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                return callZhipuAI(systemPrompt, userPrompt, modelOverride);
            } catch (Exception e) {
                lastException = e;
                logger.warn("AI接口调用失败，第{}次尝试，错误: {}", attempt + 1, e.getMessage());
                if (!isRetryable(e) || attempt >= retries) break;
                long delay = retryDelayMillis(e, attempt);
                logger.info("AI临时故障，{}ms 后进行第{}次重试", delay, attempt + 2);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new Exception("AI重试被中断", interrupted);
                }
            }
        }
        throw lastException;
    }

    boolean isRetryable(Exception error) {
        if (error instanceof RetryableAIException) return true;
        if (error instanceof AIHttpException http) {
            int status = http.statusCode();
            return status == 408 || status == 409 || status == 425 || status == 429 || status >= 500;
        }
        return false;
    }

    long retryDelayMillis(Exception error, int attempt) {
        long base = Math.max(1, retryBaseDelayMs);
        long max = Math.max(base, retryMaxDelayMs);
        long exponential = base * (1L << Math.min(Math.max(attempt, 0), 10));
        long retryAfter = error instanceof AIHttpException http ? http.retryAfterMs() : 0;
        return Math.min(max, Math.max(exponential, retryAfter));
    }

    private long parseRetryAfterMillis(String header) {
        if (header == null || header.isBlank()) return 0;
        try {
            return Math.max(0, Long.parseLong(header.trim())) * 1000L;
        } catch (NumberFormatException ignored) {
            try {
                java.time.ZonedDateTime retryAt = java.time.ZonedDateTime.parse(
                        header.trim(), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
                return Math.max(0, java.time.Duration.between(
                        java.time.ZonedDateTime.now(retryAt.getZone()), retryAt).toMillis());
            } catch (Exception invalidDate) {
                return 0;
            }
        }
    }

    /**
     * 解析AI返回的评分结果
     */
    GradeResult parseGradeResult(String response) {
        try {
            // 尝试提取JSON部分
            String jsonStr = response;
            int startIndex = response.indexOf("{");
            int endIndex = response.lastIndexOf("}");
            if (startIndex >= 0 && endIndex > startIndex) {
                jsonStr = response.substring(startIndex, endIndex + 1);
            }
            
            JsonNode jsonNode = objectMapper.readTree(jsonStr);
            if (!jsonNode.has("score") || !jsonNode.get("score").isNumber()) {
                throw new IllegalArgumentException("评分结果缺少数值型 score");
            }
            if (!jsonNode.has("comment") || !jsonNode.get("comment").isTextual()
                    || jsonNode.get("comment").asText().isBlank()) {
                throw new IllegalArgumentException("评分结果缺少有效 comment");
            }
            int score = jsonNode.get("score").asInt();
            String comment = AiAssistContentPolicy.stripTeachingTone(jsonNode.get("comment").asText());
            double confidence = jsonNode.path("confidence").asDouble(0.5);

            // 确保分数在0-100范围内
            score = Math.max(0, Math.min(100, score));
            confidence = Math.max(0, Math.min(1, confidence));

            return new GradeResult(new BigDecimal(score), comment, confidence, confidence < 0.65);
        } catch (Exception e) {
            throw new AIGradingException("AI评分结果格式异常，不能生成可信成绩", e);
        }
    }

    /**
     * 调用智谱AI对简答题进行评分
     * 
     * @param shortAnswerInfo 简答题信息（包含题目和学生答案）
     * @param totalScore 简答题总分
     * @param questionCount 简答题数量
     * @return 评分结果（包含分数和评语）
     */
    public GradeResult gradeShortAnswers(String shortAnswerInfo, int totalScore, int questionCount) {
        try {
            String systemPrompt = AiPrompts.gradeShortAnswers(totalScore, questionCount);

            String userPrompt = "请对以下简答题进行评分：\n\n"
                    + AiAssistContentPolicy.sanitizeModelInput(shortAnswerInfo != null ? shortAnswerInfo : "")
                    + "\n\n请给出总分和评语。";

            String response = callZhipuAIWithRetry(
                    AiAssistContentPolicy.withStudentFacingTone(systemPrompt),
                    userPrompt);
            return parseShortAnswerResult(response, totalScore);
        } catch (Exception e) {
            logger.error("AI简答题评分失败: {}", e.getMessage(), e);
            throw new AIGradingException("AI简答题评分暂时不可用，请教师手动批改", e);
        }
    }

    /**
     * 解析简答题AI返回的评分结果
     */
    GradeResult parseShortAnswerResult(String response, int maxScore) {
        try {
            // 尝试提取JSON部分
            String jsonStr = response;
            int startIndex = response.indexOf("{");
            int endIndex = response.lastIndexOf("}");
            if (startIndex >= 0 && endIndex > startIndex) {
                jsonStr = response.substring(startIndex, endIndex + 1);
            }
            
            JsonNode jsonNode = objectMapper.readTree(jsonStr);
            if (!jsonNode.has("score") || !jsonNode.get("score").isNumber()) {
                throw new IllegalArgumentException("简答题评分缺少数值型 score");
            }
            if (!jsonNode.has("comment") || !jsonNode.get("comment").isTextual()
                    || jsonNode.get("comment").asText().isBlank()) {
                throw new IllegalArgumentException("简答题评分缺少有效 comment");
            }
            int score = jsonNode.get("score").asInt();
            String comment = AiAssistContentPolicy.stripTeachingTone(jsonNode.get("comment").asText());
            double confidence = jsonNode.path("confidence").asDouble(0.5);
            
            // 确保分数在0-maxScore范围内
            score = Math.max(0, Math.min(maxScore, score));
            confidence = Math.max(0, Math.min(1, confidence));

            return new GradeResult(new BigDecimal(score), comment, confidence, confidence < 0.65);
        } catch (Exception e) {
            throw new AIGradingException("AI简答题评分结果格式异常，不能生成可信成绩", e);
        }
    }

    /**
     * AI解析错题，生成简洁解析
     * 
     * @param questionTitle 题目标题
     * @param questionType 题目类型
     * @param options 选项（如果有）
     * @param correctAnswer 正确答案
     * @param studentAnswer 学生答案
     * @return 解析内容
     */
    public String analyzeWrongAnswer(String questionTitle, String questionType, String options, String correctAnswer, String studentAnswer) {
        try {
            String typeLabel = "";
            switch (questionType) {
                case "single": typeLabel = "单选题"; break;
                case "multi": typeLabel = "多选题"; break;
                case "judge": typeLabel = "判断题"; break;
                default: typeLabel = questionType;
            }
            
            String systemPrompt = AiPrompts.ANALYZE_WRONG_ANSWER;

            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("题目：").append(AiAssistContentPolicy.sanitizeModelInput(questionTitle != null ? questionTitle : "")).append("\n");
            if (options != null && !options.isEmpty()) {
                userPrompt.append("选项：").append(AiAssistContentPolicy.sanitizeModelInput(options)).append("\n");
            }
            userPrompt.append("正确答案：").append(AiAssistContentPolicy.sanitizeModelInput(String.valueOf(correctAnswer))).append("\n");
            userPrompt.append("学生答案：").append(AiAssistContentPolicy.sanitizeModelInput(String.valueOf(studentAnswer)));

            String out = callZhipuAI(
                    AiAssistContentPolicy.withStudentFacingTone(systemPrompt),
                    userPrompt.toString());
            return AiAssistContentPolicy.stripTeachingTone(out);
        } catch (Exception e) {
            logger.error("AI错题解析失败: {}", e.getMessage(), e);
            return "AI解析暂时不可用";
        }
    }

    /**
     * 为在线答题生成AI评语
     * 
     * @param totalScore 总分
     * @param correctCount 答对题数
     * @param wrongCount 答错题数
     * @param wrongInfo 错题信息
     * @return AI评语
     */
    public String generateExamComment(int totalScore, int correctCount, int wrongCount, String wrongInfo) {
        try {
            String systemPrompt = AiPrompts.EXAM_COMMENT;

            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("学生得分：").append(totalScore).append("/100分\n");
            userPrompt.append("答对：").append(correctCount).append("题，答错：").append(wrongCount).append("题\n");
            if (wrongInfo != null && !wrongInfo.isEmpty()) {
                userPrompt.append("错题信息：\n").append(AiAssistContentPolicy.sanitizeModelInput(wrongInfo));
            }

            String out = callZhipuAIWithRetry(
                    AiAssistContentPolicy.withStudentFacingTone(systemPrompt),
                    userPrompt.toString());
            return out != null ? AiAssistContentPolicy.stripTeachingTone(out) : null;
        } catch (Exception e) {
            logger.error("AI生成评语失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * AI 自动出题。
     *
     * @param subject    科目或主题；basedOnContent 为 true 时改为传教学内容原文
     * @param difficulty 简单 / 中等 / 困难
     * @return 试题 JSON 字符串；失败时返回带 success:false 的降级结果，由前端提示
     */
    public String generateQuestions(String subject, int questionCount, int singleCount, int multiCount,
                                    int judgeCount, int blankCount, int shortCount, String difficulty) {
        return generateQuestions(subject, questionCount, singleCount, multiCount, judgeCount,
                blankCount, shortCount, difficulty, false);
    }

    /** 严格依据给定教学内容出题，不自行扩展范围 */
    public String generateQuestionsFromContent(String content, int questionCount, int singleCount, int multiCount,
                                               int judgeCount, int blankCount, int shortCount, String difficulty) {
        return generateQuestions(content, questionCount, singleCount, multiCount, judgeCount,
                blankCount, shortCount, difficulty, true);
    }

    /**
     * 两种出题方式的公共实现。原先各写一份，提示词与题型配比拼接几乎完全相同，
     * 改一处必漏一处。
     */
    private String generateQuestions(String subjectOrContent, int questionCount, int singleCount, int multiCount,
                                     int judgeCount, int blankCount, int shortCount, String difficulty,
                                     boolean basedOnContent) {
        try {
            String systemPrompt = AiPrompts.generateQuestions(basedOnContent);
            String safeInput = AiAssistContentPolicy.sanitizeModelInput(
                    subjectOrContent != null ? subjectOrContent : "");

            StringBuilder userPrompt = new StringBuilder();
            if (basedOnContent) {
                userPrompt.append("教学内容：\n").append(safeInput).append("\n\n");
                userPrompt.append("题目数量：").append(questionCount).append("\n");
            } else {
                userPrompt.append("请根据以下要求生成试题：\n\n");
                userPrompt.append("科目/主题：").append(safeInput).append("\n");
            }
            userPrompt.append("难度等级：").append(difficulty != null ? difficulty : "中等").append("\n");
            userPrompt.append("题目要求：\n");

            appendTypeCount(userPrompt, "单选题", singleCount);
            appendTypeCount(userPrompt, "多选题", multiCount);
            appendTypeCount(userPrompt, "判断题", judgeCount);
            appendTypeCount(userPrompt, "填空题", blankCount);
            appendTypeCount(userPrompt, "简答题", shortCount);

            userPrompt.append(basedOnContent
                    ? "\n请严格依据教学内容出题。"
                    : "\n请生成符合要求的试题，确保题目质量高、答案准确。");

            return callZhipuAIWithRetry(
                    AiAssistContentPolicy.withJsonTaskBrief(systemPrompt), userPrompt.toString());
        } catch (Exception e) {
            logger.error("AI 出题失败（basedOnContent={}）: {}", basedOnContent, e.getMessage(), e);
            return "{\"success\": false, \"message\": \"AI出题失败: " + e.getMessage() + "\", \"questions\": []}";
        }
    }

    private void appendTypeCount(StringBuilder target, String label, int count) {
        if (count > 0) target.append("- ").append(label).append("：").append(count).append("道\n");
    }
}
