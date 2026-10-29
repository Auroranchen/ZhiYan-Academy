package com.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * OpenAI 兼容协议的评测客户端（JDK HttpClient，不依赖 Spring 或 OkHttp）。
 *
 * <p>配置全部走环境变量，与生产配置解耦——评测要能换模型、换端点而不动应用配置：
 * <pre>
 *   AI_API_KEY / AI_API_URL / AI_MODEL        生成模型（默认 DeepSeek）
 *   JUDGE_API_KEY / JUDGE_API_URL / JUDGE_MODEL  评判模型（默认智谱 GLM）
 * </pre>
 *
 * <p><b>为什么生成与评判要用不同模型：</b>同一个模型给自己的输出打分存在自偏好偏差，
 * 分数会系统性偏高。跨模型评判是这一层最基本的严谨性要求。
 */
public class HttpChatModel implements ChatModel {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final HttpClient http;

    public HttpChatModel(String endpoint, String apiKey, String model, double temperature) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    /** 生成模型：默认沿用生产同款 DeepSeek。 */
    public static HttpChatModel generator() {
        return new HttpChatModel(
                env("AI_API_URL", "https://api.deepseek.com/chat/completions"),
                env("AI_API_KEY", ""),
                env("AI_MODEL", "deepseek-chat"),
                0.3);
    }

    /** 评判模型：默认智谱 GLM，与生成模型跨厂商。 */
    public static HttpChatModel judge() {
        return new HttpChatModel(
                env("JUDGE_API_URL", "https://open.bigmodel.cn/api/paas/v4/chat/completions"),
                env("JUDGE_API_KEY", ""),
                env("JUDGE_MODEL", "glm-4-flash"),
                0.0);
    }

    @Override
    public String name() {
        return model;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public Reply chat(String system, String user) throws IOException, InterruptedException {
        if (!configured()) {
            throw new IOException("未配置 API Key，模型 " + model + " 不可用");
        }
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("temperature", temperature);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        long started = System.currentTimeMillis();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - started;

        if (response.statusCode() / 100 != 2) {
            throw new IOException("模型 " + model + " 返回 HTTP " + response.statusCode()
                    + ": " + abbreviate(response.body()));
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IOException("模型响应缺少 choices: " + abbreviate(response.body()));
        }
        String content = choices.get(0).path("message").path("content").asText("");
        JsonNode usage = root.path("usage");
        return new Reply(content,
                usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0),
                elapsed);
    }

    private static String abbreviate(String text) {
        if (text == null) return "";
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
