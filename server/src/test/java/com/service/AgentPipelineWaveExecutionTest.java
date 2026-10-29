package com.service;

import com.config.AgentPipelineProperties;
import com.repository.PipelineRunRepository;
import com.response.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 内容智能体是否真的在并行跑，以及交接声明的依赖是否真的被等待。
 *
 * <p>这是本次提速改动的核心断言。只测 {@code HandoffPlanner.waves()} 是不够的：
 * 波次算得再对，执行层若仍按 for 循环逐个调用，端到端时间一点都不会变——
 * 而且症状极隐蔽（功能正常、日志正常，只是慢）。所以这里直接测量**同时进入
 * 智能体的数量**，并核对跨波依赖确实被等待。
 */
class AgentPipelineWaveExecutionTest {

    /** 单个智能体的假耗时，用来制造「同时在场」的窗口。 */
    private static final long AGENT_MILLIS = 200L;

    private LearningAgentService agentService;
    private PipelineRunRepository repository;

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();
    private final Map<String, Long> startedAt = new ConcurrentHashMap<>();
    private final Map<String, Long> finishedAt = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        agentService = mock(LearningAgentService.class);
        repository = mock(PipelineRunRepository.class);
    }

    /** 一次「调用智能体」：记录并发与起止时刻，然后返回成功。 */
    private Result<Map<String, Object>> agentCall(String key) {
        long begin = System.currentTimeMillis();
        startedAt.put(key, begin);
        int now = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(now, Math::max);
        try {
            Thread.sleep(AGENT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        inFlight.decrementAndGet();
        finishedAt.put(key, System.currentTimeMillis());

        Map<String, Object> data = new HashMap<>();
        data.put("agent", key);
        return Result.success(data);
    }

    private void stubAgents(List<Map<String, Object>> handoffs) {
        Map<String, Object> diagnose = new HashMap<>();
        diagnose.put("subject", "数据结构");
        diagnose.put("weakPoints", List.of(Map.of("name", "二叉树遍历")));
        when(agentService.diagnoseStudent(any(), any())).thenReturn(Result.success(diagnose));

        Map<String, Object> coordinate = new HashMap<>();
        coordinate.put("handoffs", handoffs);
        coordinate.put("assignments", List.of());
        when(agentService.coordinate(any(), any(), anyBoolean(), any(), any()))
                .thenReturn(Result.success(coordinate));

        when(agentService.plan(any(), any(), anyBoolean(), any(), any(), any()))
                .thenAnswer(inv -> agentCall("plan"));
        when(agentService.resource(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> agentCall("resource"));
        when(agentService.mindmap(any(), any(), any(), any()))
                .thenAnswer(inv -> agentCall("mindmap"));
        when(agentService.quiz(any(), any(), anyInt(), any(), any()))
                .thenAnswer(inv -> agentCall("quiz"));
        when(agentService.reading(any(), any(), any(), any()))
                .thenAnswer(inv -> agentCall("reading"));
        when(agentService.codecase(any(), any(), any(), any()))
                .thenAnswer(inv -> agentCall("codecase"));
        when(agentService.explainer(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> agentCall("explainer"));

        // 评审直接通过，本测试不涉及返工路径
        Map<String, Object> review = new HashMap<>();
        review.put("approved", true);
        when(agentService.reviewArtifacts(any(), any(), any(), anyBoolean()))
                .thenReturn(Result.success(review));

        Map<String, Object> assembled = new HashMap<>();
        assembled.put("id", 1);
        assembled.put("title", "测试资源包");
        when(agentService.assemble(any(), any(), any(), any(), any()))
                .thenReturn(Result.success(assembled));
    }

    /** 用一个真实的小线程池跑 worker：并发是这里要测的东西，不能用同步执行器替身。 */
    private ExecutorService workerPool() {
        return Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "test-agent-worker");
            t.setDaemon(true);
            return t;
        });
    }

    private void runPipeline(ExecutorService workers) {
        AgentPipelineService pipeline = new AgentPipelineService();
        ReflectionTestUtils.setField(pipeline, "agentService", agentService);
        ReflectionTestUtils.setField(pipeline, "pipelineRunRepository", repository);
        ReflectionTestUtils.setField(pipeline, "workerExecutor", workers);
        // 编排线程用同步执行器：测试里不需要真的再开一个线程
        ReflectionTestUtils.setField(pipeline, "orchestratorExecutor", (Executor) Runnable::run);
        ReflectionTestUtils.setField(pipeline, "properties", new AgentPipelineProperties());
        ReflectionTestUtils.setField(pipeline, "aiCallGate", new Semaphore(16));

        ReflectionTestUtils.invokeMethod(pipeline, "runPipeline",
                (Integer) 1, "student", (Integer) 7, (Integer) null, 3, List.of());
    }

    @Test
    @DisplayName("没有交接时，7 个内容智能体同时在场而不是排队")
    void contentAgentsRunConcurrentlyWithinOneWave() throws Exception {
        stubAgents(List.of());
        ExecutorService workers = workerPool();
        try {
            runPipeline(workers);
        } finally {
            workers.shutdown();
            workers.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(startedAt.keySet()).as("7 个内容智能体都应被调用")
                .containsExactlyInAnyOrder("plan", "resource", "mindmap", "quiz",
                        "reading", "codecase", "explainer");
        // 线程池有 8 个线程、每个智能体睡 200ms，因此 7 个必然同时在场。
        // 若执行层退回 for 循环逐个调用，这个数会变成 1。
        assertThat(maxInFlight.get()).as("应当 7 个同时在场").isEqualTo(7);
    }

    @Test
    @DisplayName("交接声明的依赖真的被等待：下游排到下一波，同波的不必等")
    void declaredHandoffDelaysItsDownstreamToTheNextWave() throws Exception {
        // plan -> resource：resource 被排到第二波，必须等 plan 跑完
        stubAgents(List.of(Map.of("from", "plan", "to", "resource")));
        ExecutorService workers = workerPool();
        try {
            runPipeline(workers);
        } finally {
            workers.shutdown();
            workers.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(startedAt.get("resource"))
                .as("resource 在 plan 之后才开始")
                .isGreaterThanOrEqualTo(finishedAt.get("plan"));
        assertThat(startedAt.get("quiz"))
                .as("quiz 与 plan 同波，不该被 plan 挡住")
                .isLessThan(finishedAt.get("plan"));
    }

    @Test
    @DisplayName("同一波内不存在互相等待：7 个智能体分两波后仍是 6 并发")
    void handoffSplitsTheWaveWithoutSerializingTheRest() throws Exception {
        stubAgents(List.of(Map.of("from", "plan", "to", "resource")));
        ExecutorService workers = workerPool();
        try {
            runPipeline(workers);
        } finally {
            workers.shutdown();
            workers.awaitTermination(10, TimeUnit.SECONDS);
        }

        // 第一波 6 个（除 resource）+ 第二波 1 个（resource）
        Map<String, List<String>> byStart = new LinkedHashMap<>();
        List<String> all = new ArrayList<>(startedAt.keySet());
        assertThat(all).hasSize(7);
        assertThat(maxInFlight.get()).as("第一波的 6 个应当同时在场").isEqualTo(6);
    }
}
