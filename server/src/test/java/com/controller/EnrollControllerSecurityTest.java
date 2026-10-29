package com.controller;

import com.dto.EnrollApplicationRequest;
import com.response.Result;
import com.service.EnrollService;
import com.service.FileService;
import com.service.LoginSecurityService;
import com.service.PublicRequestRateLimitService;
import com.service.SliderCaptchaService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class EnrollControllerSecurityTest {
    private final EnrollService enrollService = mock(EnrollService.class);
    private final FileService fileService = mock(FileService.class);
    private final SliderCaptchaService captchaService = mock(SliderCaptchaService.class);
    private final PublicRequestRateLimitService rateLimitService = mock(PublicRequestRateLimitService.class);
    private final LoginSecurityService loginSecurityService = mock(LoginSecurityService.class);
    private final EnrollController controller = new EnrollController(
            enrollService, fileService, captchaService, rateLimitService, loginSecurityService);

    @Test
    void publicUploadRequiresCaptchaBeforeSaving() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockMultipartFile file = new MockMultipartFile(
                "file", "proposal.pdf", "application/pdf", "%PDF-test".getBytes());
        when(loginSecurityService.resolveIp(request)).thenReturn("127.0.0.1");
        when(rateLimitService.allowUpload("127.0.0.1")).thenReturn(true);
        when(captchaService.isTokenValid("expired")).thenReturn(false);

        Result<?> result = controller.upload(file, "expired", request);

        assertEquals(412, result.getCode());
        verify(fileService, never()).saveFile(file);
    }

    @Test
    void applicationConsumesOneTimeCaptchaToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        EnrollApplicationRequest body = new EnrollApplicationRequest(
                "示例大学", "张老师", "教务处", "13800138000",
                "预约演示", null, null, "captcha-token");
        when(loginSecurityService.resolveIp(request)).thenReturn("127.0.0.1");
        when(rateLimitService.allowApply("127.0.0.1")).thenReturn(true);
        when(captchaService.consumeToken("captcha-token")).thenReturn(true);
        when(enrollService.apply(anyString(), anyString(), anyString(), anyString(),
                anyString(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(Result.success());

        Result<Void> result = controller.apply(body, request);

        assertEquals(200, result.getCode());
        verify(captchaService).consumeToken("captcha-token");
    }

    @Test
    void updateStatusRejectsWhenAdminNotLoggedIn() {
        MockHttpSession session = new MockHttpSession();
        Result<Void> result = controller.updateStatus(1, Map.of("status", 1), session);
        assertEquals(401, result.getCode());
        verify(enrollService, never()).updateStatus(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void updateStatusAllowsAdmin() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminid", 1);
        when(enrollService.updateStatus(1502, 1)).thenReturn(Result.success());

        Result<Void> result = controller.updateStatus(1502, Map.of("status", 1), session);

        assertEquals(200, result.getCode());
        verify(enrollService).updateStatus(1502, 1);
    }
}
