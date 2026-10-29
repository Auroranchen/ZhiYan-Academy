package com.service;

import com.config.EmbeddingProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rag.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 智谱 embedding-3 向量客户端（OpenAI 兼容协议）。
 *
 * <p><b>为什么另起一个客户端而不是扩展 {@code ZhipuAIService}：</b>
 * 那个类的 HTTP 方法是 chat 专用的私有方法，且它配置的 Key 是 DeepSeek 的——
 * DeepSeek 根本没有 embeddings 接口。两者的端点、请求体、鉴权来源都不一样。
 *
 * <p>结构照抄 {@code WeChatMiniProgramClient}：构造注入 {@code RestClient}、
 * 配置走 {@code @ConfigurationProperties}、**显式代理支持**（本项目的服务器访问外部 API
 * 需要走代理，见 application.yml 里那条说明——不加这个在会场网络会直接挂）。
 */
public class ZhipuEmbeddingClient implements EmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(ZhipuEmbeddingClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EmbeddingProperties props;
    private final RestClient restClient;

    public ZhipuEmbeddingClient(EmbeddingProperties props, RestClient.Builder builder) {
        this.props = props;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setReadTimeout(props.getReadTimeoutMs());
        if (props.isProxyConfigured()) {
            factory.setProxy(new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(props.getProxyHost(), props.getProxyPort())));
            logger.info("向量客户端走代理 {}:{}", props.getProxyHost(), props.getProxyPort());
        }
        this.restClient = builder.requestFactory(factory).build();
    }

    @Override
    public int dimensions() {
        return props.getDim();
    }

    @Override
    public String modelId() {
        return props.getModel() + "-" + props.getDim();
    }

    @Override
    public double[] embed(String text) {
        List<double[]> vectors = embedBatch(List.of(text == null ? "" : text));
        return vectors.isEmpty() ? new double[0] : vectors.get(0);
    }

    /**
     * 批量向量化——**这是本类存在的主要理由**。
     *
     * <p>一份课程最多 12 份资料 × 80 切片 = 960 块。逐个远程调用就是 960 次串行 HTTP，
     * 而且全在一个事务里，必然超时回滚，留下「索引了一半但失效判断认为它是好的」的课程。
     * 按 64 条一批只需 15 次请求。
     */
    @Override
    public List<double[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();

        List<double[]> out = new ArrayList<>(texts.size());
        int batch = Math.max(1, Math.min(props.getBatchSize(), 64));
        for (int from = 0; from < texts.size(); from += batch) {
            int to = Math.min(texts.size(), from + batch);
            out.addAll(requestBatch(texts.subList(from, to)));
        }
        return out;
    }

    private List<double[]> requestBatch(List<String> batch) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", props.getModel());
        ArrayNode input = body.putArray("input");
        batch.forEach(input::add);
        // 只有 embedding-3 支持 dimensions，旧模型传了会报错
        if (props.getModel().startsWith("embedding-3")) {
            body.put("dimensions", props.getDim());
        }

        // 请求体在重试之间不变，序列化一次即可
        final String payload;
        try {
            payload = MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("向量请求体序列化失败: " + e.getMessage(), e);
        }

        Exception last = null;
        for (int attempt = 0; attempt <= Math.max(0, props.getMaxRetries()); attempt++) {
            if (attempt > 0) {
                sleepQuietly(1000L << (attempt - 1));
            }
            try {
                String raw = restClient.post()
                        .uri(props.getApiUrl())
                        .header("Authorization", "Bearer " + props.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (request, response) -> {
                            String text = new String(response.getBody().readAllBytes());
                            throw new IllegalStateException(
                                    "HTTP " + response.getStatusCode().value() + ": " + abbreviate(text));
                        })
                        .body(String.class);
                return parseVectors(raw, batch.size());
            } catch (RestClientException | IllegalStateException e) {
                last = e;
                logger.warn("向量化失败（第 {} 次尝试）: {}", attempt + 1, e.getMessage());
            }
        }
        throw new IllegalStateException("向量化连续失败: "
                + (last == null ? "未知原因" : last.getMessage()), last);
    }

    /** 解析 OpenAI 兼容响应：{@code data[].embedding}，并按 index 还原顺序。 */
    List<double[]> parseVectors(String raw, int expected) {
        try {
            JsonNode root = MAPPER.readTree(raw);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                throw new IllegalStateException("响应缺少 data 数组: " + abbreviate(raw));
            }
            List<JsonNode> items = new ArrayList<>();
            data.forEach(items::add);
            // 协议保证 index 对应输入顺序，但显式排序更稳——顺序错了会把向量配到错误的切片上
            items.sort(Comparator.comparingInt(node -> node.path("index").asInt(0)));

            List<double[]> vectors = new ArrayList<>(items.size());
            for (JsonNode item : items) {
                List<Double> values = MAPPER.convertValue(
                        item.path("embedding"), new TypeReference<List<Double>>() {});
                vectors.add(toNormalizedArray(values));
            }
            if (vectors.size() != expected) {
                throw new IllegalStateException(
                        "返回向量数 " + vectors.size() + " 与请求数 " + expected + " 不符");
            }
            return vectors;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("向量响应解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 转数组并做 L2 归一化。
     *
     * <p>归一化是必需的：检索用的是点积当余弦相似度，前提就是两边都已归一化。
     * 官方返回的向量通常是归一化的，但依赖这一点太脆——自己再归一次成本可以忽略。
     */
    static double[] toNormalizedArray(List<Double> values) {
        double[] vector = new double[values.size()];
        double norm = 0;
        for (int i = 0; i < vector.length; i++) {
            vector[i] = values.get(i);
            norm += vector[i] * vector[i];
        }
        if (norm > 0) {
            double len = Math.sqrt(norm);
            for (int i = 0; i < vector.length; i++) vector[i] /= len;
        }
        return vector;
    }

    @Override
    public String toJson(double[] vector) {
        try {
            return MAPPER.writeValueAsString(vector);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Override
    public double[] fromJson(String json) {
        try {
            List<Double> values = MAPPER.readValue(json, new TypeReference<List<Double>>() {});
            double[] result = new double[values.size()];
            for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
            return result;
        } catch (Exception e) {
            return new double[0];
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String abbreviate(String text) {
        if (text == null) return "";
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
