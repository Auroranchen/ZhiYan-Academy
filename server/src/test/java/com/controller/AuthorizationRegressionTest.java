package com.controller;

import com.config.RequireRole;
import com.dto.CourseCreateRequest;
import com.dto.CourseJoinRequest;
import com.dto.CourseUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.model.Course;
import com.model.CourseResource;
import com.model.Homework;
import com.response.Result;
import com.service.AiInsightService;
import com.service.CourseResourceService;
import com.service.CourseService;
import com.service.HomeworkCommentService;
import com.service.LearningAgentService;
import com.service.PlagiarismService;
import com.service.PeerReviewService;
import com.service.ResourceAccessService;
import com.service.StatisticsService;
import com.service.StudentAttendanceService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


class AuthorizationRegressionTest {


    @Test
    void privilegedMutationEndpointsDeclareRoleGuards() throws Exception {
        assertRoles(NoticeController.class.getMethod("add", com.model.Notice.class), "admin");
        assertRoles(NoticeController.class.getMethod("update", com.model.Notice.class), "admin");
        assertRoles(StudentAttendanceController.class.getMethod(
                "delete", Integer.class, jakarta.servlet.http.HttpSession.class), "teacher", "admin");
        assertRoles(CourseController.class.getMethod(
                "add", CourseCreateRequest.class, jakarta.servlet.http.HttpSession.class), "teacher", "admin");
        assertRoles(com.controller.SecureFileController.class.getMethod("download", String.class),
                "teacher", "admin");
    }


    @Test
    void joinCourseUsesStudentClassFromSession() {
        CourseService courses = mock(CourseService.class);
        CourseController controller = new CourseController(courses);
        when(courses.joinByCode("12345678", 7)).thenReturn(Result.success());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("studentid", 9);
        session.setAttribute("classid", 7);
        controller.joinByCode(new CourseJoinRequest("12345678"), session);

        verify(courses).joinByCode("12345678", 7);
        verify(courses, never()).joinByCode("12345678", 999);
    }


    @Test
    void courseUpdatePreservesServerSideCodeAndTeacherOwnership() {
        CourseService courses = mock(CourseService.class);
        CourseController controller = new CourseController(courses);
        Course existing = new Course();
        existing.setCourseid(4);
        existing.setTeacherid(3);
        existing.setCoursecode("87654321");
        when(courses.getById(4)).thenReturn(existing);
        when(courses.update(any(Course.class), anyList())).thenReturn(Result.success());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("teacherid", 3);
        CourseUpdateRequest request = new CourseUpdateRequest(
                4, "安全课程", 999, 32, null, "2025-2026-2", List.of(5));
        controller.update(request, session);

        var captor = org.mockito.ArgumentCaptor.forClass(Course.class);
        verify(courses).update(captor.capture(), eq(List.of(5)));
        assertEquals(3, captor.getValue().getTeacherid());
        assertEquals("87654321", captor.getValue().getCoursecode());
    }


    @Test
    void studentHomeworkPayloadHasNoCorrectAnswers() throws Exception {
        HomeworkController controller = new HomeworkController();
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());
        Homework homework = new Homework();
        homework.setType(1);
        homework.setContent("{\"questions\":[{\"id\":\"q1\",\"title\":\"题目\",\"answer\":2,\"correctAnswer\":2}]}");

        controller.redactAnswers(homework);

