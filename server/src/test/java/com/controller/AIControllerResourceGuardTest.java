package com.controller;

import com.model.Course;
import com.service.CourseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author hq
 * @date 2023/10/10
 */
class AIControllerResourceGuardTest {

    /**
     * 测试类
     */
    private AIController controller;

    /**
     * 测试前初始化
     */
    @BeforeEach
    void setUp() {
        controller = new AIController();
        ReflectionTestUtils.setField(controller, "maxSystemChars", 20);
        ReflectionTestUtils.setField(controller, "maxMessageChars", 30);
        ReflectionTestUtils.setField(controller, "maxConcurrentPerUser", 2);
    }

    /**
     * 测试后清理
     */
    @AfterEach
    void tearDown() {
        controller.shutdownStreamExecutor();
    }

    /**
     * 测试聊天限制
     */
    @Test
    void rejectsOversizedPromptOrMessage() {
        assertFalse(controller.exceedsChatLimits("短提示", "短问题"));
        assertTrue(controller.exceedsChatLimits("系".repeat(21), "问题"));
        assertTrue(controller.exceedsChatLimits("提示", "问".repeat(31)));
    }

    /**
     * 测试并发流限制
     */
    @Test
    void limitsConcurrentStreamsPerUserAndReleasesCapacity() {
        String user = "student:7";
        assertTrue(controller.tryAcquireStream(user));
        assertTrue(controller.tryAcquireStream(user));
        assertFalse(controller.tryAcquireStream(user));

        controller.releaseStream(user);
        assertTrue(controller.tryAcquireStream(user));
    }

    /**
     * 测试课程知识访问限制
     */
    @Test
    void courseKnowledgeAccessIsBoundToTeacherOrStudentClass() {
        CourseService courses = mock(CourseService.class);
        ReflectionTestUtils.setField(controller, "courseService", courses);
        Course course = new Course();
        course.setCourseid(9);
        course.setTeacherid(3);
        when(courses.getById(9)).thenReturn(course);
        when(courses.getByClassId(5, null)).thenReturn(List.of(course));

        MockHttpSession teacher = new MockHttpSession();
        teacher.setAttribute("teacherid", 3);
        assertTrue(controller.hasCourseAccess(9, teacher));
        teacher.setAttribute("teacherid", 4);
        assertFalse(controller.hasCourseAccess(9, teacher));

        MockHttpSession student = new MockHttpSession();
        student.setAttribute("classid", 5);
        assertTrue(controller.hasCourseAccess(9, student));
        student.setAttribute("classid", 6);
        when(courses.getByClassId(6, null)).thenReturn(List.of());
        assertFalse(controller.hasCourseAccess(9, student));
    }

    /**
     * 测试无效课程ID被忽略
     */
    @Test
    void invalidCourseIdIsIgnored() {
        assertNull(controller.parseCourseId(null));
        assertNull(controller.parseCourseId("abc"));
        assertNull(controller.parseCourseId("-1"));
        assertEquals(12, controller.parseCourseId("12"));
    }
}
