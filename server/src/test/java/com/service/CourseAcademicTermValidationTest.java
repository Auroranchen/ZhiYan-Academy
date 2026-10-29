package com.service;

import com.mapper.AcademicTermMapper;
import com.mapper.CourseClassMapper;
import com.mapper.CourseMapper;
import com.model.AcademicTerm;
import com.model.Course;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseAcademicTermValidationTest {
    private final CourseMapper courseMapper = mock(CourseMapper.class);
    private final CourseClassMapper courseClassMapper = mock(CourseClassMapper.class);
    private final AcademicTermMapper termMapper = mock(AcademicTermMapper.class);
    private final CourseService service = new CourseService(courseMapper, courseClassMapper, termMapper);

    @Test
    void createRejectsUnconfiguredAcademicTerm() {
        Course course = course("2099-2100-1");

        var result = service.add(course, List.of(1));

        assertEquals(400, result.getCode());
        verify(courseMapper, never()).insert(any());
    }

    @Test
    void createAcceptsConfiguredAcademicTerm() {
        Course course = course("2026-2027-1");
        AcademicTerm term = new AcademicTerm();
        term.setTermCode("2026-2027-1");
        when(termMapper.findByCode("2026-2027-1")).thenReturn(term);
        when(courseMapper.insert(course)).thenAnswer(invocation -> {
            course.setCourseid(8);
            return 1;
        });

        var result = service.add(course, List.of());

        assertEquals(200, result.getCode());
        verify(courseMapper).insert(course);
    }

    private Course course(String semester) {
        Course course = new Course();
        course.setCoursename("企业应用开发");
        course.setTeacherid(3);
        course.setSemester(semester);
        course.setHours(48);
        return course;
    }
}