        var root = new ObjectMapper().readTree(homework.getContent());
        assertFalse(root.path("questions").get(0).has("answer"));
        assertFalse(root.path("questions").get(0).has("correctAnswer"));
        assertEquals("题目", root.path("questions").get(0).path("title").asText());
    }

    @Test
    void courseResourceRejectsStudentOutsideCourseClass() {
        CourseResourceService resources = mock(CourseResourceService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CourseResourceController controller = new CourseResourceController();
        ReflectionTestUtils.setField(controller, "courseResourceService", resources);
        ReflectionTestUtils.setField(controller, "jdbcTemplate", jdbc);
        CourseResource resource = new CourseResource();
        resource.setId(8);
        resource.setCourseid(12);
        when(resources.getById(8)).thenReturn(resource);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(12), eq(5))).thenReturn(0);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("studentid", 1);
        session.setAttribute("classid", 5);

        Result<CourseResource> result = controller.getById(8, session);

        assertEquals(403, result.getCode());
    }

    @Test
    void batchAttendanceUsesSessionTeacherId() {
        StudentAttendanceService service = mock(StudentAttendanceService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StudentAttendanceController controller = new StudentAttendanceController();
        ReflectionTestUtils.setField(controller, "studentAttendanceService", service);
        ReflectionTestUtils.setField(controller, "jdbcTemplate", jdbc);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(11), eq(3))).thenReturn(1);
        when(service.batchAdd(eq(11), eq(5), any(), eq(3), anyList())).thenReturn(Result.success());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("teacherid", 3);
        Map<String, Object> body = new HashMap<>();
        body.put("courseid", 11);
        body.put("classid", 5);
        body.put("teacherid", 999);
        body.put("attenddate", "2026-07-18");
        body.put("records", List.of(Map.of("studentid", 7, "status", 1)));

        controller.batchAdd(body, session);

        verify(service).batchAdd(eq(11), eq(5), any(), eq(3), anyList());
    }

    @Test
    void peerReviewTeacherEndpointsDeclareRoleGuards() throws Exception {
        Class<?> session = jakarta.servlet.http.HttpSession.class;
        assertRoles(PeerReviewController.class.getMethod("task", Integer.class, session), "teacher", "admin");
        assertRoles(PeerReviewController.class.getMethod("summary", Integer.class, session), "teacher", "admin");
        assertRoles(PeerReviewController.class.getMethod("close", Integer.class, session), "teacher", "admin");
        assertRoles(PeerReviewController.class.getMethod("apply", Integer.class, session), "teacher", "admin");
        assertRoles(PeerReviewController.class.getMethod("assign", Map.class, session), "teacher", "admin");
    }

    /** 互评汇总含全班学号与均分，此前任何登录用户都能读 */
    @Test
    void peerReviewSummaryRejectsTeacherWithoutHomeworkOwnership() {
        PeerReviewService peer = mock(PeerReviewService.class);
        ResourceAccessService access = mock(ResourceAccessService.class);
        PeerReviewController controller = new PeerReviewController();
        ReflectionTestUtils.setField(controller, "peerReviewService", peer);
        ReflectionTestUtils.setField(controller, "resourceAccess", access);
        when(access.canManageHomework(eq(42), any())).thenReturn(false);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("teacherid", 3);

        Result<List<Map<String, Object>>> result = controller.summary(42, session);

        assertEquals(403, result.getCode());
        verify(peer, never()).summary(anyInt());
    }

    /** 重新分配会清空既有评阅记录，绝不能让非本课教师触发 */
    @Test
    void peerReviewAssignRejectsForeignHomework() {
        PeerReviewService peer = mock(PeerReviewService.class);
        ResourceAccessService access = mock(ResourceAccessService.class);
        PeerReviewController controller = new PeerReviewController();
        ReflectionTestUtils.setField(controller, "peerReviewService", peer);
        ReflectionTestUtils.setField(controller, "resourceAccess", access);
        when(access.canManageHomework(eq(42), any())).thenReturn(false);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("teacherid", 3);

        Result<Map<String, Object>> result = controller.assign(Map.of("homeworkid", 42), session);

        assertEquals(403, result.getCode());
        verify(peer, never()).assign(anyInt(), anyInt(), any(), any(), any());
    }

    /** 作业评论此前只要求登录，不校验是否属于该课程 */
    @Test
    void homeworkCommentsRejectUserOutsideCourse() {
        HomeworkCommentService comments = mock(HomeworkCommentService.class);
        ResourceAccessService access = mock(ResourceAccessService.class);
        HomeworkCommentController controller = new HomeworkCommentController();
        ReflectionTestUtils.setField(controller, "homeworkCommentService", comments);
        ReflectionTestUtils.setField(controller, "resourceAccess", access);
        when(access.canViewHomework(eq(77), any())).thenReturn(false);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("studentid", 1);

        Result<List<com.model.HomeworkComment>> result = controller.getByHomeworkid(77, session);

        assertEquals(403, result.getCode());
        verify(comments, never()).getByHomeworkid(anyInt());
    }

    /** 查重整个控制器此前对所有登录用户开放 */
    @Test
    void plagiarismControllerIsTeacherOnly() {
        RequireRole guard = PlagiarismController.class.getAnnotation(RequireRole.class);
        assertNotNull(guard, "PlagiarismController 缺少角色保护");
        assertEquals(java.util.Set.of("teacher", "admin"), java.util.Set.of(guard.value()));
    }

    @Test
    void plagiarismRejectsHomeworkFromAnotherTeacher() {
        PlagiarismService plagiarism = mock(PlagiarismService.class);
        ResourceAccessService access = mock(ResourceAccessService.class);
        PlagiarismController controller = new PlagiarismController();
        ReflectionTestUtils.setField(controller, "plagiarismService", plagiarism);
        ReflectionTestUtils.setField(controller, "resourceAccess", access);
        when(access.canManageHomework(eq(9), any())).thenReturn(false);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("teacherid", 3);

        assertEquals(403, controller.check(Map.of("homeworkid", 9), session).getCode());
        assertEquals(403, controller.aiDetect(Map.of("homeworkid", 9), session).getCode());
        assertEquals(403, controller.byHomework(9, session).getCode());
        verify(plagiarism, never()).check(anyInt(), anyInt(), any());
        verify(plagiarism, never()).aiDetect(anyInt());
    }

    /** 班级学情周报含学生姓名与偏低名单，只应对本课教师开放 */
    @Test
    void classReportRejectsCourseFromAnotherTeacher() {
        AiInsightService insight = mock(AiInsightService.class);
        ResourceAccessService access = mock(ResourceAccessService.class);
        AiInsightController controller = new AiInsightController();
        ReflectionTestUtils.setField(controller, "aiInsightService", insight);
        ReflectionTestUtils.setField(controller, "resourceAccess", access);
        when(access.canManageCourse(eq(12), any())).thenReturn(false);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("teacherid", 3);

        assertEquals(403, controller.cachedClassReport(12, session).getCode());
        assertEquals(403, controller.classReport(Map.of("courseid", 12), session).getCode());
        verify(insight, never()).getCachedClassReport(anyInt());
        verify(insight, never()).classReport(anyInt());
    }

    /** 班级学情诊断返回全班成绩分布与薄弱学生，此前任何教师都能诊断任意课程 */
    @Test
    void classDiagnosisRejectsCourseFromAnotherTeacher() {
        LearningAgentService agent = mock(LearningAgentService.class);
        ResourceAccessService access = mock(ResourceAccessService.class);
        LearningAgentController controller = new LearningAgentController();
        ReflectionTestUtils.setField(controller, "agentService", agent);
        ReflectionTestUtils.setField(controller, "resourceAccess", access);
        when(access.canManageCourse(eq(12), any())).thenReturn(false);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("teacherid", 3);

        assertEquals(403, controller.diagnoseClass(Map.of("courseid", 12), session).getCode());
        verify(agent, never()).diagnoseClass(anyInt());
    }

    /** 逐题错误率会暴露全班答题分布，学生交卷前拿到等于提前知道答案倾向 */
    @Test
    void homeworkErrorRateIsRestrictedToOwningTeacher() throws Exception {
        assertRoles(StatisticsController.class.getMethod(
                "getHomeworkErrorRate", Integer.class, jakarta.servlet.http.HttpSession.class),
                "teacher", "admin");

        StatisticsService statistics = mock(StatisticsService.class);
        ResourceAccessService access = mock(ResourceAccessService.class);
        StatisticsController controller = new StatisticsController();
        ReflectionTestUtils.setField(controller, "statisticsService", statistics);
        ReflectionTestUtils.setField(controller, "resourceAccess", access);
        when(access.canManageHomework(eq(9), any())).thenReturn(false);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("teacherid", 3);

        assertEquals(403, controller.getHomeworkErrorRate(9, session).getCode());
        verify(statistics, never()).getHomeworkErrorRate(anyInt());
    }

    /** 全校汇总数据只供管理端使用 */
    @Test
    void schoolWideStatisticsAreAdminOnly() throws Exception {
        assertRoles(StatisticsController.class.getMethod("getOverviewStats"), "admin");
        assertRoles(StatisticsController.class.getMethod("getCourseHomeworkStats"), "admin");
    }

    private void assertRoles(Method method, String... expected) {
        RequireRole guard = method.getAnnotation(RequireRole.class);
        assertNotNull(guard, method + " 缺少角色保护");
        assertEquals(java.util.Set.of(expected), java.util.Set.of(guard.value()));
    }
}
