package com.config;

import com.rag.EmbeddingService;
import com.service.LocalEmbeddingService;
import com.service.ZhipuEmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 向量器装配：二选一，容器里始终只有一个 {@link EmbeddingService}。
 *
 * <p><b>为什么缺 Key 时静默回落到本地向量器而不是启动失败：</b>
 * 课程资料检索是核心链路，不该因为一个外部 Key 没配就整个起不来。
 * 回落时会打 warn 并把当前向量器标识打出来——因为**这两种向量器的检索质量差很多**，
 * 悄悄换了实现却没人知道，会让人对着变差的结果找不到原因。
 */
@Configuration
public class EmbeddingConfig {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingConfig.class);

    @Bean
    public EmbeddingService embeddingService(EmbeddingProperties props, RestClient.Builder builder) {
        if (props.zhipuConfigured()) {
            logger.info("向量器 = 智谱 {}（维度 {}，批量 {}）",
                    props.getModel(), props.getDim(), props.getBatchSize());
            return new ZhipuEmbeddingClient(props, builder);
        }
        if ("zhipu".equalsIgnoreCase(props.getProvider())) {
            logger.warn("app.rag.embedding.provider=zhipu 但未配置 API Key，"
                    + "回落到本地哈希向量器（语义能力显著较弱）。"
                    + "请设置 ZHIPU_API_KEY 或 config/application-secrets.yml。");
        } else {
            logger.info("向量器 = 本地哈希（provider={}）", props.getProvider());
        }
        return new LocalEmbeddingService();
    }
}
