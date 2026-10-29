package com.controller;

import com.config.RequireRole;
import com.dto.CourseCreateRequest;
import com.dto.CourseJoinRequest;
import com.dto.CourseUpdateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseControllerContractTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void mutationEndpointsKeepExplicitRoleGuards() throws Exception {
        assertRoles(CourseController.class.getMethod(
                "add", CourseCreateRequest.class, jakarta.servlet.http.HttpSession.class), "teacher", "admin");
        assertRoles(CourseController.class.getMethod(
                "update", CourseUpdateRequest.class, jakarta.servlet.http.HttpSession.class), "teacher", "admin");
        assertRoles(CourseController.class.getMethod(
                "joinByCode", CourseJoinRequest.class, jakarta.servlet.http.HttpSession.class), "student");
    }

    @Test
    void createRequestRejectsMalformedBusinessInput() {
        CourseCreateRequest request = new CourseCreateRequest(
                " ", -3, 0, null, "2026-spring", List.of(1, -2));

        Set<String> paths = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertTrue(paths.contains("coursename"));
        assertTrue(paths.contains("teacherid"));
        assertTrue(paths.contains("hours"));
        assertTrue(paths.contains("semester"));
        assertTrue(paths.stream().anyMatch(path -> path.startsWith("classIds")));
    }

    @Test
    void updateRequestRequiresPositiveCourseId() {
        CourseUpdateRequest request = new CourseUpdateRequest(
                null, "企业应用开发", 3, 48, "课程描述", "2026-2027-1", List.of(101));

        Set<String> paths = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertEquals(Set.of("courseid"), paths);
    }

    @Test
    void validCourseRequestsPassValidation() {
        CourseCreateRequest create = new CourseCreateRequest(
                "企业应用开发", 3, 48, "Spring Boot 实践", "2026-2027-1", List.of(101, 102));
        CourseJoinRequest join = new CourseJoinRequest("NET2101");

        assertTrue(validator.validate(create).isEmpty());
        assertTrue(validator.validate(join).isEmpty());
    }

    private void assertRoles(Method method, String... roles) {
        RequireRole guard = method.getAnnotation(RequireRole.class);
        assertNotNull(guard, method + " 缺少角色保护");
        assertEquals(Set.of(roles), Set.of(guard.value()));
    }
}
