package com.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 归属校验的行为约定。重点是「拿不准就拒绝」：
 * 任何一条判定路径都不允许因为查询异常或身份缺失而意外放行。
 */
class ResourceAccessServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ResourceAccessService access = new ResourceAccessService(jdbc);

    private MockHttpSession sessionOf(String key, Object value) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(key, value);
        return session;
    }

    @Test
    void adminCanManageAnything() {
        MockHttpSession admin = sessionOf("adminid", 1);

        assertTrue(access.canManageHomework(10, admin));
        assertTrue(access.canManageSubmission(10, admin));
        assertTrue(access.canManageCourse(10, admin));
        // 管理员不需要落库查询
        verifyNoInteractions(jdbc);
    }

    @Test
    void teacherOwningTheCourseCanManageItsHomework() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(10), eq(3))).thenReturn(1);

        assertTrue(access.canManageHomework(10, sessionOf("teacherid", 3)));
    }

    @Test
    void teacherFromAnotherCourseIsRejected() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(10), eq(4))).thenReturn(0);

        assertFalse(access.canManageHomework(10, sessionOf("teacherid", 4)));
    }

    @Test
    void studentCannotManageEvenOwnHomework() {
        assertFalse(access.canManageHomework(10, sessionOf("studentid", 7)));
        assertFalse(access.canManageSubmission(10, sessionOf("studentid", 7)));
    }

    @Test
    void submissionOwnerCanViewButPeerReviewerAlsoCan() {
        MockHttpSession student = sessionOf("studentid", 7);
        // 第一条查询判断是否本人提交，第二条判断是否被分配为审阅人
        when(jdbc.queryForObject(contains("FROM studenthomework WHERE"), eq(Integer.class), eq(5), eq(7)))
                .thenReturn(0);
        when(jdbc.queryForObject(contains("FROM peer_review"), eq(Integer.class), eq(5), eq(7)))
                .thenReturn(1);

        assertTrue(access.canViewSubmission(5, student));
    }

    @Test
    void unrelatedStudentCannotViewSubmission() {
        MockHttpSession student = sessionOf("studentid", 7);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(5), eq(7))).thenReturn(0);

        assertFalse(access.canViewSubmission(5, student));
    }

    @Test
    void nullSessionOrIdIsRejected() {
        assertFalse(access.canManageHomework(null, sessionOf("adminid", 1)));
        assertFalse(access.canManageHomework(10, null));
        assertFalse(access.canViewHomework(10, new MockHttpSession()));
        assertFalse(access.isSubmissionOwner(10, new MockHttpSession()));
    }

    @Test
    void databaseFailureDeniesInsteadOfAllowing() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertFalse(access.canManageHomework(10, sessionOf("teacherid", 3)));
        assertFalse(access.canViewCourse(10, sessionOf("studentid", 7)));
        assertFalse(access.canViewSubmission(10, sessionOf("studentid", 7)));
    }
}
