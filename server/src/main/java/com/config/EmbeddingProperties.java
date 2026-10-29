package com.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 向量化配置（{@code app.rag.embedding.*}）。
 *
 * <p>默认走本地哈希向量器：没有任何 Key 也能启动、能检索，
 * 只是语义能力弱——这正是消融实验要量化的东西。
 */
@Component
@ConfigurationProperties(prefix = "app.rag.embedding")
public class EmbeddingProperties {

    /** 向量器：{@code local}（本地哈希）或 {@code zhipu}（智谱 embedding-3）。 */
    private String provider = "local";

    private String apiUrl = "https://open.bigmodel.cn/api/paas/v4/embeddings";

    /** 智谱 API Key。**与 AI_API_KEY 不是同一个**：那个是 DeepSeek 的，DeepSeek 没有 embeddings 接口。 */
    private String apiKey = "";

    private String model = "embedding-3";

    /**
     * 输出维度。embedding-3 支持 Matryoshka 压缩：256 / 512 / 1024 / 2048。
     *
     * <p>默认取 1024 而非官方默认的 2048，是成本决策而非质量决策：
     * 每次检索都要把候选片的向量反序列化一遍，维度直接放大这个开销。
     */
    private int dim = 1024;

    /** 单请求批量上限。官方限制为 64。 */
    private int batchSize = 64;

    /** 可选 HTTP 代理：部分服务器无法直连外部 API。 */
    private String proxyHost = "";
    private int proxyPort = 0;

    private int connectTimeoutMs = 10_000;
    private int readTimeoutMs = 30_000;
    private int maxRetries = 2;

    public boolean zhipuConfigured() {
        return "zhipu".equalsIgnoreCase(provider) && apiKey != null && !apiKey.isBlank();
    }

    public boolean isProxyConfigured() {
        return proxyHost != null && !proxyHost.isBlank() && proxyPort > 0;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getDim() {
        return dim;
    }

    public void setDim(int dim) {
        this.dim = dim;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
