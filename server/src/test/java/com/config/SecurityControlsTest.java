package com.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.FileService;
import com.service.PasswordService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author yancy
 * @date 2023/7/11
 */
class SecurityControlsTest {

    /**
     * 测试匿名请求是否被拒绝
     * @throws Exception
     */
    @Test
    void anonymousRequestIsRejected() throws Exception {
        SessionAuthInterceptor interceptor = new SessionAuthInterceptor(new ObjectMapper());
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest("GET", "/student/list"),
                response,
                handler("adminOnly"));

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("登录已失效"));
    }

    /**
     * 测试错误角色是否被拒绝，正确角色是否被允许
     * @throws Exception
     */
    @Test
    void wrongRoleIsRejectedAndCorrectRoleIsAllowed() throws Exception {
        SessionAuthInterceptor interceptor = new SessionAuthInterceptor(new ObjectMapper());
        MockHttpServletRequest studentRequest = requestWithRole("student", "studentid", 7);
        MockHttpServletResponse forbidden = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(studentRequest, forbidden, handler("adminOnly")));
        assertEquals(403, forbidden.getStatus());

        MockHttpServletRequest adminRequest = requestWithRole("admin", "adminid", 1);
        assertTrue(interceptor.preHandle(adminRequest, new MockHttpServletResponse(), handler("adminOnly")));
    }

    /**
     * 测试密码服务是否将明文迁移到bcrypt并验证bcrypt
     */
    @Test
    void passwordServiceMigratesPlaintextAndVerifiesBcrypt() {
        PasswordService passwords = new PasswordService();
        assertTrue(passwords.matches("legacy123", "legacy123"));
        assertTrue(passwords.needsMigration("legacy123"));

        String hash = passwords.encode("safe-password");
        assertTrue(hash.startsWith("$2"));
        assertTrue(passwords.matches("safe-password", hash));
        assertFalse(passwords.matches("wrong", hash));
        assertFalse(passwords.needsMigration(hash));
    }

    /**
     * 测试文件服务是否拒绝遍历和绝对路径
     */
    @Test
    void fileServiceRejectsTraversalAndAbsolutePaths() {
        FileService files = new FileService();
        ReflectionTestUtils.setField(files, "uploadDir", "upload/");

        assertNull(files.resolveSafePath("../../config/application-secrets.yml"));
        assertNull(files.resolveSafePath(Path.of(System.getProperty("user.dir")).resolve("secret.txt").toString()));
        Path safe = files.resolveSafePath("2026/07/material.pdf");
        assertNotNull(safe);
        assertTrue(safe.toString().replace('\\', '/').endsWith("/upload/2026/07/material.pdf"));
    }

    /**
     * 测试公共文档验证是否仅使用魔术字节
     */
    @Test
    void publicDocumentValidationUsesMagicBytesInsteadOfExtensionOnly() {
        FileService files = new FileService();
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "proposal.pdf", "application/pdf", "%PDF-1.7\nbody".getBytes());
        MockMultipartFile disguisedExecutable = new MockMultipartFile(
                "file", "malware.pdf", "application/pdf", "MZ-not-a-pdf".getBytes());

        assertTrue(files.isSafePublicDocument(pdf));
        assertFalse(files.isSafePublicDocument(disguisedExecutable));
    }

    /**
     * 测试AI速率限制是否在配置的配额后返回429
     * @throws Exception
     */
    @Test
    void aiRateLimitReturns429AfterConfiguredQuota() throws Exception {
        AiRateLimitInterceptor interceptor = new AiRateLimitInterceptor(new ObjectMapper());
        ReflectionTestUtils.setField(interceptor, "requestsPerMinute", 1);
        MockHttpServletRequest request = requestWithRole("student", "studentid", 9);

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        MockHttpServletResponse limited = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, limited, new Object()));
        assertEquals(429, limited.getStatus());
    }

    /**
     * 创建一个带有角色和ID的模拟请求
     * @param role 角色
     * @param idKey ID密钥
     * @param id ID
     * @return 模拟请求
     */
    private MockHttpServletRequest requestWithRole(String role, String idKey, int id) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("role", role);
        session.setAttribute(idKey, id);
        request.setSession(session);
        return request;
    }

    /**
     * 获取处理程序方法
     * @param methodName 方法名
     * @return 处理程序方法
     * @throws NoSuchMethodException 如果没有找到方法
     */
    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        GuardedController bean = new GuardedController();
        Method method = GuardedController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(bean, method);
    }

    /**
     * 一个受保护的控制器类，用于测试
     */
    private static class GuardedController {
        @RequireRole("admin")
        public void adminOnly() {
        }
    }
}
