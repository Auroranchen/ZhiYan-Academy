package com.service;

import com.rag.EmbeddingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 向量服务的**单次初始化**。
 *
 * <p>背景：流水线并行化后，7 个内容智能体几乎同时进入 {@code build()}。
 * 而原实现在无同步的情况下写 {@code @Autowired} 字段：
 *
 * <pre>{@code if (embeddingService == null) embeddingService = new LocalEmbeddingService();}</pre>
 *
 * <p>并发下这会让多个线程各自构造一份，并且存在可见性问题——某个线程可能用到
 * 尚未安全发布的实例。串行执行时不会暴露，并行后成为常态。
 *
 * <p>用栅栏把线程排到同一时刻进入初始化：这是让「竞态」在测试里可复现的关键，
 * 否则各线程错开到达，缺陷会随机地时有时无。
 */
class CourseKnowledgeContextEmbeddingInitTest {

    private static final int THREADS = 16;

    @Test
    @DisplayName("并发初始化只产生一个向量服务实例")
    void embeddingServiceIsCreatedExactlyOnce() throws Exception {
        CourseKnowledgeContextService service = new CourseKnowledgeContextService();
        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        Set<EmbeddingService> observed = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS, r -> {
            Thread t = new Thread(r, "embed-init-test");
            t.setDaemon(true);
            return t;
        });

        try {
            for (int i = 0; i < THREADS; i++) {
                pool.execute(() -> {
                    try {
                        barrier.await(5, TimeUnit.SECONDS);
                        observed.add(service.embedding());
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(observed).as("所有线程必须拿到同一个向量服务实例").hasSize(1);
    }
}
