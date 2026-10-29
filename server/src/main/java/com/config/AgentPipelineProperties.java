package com.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 多智能体流水线的并行调度参数（{@code app.agent-pipeline.*}）。
 *
 * <p>这些默认值来自实测：一次流水线在 {@code deepseek-flash} 上串行约 91.7s，
 * 其中内容段（7 个智能体）占 42.9s。按依赖分波并行后该段降到 10.3s，
 * 端到端进入 1 分钟。参数留成配置是为了现场演示时能对着实时数据调整，
 * 而不用改代码重新打包。
 */
@Component
@ConfigurationProperties(prefix = "app.agent-pipeline")
public class AgentPipelineProperties {

    /**
     * 内容段最多分几波执行。
     *
     * <p>压得越平越快，代价是**深度超过本值的交接不再等待上游**，
     * 上游产物也就注入不了下游提示词（如实记为 {@code injected:false}）。
     * 1 = 全部并行；设得很大则等价于严格按依赖串行。
     */
    private int maxWaves = 2;

    /** 执行单个智能体调用的 worker 线程数，需覆盖最宽的一波。 */
    private int workerThreads = 8;

    /** 同时进行的流水线数量上限（每个 run 占用一个编排线程）。 */
    private int orchestratorThreads = 4;

    /** 跨所有 run 的同时大模型调用上限，用于保护上游 API 与成本。 */
    private int maxConcurrentAiCalls = 8;

    /** 超过这个时长仍处于 running 的运行会被判定为卡死并标记为 error。 */
    private long staleTimeoutMs = 600_000L;

    /** 看门狗扫描间隔。 */
    private long watchdogIntervalMs = 60_000L;

    public int getMaxWaves() {
        return maxWaves;
    }

    public void setMaxWaves(int maxWaves) {
        this.maxWaves = maxWaves;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public int getOrchestratorThreads() {
        return orchestratorThreads;
    }

    public void setOrchestratorThreads(int orchestratorThreads) {
        this.orchestratorThreads = orchestratorThreads;
    }

    public int getMaxConcurrentAiCalls() {
        return maxConcurrentAiCalls;
    }

    public void setMaxConcurrentAiCalls(int maxConcurrentAiCalls) {
        this.maxConcurrentAiCalls = maxConcurrentAiCalls;
    }

    public long getStaleTimeoutMs() {
        return staleTimeoutMs;
    }

    public void setStaleTimeoutMs(long staleTimeoutMs) {
        this.staleTimeoutMs = staleTimeoutMs;
    }

    public long getWatchdogIntervalMs() {
        return watchdogIntervalMs;
    }

    public void setWatchdogIntervalMs(long watchdogIntervalMs) {
        this.watchdogIntervalMs = watchdogIntervalMs;
    }
}
