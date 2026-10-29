package com.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 */
@Configuration
@EnableScheduling
public class AsyncConfig {

    /**
     * 流水线**编排**线程池：每个运行中的流水线占用一个线程。
     *
     * <p>必须与下面的 worker 池**分开**。若共用一个池，编排线程会阻塞等待本波次的
     * worker 任务，而 worker 任务又排在这些编排线程后面拿不到线程——多用户并发时会死锁，
     * 且症状是「全部卡住」而非「变慢」，很难从现象反推原因。
     */
    @Bean("agentPipelineOrchestrator")
    public TaskExecutor agentPipelineOrchestrator(AgentPipelineProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int threads = Math.max(1, properties.getOrchestratorThreads());
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("agent-orch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** 流水线**单智能体调用**的 worker 池：宽度需覆盖最宽的一波内容智能体。 */
    @Bean("agentPipelineWorker")
    public TaskExecutor agentPipelineWorker(AgentPipelineProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int threads = Math.max(1, properties.getWorkerThreads());
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("agent-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * AI评分专用线程池
     */
    @Bean("aiGradingExecutor")
    public TaskExecutor aiGradingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-grading-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
