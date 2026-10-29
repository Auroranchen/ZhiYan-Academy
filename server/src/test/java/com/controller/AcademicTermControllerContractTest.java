package com.controller;

import com.config.RequireRole;
import com.dto.AcademicTermRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 学期控制器合同测试
 */
class AcademicTermControllerContractTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * 确保所有变更学期的端点需要管理员角色
     */
    @Test
    void allMutationEndpointsRequireAdminRole() throws Exception {
        assertAdmin(AcademicTermController.class.getMethod("create", AcademicTermRequest.class));
        assertAdmin(AcademicTermController.class.getMethod("update", Integer.class, AcademicTermRequest.class));
        assertAdmin(AcademicTermController.class.getMethod("activate", Integer.class));
        assertAdmin(AcademicTermController.class.getMethod("archive", Integer.class));
        assertAdmin(AcademicTermController.class.getMethod("delete", Integer.class));
    }

    /**
     * 确保请求拒绝无效的学期代码和日期范围
     */
    @Test
    void requestRejectsInvalidCodeAndDateRange() {
        AcademicTermRequest invalid = new AcademicTermRequest(
                "2026-A", "", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1));

        Set<String> messages = validator.validate(invalid).stream()
                .map(violation -> violation.getMessage())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(messages.contains("学期编码格式应为 2025-2026-1"));
        assertTrue(messages.contains("学期名称不能为空"));
        assertTrue(messages.contains("结束日期不能早于开始日期"));
    }

    /**
     * 确保请求接受有效的学期
     */
    @Test
    void requestAcceptsValidAcademicTerm() {
        AcademicTermRequest valid = new AcademicTermRequest(
                "2026-2027-1", "2026-2027学年第一学期",
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 31));

        assertTrue(validator.validate(valid).isEmpty());
        assertFalse(valid.termName().isBlank());
    }

    /**
     * 确保方法有角色保护
     */
    private void assertAdmin(Method method) {
        RequireRole guard = method.getAnnotation(RequireRole.class);
        assertNotNull(guard, method + " 缺少角色保护");
        assertEquals(Set.of("admin"), Set.of(guard.value()));
    }
}
