package com.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class EnterpriseBaselineIntegrationTest {
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

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesSchemaHistoryAndCoreForeignKeys() {
        Integer migrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class);
        Integer progressColumn = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='learning_plan' AND COLUMN_NAME='progress_json'",
                Integer.class);
        Integer foreignKeys = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()",
                Integer.class);
        Integer readableTerms = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM academic_term WHERE term_name LIKE '%学年第%学期'",
                Integer.class);
        Integer readableBadges = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM badge_def " +
                        "WHERE code='FIRST_SUBMIT' AND name='初露锋芒' AND description='完成第一次作业提交'",
                Integer.class);
        assertTrue(migrations != null && migrations >= 2);
        assertTrue(progressColumn != null && progressColumn == 1);
        assertTrue(foreignKeys != null && foreignKeys >= 10);
        assertTrue(readableTerms != null && readableTerms >= 1);
        assertTrue(readableBadges != null && readableBadges == 1);
    }

    @Test
    void anonymousIsRejectedButAdminSessionReachesController() throws Exception {
        mockMvc.perform(get("/course/list"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("role", "admin");
        session.setAttribute("adminid", 1);
        mockMvc.perform(get("/course/list").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void publicCaptchaEndpointIssuesCsrfCookie() throws Exception {
        mockMvc.perform(get("/captcha/slider"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(result -> assertTrue(result.getResponse().getCookie("XSRF-TOKEN") != null));
    }
}
