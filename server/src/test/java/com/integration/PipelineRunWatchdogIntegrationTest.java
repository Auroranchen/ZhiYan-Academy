package com.integration;

import com.repository.PipelineRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 看门狗：把**卡死的运行**从 running 里解救出来。
 *
 * <p>背景：实测出现过一条自 19:25 起 934s 仍是 {@code running} 的运行。
 * 它带来的不只是数字难看——前端会永远显示「智能体协作中」，
 * 「停止生成」按钮也失效，用户只能刷新页面然后发现资源库里什么都没有。
 *
 * <p>之所以能用 {@code update_time} 判定卡死：每完成一步都会回写 {@code steps_json}
 * 并刷新 {@code update_time}（见 {@code PipelineRunRepository.saveSteps}），
 * 所以一条「还活着」的运行其 {@code update_time} 一定在持续推进。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PipelineRunWatchdogIntegrationTest {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long STALE_TIMEOUT_MS = 600_000L;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("hwsc_test")
            .withUsername("hwsc")
            .withPassword("hwsc_test_password")
            .withInitScript("db/test-base-schema.sql");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.profiles.active", () -> "test");
        registry.add("spring.autoconfigure.exclude", () -> String.join(",",
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
                "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"));
    }

    @Autowired
    PipelineRunRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Integer insertRun(int ownerId, String status, LocalDateTime updateTime) {
        String stamp = updateTime.format(TIMESTAMP);
        jdbcTemplate.update("INSERT INTO pipeline_run"
                        + "(owner_type,owner_id,status,steps_json,create_time,update_time) VALUES(?,?,?,?,?,?)",
                "student", ownerId, status, "{}", stamp, stamp);
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM pipeline_run WHERE owner_id=?", Integer.class, ownerId);
    }

    private String statusOf(Integer runId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM pipeline_run WHERE id=?", String.class, runId);
    }

    @Test
    @DisplayName("长时间没有推进的 running 被标记为 error")
    void staleRunningRunIsMarkedAsError() {
        Integer stale = insertRun(990001, "running", LocalDateTime.now().minusMinutes(30));

        List<Integer> swept = repository.markStaleRunsAsError(STALE_TIMEOUT_MS);

        assertThat(swept).as("应当把卡死的运行报告出来，供调用方结束其 SSE 流").contains(stale);
        assertThat(statusOf(stale)).isEqualTo("error");
    }

    @Test
    @DisplayName("刚推进过的运行不受影响——看门狗不能误杀正在跑的流水线")
    void recentlyProgressedRunIsLeftAlone() {
        Integer fresh = insertRun(990002, "running", LocalDateTime.now());

        List<Integer> swept = repository.markStaleRunsAsError(STALE_TIMEOUT_MS);

        assertThat(swept).doesNotContain(fresh);
        assertThat(statusOf(fresh)).isEqualTo("running");
    }

    @Test
    @DisplayName("已结束的运行不会被重复处理")
    void finishedRunsAreIgnored() {
        Integer done = insertRun(990003, "done", LocalDateTime.now().minusMinutes(30));

        List<Integer> swept = repository.markStaleRunsAsError(STALE_TIMEOUT_MS);

        assertThat(swept).doesNotContain(done);
        assertThat(statusOf(done)).as("失败会被改写成 error，成功不该被动").isEqualTo("done");
    }
}
