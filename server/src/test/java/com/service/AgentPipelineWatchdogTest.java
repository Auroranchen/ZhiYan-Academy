package com.service;

import com.config.AgentPipelineProperties;
import com.repository.PipelineRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 看门狗的善后动作。
 *
 * <p>只把数据库状态改成 error 是不够的：那条运行还留着取消标志、还可能挂着 SSE 连接。
 * 前端正是靠 SSE 流才知道「结束了」——不关流，页面会一直转圈到用户手动刷新。
 */
class AgentPipelineWatchdogTest {

    @Test
    @DisplayName("被判定卡死的运行要同时清掉取消标志")
    void sweptRunsHaveTheirCancelFlagsCleared() {
        PipelineRunRepository repository = mock(PipelineRunRepository.class);
        when(repository.markStaleRunsAsError(600_000L)).thenReturn(List.of(5));

        AgentPipelineService pipeline = new AgentPipelineService();
        ReflectionTestUtils.setField(pipeline, "pipelineRunRepository", repository);
        ReflectionTestUtils.setField(pipeline, "properties", new AgentPipelineProperties());

        @SuppressWarnings("unchecked")
        Map<Integer, AtomicBoolean> flags = (Map<Integer, AtomicBoolean>)
                ReflectionTestUtils.getField(pipeline, "cancelFlags");
        assertThat(flags).isNotNull();
        flags.put(5, new AtomicBoolean(false));

        pipeline.sweepStaleRuns();

        verify(repository).markStaleRunsAsError(600_000L);
        assertThat(flags).as("卡死的运行不该在内存里留下永久条目").doesNotContainKey(5);
    }

    @Test
    @DisplayName("没有卡死运行时什么也不做，不影响正常流水线")
    void nothingHappensWhenNothingIsStale() {
        PipelineRunRepository repository = mock(PipelineRunRepository.class);
        when(repository.markStaleRunsAsError(600_000L)).thenReturn(List.of());

        AgentPipelineService pipeline = new AgentPipelineService();
        ReflectionTestUtils.setField(pipeline, "pipelineRunRepository", repository);
        ReflectionTestUtils.setField(pipeline, "properties", new AgentPipelineProperties());

        @SuppressWarnings("unchecked")
        Map<Integer, AtomicBoolean> flags = (Map<Integer, AtomicBoolean>)
                ReflectionTestUtils.getField(pipeline, "cancelFlags");
        assertThat(flags).isNotNull();
        Map<Integer, AtomicBoolean> snapshot = new ConcurrentHashMap<>(flags);

        pipeline.sweepStaleRuns();

        assertThat(flags).isEqualTo(snapshot);
    }
}
