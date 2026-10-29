package com.integration;

import com.model.Admin;
import com.service.AdminService;
import com.service.SliderCaptchaService;
import com.service.StudentService;
import com.service.TeacherService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 回归测试：登录成功后，与首个已认证请求并发的其他请求不能被 401 踢出。
 *
 * <p>背景：AuthController 登录时已手动 {@code changeSessionId()} 轮换会话 ID；
 * 若 Spring Security 的会话固定保护（{@code sessionFixation(changeSessionId)}）又在
 * 登录后的首个请求上再次轮换，旧会话 ID 立即失效——前端登录后首页并发发出的请求
 * 若带着旧 ID 到达，就会被 401 拒绝并触发前端登出（表现为「登录成功但进不去」）。</p>
 *
 * <p>用真实内嵌 Tomcat + 真实 HTTP 客户端复现（MockMvc 的 MockHttpSession 不模拟
 * 会话 ID 失效语义，无法复现该竞态）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class SessionFixationRaceTest {

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

    @LocalServerPort
    int port;

    @MockBean
    SliderCaptchaService sliderCaptchaService;
    @MockBean
    AdminService adminService;
    @MockBean
    TeacherService teacherService;
    @MockBean
    StudentService studentService;

    @Test
    void postLoginRequestsKeepTheLoginSessionId() throws Exception {
        when(sliderCaptchaService.consumeToken(any())).thenReturn(true);
        Admin admin = new Admin();
        admin.setAdminid(1);
        admin.setUsername("admin");
        admin.setRealname("管理员");
        when(adminService.login(anyString(), anyString())).thenReturn(admin);

        // 1) 真实登录：CookieManager 模拟浏览器保存会话 Cookie
        CookieManager cookieManager = new CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookieManager).build();

        HttpResponse<String> login = client.send(
                HttpRequest.newBuilder(uri("/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"admin\",\"password\":\"x\",\"captchaToken\":\"mock\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), "登录应成功");
        String sessionIdAfterLogin = jsessionId(cookieManager);
        assertNotNull(sessionIdAfterLogin, "登录响应应下发 JSESSIONID");

        // 2) 登录后的首个已认证请求
        HttpResponse<String> first = client.send(
                HttpRequest.newBuilder(uri("/course/list")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, first.statusCode(), "首个已认证请求应成功");

        // 3) 会话 ID 不应在首个请求上被再次轮换（登录时已轮换过一次）
        assertEquals(sessionIdAfterLogin, jsessionId(cookieManager),
                "首个已认证请求不应再次轮换会话 ID");

        // 4) 携带登录时下发的旧会话 ID 的并发请求必须仍有效（复现 401 竞态）
        HttpClient raw = HttpClient.newHttpClient();
        HttpResponse<String> stale = raw.send(
                HttpRequest.newBuilder(uri("/course/list"))
                        .header("Cookie", "JSESSIONID=" + sessionIdAfterLogin)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, stale.statusCode(), "携带旧会话 ID 的并发请求不应被 401 踢出");
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/ZhiYanAcademy" + path);
    }

    private String jsessionId(CookieManager cm) {
        return cm.getCookieStore().getCookies().stream()
                .filter(c -> "JSESSIONID".equals(c.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
