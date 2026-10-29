package com.controller;

import com.config.RequireRole;
import com.dto.CourseCreateRequest;
import com.dto.CourseJoinRequest;
import com.dto.CourseUpdateRequest;
import com.model.Course;
import com.response.Result;
import com.service.CourseService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/course")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/list")
    @RequireRole("admin")
    public Result<List<Course>> list(@RequestParam(required = false) String keyword,
                                     @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于1") Integer page,
                                     @RequestParam(defaultValue = "10")
                                     @Min(value = 1, message = "每页数量不能小于1")
                                     @Max(value = 200, message = "每页数量不能超过200") Integer pageSize) {
        return Result.success(courseService.getList(keyword, page, pageSize), courseService.getCount(keyword));
    }

    @GetMapping("/all")
    @RequireRole("admin")
    public Result<List<Course>> all() {
        return Result.success(courseService.getAll());
    }

    @GetMapping("/{id}")
    public Result<Course> getById(@PathVariable("id") Integer id, HttpSession session) {
        Course course = courseService.getById(id);
        if (course == null) return Result.error(404, "课程不存在");
        if (!canView(course, session)) return Result.error(403, "无权查看该课程");
        return Result.success(course);
    }

    @GetMapping("/byTeacher/{id}")
    public Result<List<Course>> getByTeacherId(@PathVariable("id") Integer id,
                                               @RequestParam(required = false) String semester,
                                               HttpSession session) {
        Integer teacherId = session == null ? null : (Integer) session.getAttribute("teacherid");
        boolean admin = session != null && session.getAttribute("adminid") != null;
        if (!admin && (teacherId == null || !teacherId.equals(id))) {
            return Result.error(403, "无权查看其他教师的课程");
        }
        return Result.success(courseService.getByTeacherId(id, semester));
    }

    @GetMapping("/byClass/{id}")
    public Result<List<Course>> getByClassId(@PathVariable("id") Integer id,
                                             @RequestParam(required = false) String semester,
                                             HttpSession session) {
        boolean admin = session != null && session.getAttribute("adminid") != null;
        Integer studentClassId = session == null ? null : (Integer) session.getAttribute("classid");
        Integer teacherId = session == null ? null : (Integer) session.getAttribute("teacherid");
        if (!admin && studentClassId != null && !studentClassId.equals(id)) {
            return Result.error(403, "无权查看其他班级的课程");
        }
        if (!admin && studentClassId == null && teacherId == null) {
            return Result.error(403, "无权查看该班级课程");
        }
        List<Course> list = courseService.getByClassId(id, semester);
        if (!admin && teacherId != null && list != null) {
            list = list.stream().filter(course -> teacherId.equals(course.getTeacherid())).toList();
        }
        return Result.success(list);
    }

    @PostMapping("/add")
    @RequireRole({"teacher", "admin"})
    public Result<Course> add(@Valid @RequestBody CourseCreateRequest request, HttpSession session) {
        Integer sessionTeacherId = session == null ? null : (Integer) session.getAttribute("teacherid");
        Integer teacherId = sessionTeacherId != null ? sessionTeacherId : request.teacherid();
        if (teacherId == null) return Result.error(400, "任课教师不能为空");

        Course course = new Course();
        course.setCoursename(request.coursename());
        course.setTeacherid(teacherId);
        course.setHours(request.hours());
        course.setDescription(request.description());
        course.setSemester(request.semester());
        return courseService.add(course, request.classIds());
    }

    @PostMapping("/joinByCode")
    @RequireRole("student")
    public Result<Void> joinByCode(@Valid @RequestBody CourseJoinRequest request, HttpSession session) {
        return courseService.joinByCode(request.coursecode(), (Integer) session.getAttribute("classid"));
    }

    @PutMapping("/update")
    @RequireRole({"teacher", "admin"})
    public Result<Void> update(@Valid @RequestBody CourseUpdateRequest request, HttpSession session) {
        Integer courseId = request.courseid();
        Course existing = courseService.getById(courseId);
        if (existing == null) return Result.error(404, "课程不存在");
        if (!canManage(existing, session)) return Result.error(403, "无权修改该课程");

        Course course = new Course();
        course.setCourseid(courseId);
        course.setCoursename(request.coursename());
        Integer sessionTeacherId = session == null ? null : (Integer) session.getAttribute("teacherid");
        Integer teacherId = sessionTeacherId != null ? sessionTeacherId : request.teacherid();
        if (teacherId == null) return Result.error(400, "任课教师不能为空");
        course.setTeacherid(teacherId);
        course.setHours(request.hours());
        course.setDescription(request.description());
        course.setSemester(request.semester());
        course.setCoursecode(existing.getCoursecode());
        return courseService.update(course, request.classIds());
    }

    @DeleteMapping("/delete/{id}")
    @RequireRole({"teacher", "admin"})
    public Result<Void> delete(@PathVariable("id") Integer id, HttpSession session) {
        Course existing = courseService.getById(id);
        if (existing == null) return Result.error(404, "课程不存在");
        if (!canManage(existing, session)) return Result.error(403, "无权删除该课程");
        return courseService.delete(id);
    }

    private boolean canView(Course course, HttpSession session) {
        if (course == null || session == null) return false;
        if (session.getAttribute("adminid") != null) return true;
        Integer teacherId = (Integer) session.getAttribute("teacherid");
        if (teacherId != null) return teacherId.equals(course.getTeacherid());
        Integer classId = (Integer) session.getAttribute("classid");
        if (classId == null) return false;
        List<Course> courses = courseService.getByClassId(classId, null);
        return courses != null && courses.stream()
                .anyMatch(item -> course.getCourseid().equals(item.getCourseid()));
    }

    private boolean canManage(Course course, HttpSession session) {
        if (course == null || session == null) return false;
        if (session.getAttribute("adminid") != null) return true;
        Integer teacherId = (Integer) session.getAttribute("teacherid");
        return teacherId != null && teacherId.equals(course.getTeacherid());
    }

}
