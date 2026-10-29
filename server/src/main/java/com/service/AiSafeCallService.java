package com.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 统一 AI 安全调用网关。
 * 所有面向师生的大模型生成必须经由本服务：
 *  1. system 提示自动追加防幻觉硬约束（AiAssistContentPolicy）
 *  2. user 输入统一做违禁/越狱话术清洗
 *  3. 模型输出统一做敏感内容出口过滤
 *  4. 每次调用记录场景、耗时、成败到 ai_call_log，支撑管理端 AI 监控面板
 */
@Service
public class AiSafeCallService {

    private static final Logger logger = LoggerFactory.getLogger(AiSafeCallService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ZhipuAIService aiService;

    @Autowired
    private com.ai.AiModelRouter modelRouter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 本次调用使用的模型，由场景决定（见 {@link com.ai.AiModelRouter}）。
     *
     * <p>默认模型来自 {@code ai.zhipu.model}，路由只负责在命中规则时覆盖它。
     */
    private String modelFor(String scene) {
        return modelRouter.modelFor(scene, aiService.getConfiguredModel());
    }

    /**
     * 安全文本调用：返回过滤后的模型文本。异常向上抛出，由调用方决定降级策略。
     */
    public String callText(String scene, String system, String user) throws Exception {
        long t0 = System.currentTimeMillis();
        ZhipuAIService.CallMetrics metrics = null;
        try {
            String raw = aiService.callPublic(
                    AiAssistContentPolicy.withAntiHallucination(system),
                    AiAssistContentPolicy.sanitizeModelInput(user),
                    modelFor(scene));
            metrics = aiService.consumeLastCallMetrics();
            String out = AiAssistContentPolicy.filterOutput(raw);
            record(scene, true, System.currentTimeMillis() - t0, null, metrics);
            return out;
        } catch (Exception e) {
            if (metrics == null) metrics = aiService.consumeLastCallMetrics();
            record(scene, false, System.currentTimeMillis() - t0, e.getMessage(), metrics);
            throw e;
        }
    }

    /**
     * 安全 JSON 调用：解析为 Map，解析失败自动重试一次；两次均失败返回 null。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> callJson(String scene, String system, String user) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            long t0 = System.currentTimeMillis();
            ZhipuAIService.CallMetrics metrics = null;
            try {
                String raw = aiService.callPublic(
                        AiAssistContentPolicy.withAntiHallucination(system),
                        AiAssistContentPolicy.sanitizeModelInput(user),
                        modelFor(scene));
                metrics = aiService.consumeLastCallMetrics();
                String json = extractJson(AiAssistContentPolicy.filterOutput(raw));
                JsonNode root = parseLeniently(json, scene);
                Map<String, Object> parsed = objectMapper.convertValue(root, Map.class);
                record(scene, true, System.currentTimeMillis() - t0, null, metrics);
                return parsed;
            } catch (Exception e) {
                if (metrics == null) metrics = aiService.consumeLastCallMetrics();
                record(scene, false, System.currentTimeMillis() - t0, e.getMessage(), metrics);
                logger.warn("[{}] AI JSON调用失败(第{}次): {}", scene, attempt, e.getMessage());
            }
        }
        return null;
    }

    /**
     * 安全 JSON 调用，但**由调用方自己解析**，网关只返回过滤后的原始文本。
     *
     * <p>存在的理由：{@link #callJson} 的契约是「解析成 Map，失败返回 null，两次后放弃」，
     * 而有些调用方需要的是原始字符串（自己带解析器、自己有格式校验与错误话术）。
     * 硬套 callJson 会破坏它们的解析逻辑与错误语义。
     *
     * <p>与 {@link #callText} 一致：追加防幻觉约束、清洗输入、过滤输出、记 ai_call_log，
     * 失败向上抛出。调用方若需要额外的提示词尾部（如 JSON 输出要求），
     * 自行在 {@code system} 里拼好再传进来——网关只叠加防幻觉那一段，不会覆盖。
     */
    public String callJsonRaw(String scene, String system, String user) throws Exception {
        long t0 = System.currentTimeMillis();
        ZhipuAIService.CallMetrics metrics = null;
        try {
            String raw = aiService.callPublic(
                    AiAssistContentPolicy.withAntiHallucination(system),
                    AiAssistContentPolicy.sanitizeModelInput(user),
                    modelFor(scene));
            metrics = aiService.consumeLastCallMetrics();
            String out = AiAssistContentPolicy.filterOutput(raw);
            record(scene, true, System.currentTimeMillis() - t0, null, metrics);
            return out;
        } catch (Exception e) {
            if (metrics == null) metrics = aiService.consumeLastCallMetrics();
            record(scene, false, System.currentTimeMillis() - t0, e.getMessage(), metrics);
            throw e;
        }
    }

    /**
     * 安全流式调用：逐片回调，调用结束后记录到 ai_call_log。
     *
     * <p>补的是一个可观测性空洞——此前流式只走 {@code ZhipuAIService.callStream}，
     * 完全不经过网关，于是「管理端的 AI 监控面板」看不见任何流式调用。
     *
     * <p><b>已知限制：</b>token 数会记成 0。流式响应要到
     * {@code stream_options.include_usage} 才能拿到用量，而当前的 callStream 没有取，
     * 这条留给后续拆分 ZhipuAIService 时一并补上。至少「这次调用发生过」现在是可见的。
     *
     * @param onToken 每个增量片段的回调
     */
    public void callStreamText(String scene, String system, String user,
                               java.util.function.Consumer<String> onToken) throws Exception {
        long t0 = System.currentTimeMillis();
        try {
            aiService.callStream(
                    AiAssistContentPolicy.withAntiHallucination(system),
                    AiAssistContentPolicy.sanitizeModelInput(user),
                    onToken,
                    modelFor(scene));
            record(scene, true, System.currentTimeMillis() - t0, null, null);
        } catch (Exception e) {
            record(scene, false, System.currentTimeMillis() - t0, e.getMessage(), null);
            throw e;
        }
    }

    private void record(String scene, boolean success, long elapsedMs, String error,
                        ZhipuAIService.CallMetrics metrics) {
        try {
            String err = error;
            if (err != null && err.length() > 290) err = err.substring(0, 290);
            String model = metrics != null ? metrics.model() : aiService.getConfiguredModel();
            int promptTokens = metrics != null ? metrics.promptTokens() : 0;
            int completionTokens = metrics != null ? metrics.completionTokens() : 0;
            int totalTokens = metrics != null ? metrics.totalTokens() : 0;
            jdbcTemplate.update(
                "INSERT INTO ai_call_log(scene, success, elapsed_ms, model, prompt_tokens, " +
                        "completion_tokens, total_tokens, error, create_time) VALUES (?,?,?,?,?,?,?,?,?)",
                scene, success ? 1 : 0, (int) Math.min(elapsedMs, Integer.MAX_VALUE), model,
                promptTokens, completionTokens, totalTokens, err,
                LocalDateTime.now().format(TS));
        } catch (Exception e) {
            logger.debug("记录AI调用日志失败: {}", e.getMessage());
        }
    }

    /**
     * 解析模型产出的 JSON：直接解析失败时先做一次**本地修复**再试。
     *
     * <p>为什么要插这一步：解析失败会让 {@link #callJson} 重发整次调用，
     * 而一次 codecase 生成要 70–80 秒。实测有一次 17,033 token 的产物仅仅因为
     * 字符串值里少转义一个引号就被整体丢弃重来——本地修一下是零成本的，
     * 只有修不好的时候才值得付出重发的代价。
     *
     * @throws Exception 修复后仍不可解析时，抛出**原始**解析错误（贴近模型原文，便于排查）
     */
    private JsonNode parseLeniently(String json, String scene) throws Exception {
        try {
            return objectMapper.readTree(json);
        } catch (Exception first) {
            try {
                JsonNode repaired = objectMapper.readTree(com.ai.JsonRepair.repair(json));
                logger.info("[{}] JSON 本地修复成功，避免了一次重发调用", scene);
                return repaired;
            } catch (Exception unrepairable) {
                logger.debug("[{}] JSON 本地修复无效: {}", scene, unrepairable.getMessage());
                throw first;
            }
        }
    }

    private String extractJson(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl >= 0) s = s.substring(nl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
            s = s.trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) return s.substring(start, end + 1);
        return s;
    }
}
