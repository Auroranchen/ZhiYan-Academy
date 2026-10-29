package com.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 看门狗的 SQL 编排（不依赖真实数据库的那一半）。
 *
 * <p>真实的 SQL 语义由 {@code PipelineRunWatchdogIntegrationTest} 在 Testcontainers
 * 起的 MySQL 上验证；这里守的是**编排**：有没有把查出来的每一条都置为 error，
 * 以及查库失败时会不会把整个定时任务带崩。
 */
class PipelineRunRepositoryWatchdogTest {

    private JdbcTemplate jdbc;
    private PipelineRunRepository repository;

    private void setUp(List<Integer> staleIds) {
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Integer.class), any(), any())).thenReturn(staleIds);
        repository = new PipelineRunRepository(jdbc);
    }

    @Test
    @DisplayName("查出的卡死运行逐个置为 error 并回报 id")
    void marksEveryStaleRunAsError() {
        setUp(List.of(7, 9));

        List<Integer> swept = repository.markStaleRunsAsError(600_000L);

        assertThat(swept).containsExactly(7, 9);
        // 两条都要被改写，否则前端会一直显示「智能体协作中」
        verify(jdbc, times(2)).update(anyString(), eq("error"), isNull(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("查询条件带上 running 与时间阈值，不能把已结束的运行也算进来")
    void onlyRunningRunsBeforeTheCutoffAreConsidered() {
        setUp(List.of());

        repository.markStaleRunsAsError(600_000L);

        verify(jdbc).queryForList(anyString(), eq(Integer.class), eq("running"), any());
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("数据库异常不让定时任务崩掉")
    void databaseFailureIsSwallowed() {
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Integer.class), any(), any()))
                .thenThrow(new RuntimeException("数据库连接不可用"));
        repository = new PipelineRunRepository(jdbc);

        assertThat(repository.markStaleRunsAsError(600_000L)).isEmpty();
    }

    @Test
    @DisplayName("阈值非法时不至于把刚创建的正常运行误判为卡死")
    void nonPositiveTimeoutStillUsesASaneCutoff() {
        setUp(List.of());

        repository.markStaleRunsAsError(0L);

        // 只断言查询发生过：真正的时间语义由集成测试用真实 MySQL 验证
        verify(jdbc).queryForList(anyString(), eq(Integer.class), eq("running"), any());
    }
}
