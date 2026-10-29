package com.service;

import com.mapper.AcademicTermMapper;
import com.mapper.CourseClassMapper;
import com.mapper.CourseMapper;
import com.model.Course;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CourseServiceContractTest {
    private final CourseMapper courseMapper = mock(CourseMapper.class);
    private final CourseClassMapper courseClassMapper = mock(CourseClassMapper.class);
    private final AcademicTermMapper termMapper = mock(AcademicTermMapper.class);
    private final CourseService service = new CourseService(courseMapper, courseClassMapper, termMapper);

    @Test
    void createReportsMissingTeacherAsBadRequest() {
        Course course = validCourse();
        course.setTeacherid(null);

        assertEquals(400, service.add(course, null).getCode());
        verifyNoInteractions(courseMapper, courseClassMapper, termMapper);
    }

    @Test
    void updateReportsMissingCourseAsNotFound() {
        Course course = validCourse();
        course.setCourseid(404);
        when(termMapper.findByCode(course.getSemester())).thenReturn(new com.model.AcademicTerm());
        when(courseMapper.findById(404)).thenReturn(null);

        assertEquals(404, service.update(course, null).getCode());
    }

    @Test
    void deleteReportsDependentHomeworkAsConflict() {
        Course course = validCourse();
        course.setCourseid(8);
        when(courseMapper.findById(8)).thenReturn(course);
        when(courseMapper.countHomework(8)).thenReturn(2);

        assertEquals(409, service.delete(8).getCode());
    }

    @Test
    void joinReportsUnknownCodeAsNotFound() {
        when(courseMapper.findByCode("UNKNOWN")).thenReturn(null);

        assertEquals(404, service.joinByCode("UNKNOWN", 101).getCode());
    }

    private Course validCourse() {
        Course course = new Course();
        course.setCoursename("企业应用开发");
        course.setTeacherid(3);
        course.setHours(48);
        course.setSemester("2026-2027-1");
        return course;
    }
}
