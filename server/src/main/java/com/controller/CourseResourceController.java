package com.controller;

import com.config.RequireRole;
import com.model.CourseResource;
import com.response.Result;
import com.service.CourseResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 课程资源控制器
 */
@RestController
@RequestMapping("/courseResource")
public class CourseResourceController {

    @Autowired
    private CourseResourceService courseResourceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 根据课程ID获取资源列表
     */
    @GetMapping("/byCourse/{courseid}")
    public Result<List<CourseResource>> getByCourseid(
            @PathVariable("courseid") Integer courseid,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {
        if (!canViewCourse(courseid, session)) return Result.error(403, "无权查看该课程资源");
        List<CourseResource> list = courseResourceService.getByCourseid(courseid, page, pageSize);
        Long total = courseResourceService.getCountByCourseid(courseid);
        return Result.success(list, total);
    }

    /**
     * 根据教师ID获取资源列表
     */
    @GetMapping("/byTeacher/{teacherid}")
    public Result<List<CourseResource>> getByTeacherid(
            @PathVariable("teacherid") Integer teacherid,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) Integer courseid,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {
        boolean admin = session.getAttribute("adminid") != null;
        Integer sessionTeacherId = (Integer) session.getAttribute("teacherid");
        if (!admin && (sessionTeacherId == null || !sessionTeacherId.equals(teacherid))) {
            return Result.error(403, "无权查看其他教师的资源");
        }
        List<CourseResource> list = courseResourceService.getByTeacherid(teacherid, keyword, semester, courseid, page, pageSize);
        Long total = courseResourceService.getCountByTeacherid(teacherid, keyword, semester, courseid);
        return Result.success(list, total);
    }

    /**
     * 获取资源详情
     */
    @GetMapping("/{id}")
    public Result<CourseResource> getById(@PathVariable("id") Integer id, HttpSession session) {
        CourseResource resource = courseResourceService.getById(id);
        if (resource == null) {
            return Result.error("资源不存在");
        }
        if (!canViewCourse(resource.getCourseid(), session)) return Result.error(403, "无权查看该资源");
        return Result.success(resource);
    }

    /**
     * 上传资源
     */
    @PostMapping("/upload")
    @RequireRole("teacher")
    public Result<CourseResource> upload(
            @RequestParam("courseid") Integer courseid,
            @RequestParam(value = "teacherid", required = false) Integer ignoredTeacherid,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "filetype", required = false) String filetype,
            @RequestParam("file") MultipartFile file,
            HttpSession session) {
        Integer teacherid = (Integer) session.getAttribute("teacherid");
        if (!teacherOwnsCourse(courseid, teacherid)) return Result.error(403, "无权向该课程上传资源");
        return courseResourceService.upload(courseid, teacherid, title, description, filetype, file);
    }

    /**
     * 更新资源信息
     */
    @PutMapping("/update")
    @RequireRole({"teacher", "admin"})
    public Result<Void> update(@RequestBody CourseResource resource, HttpSession session) {
        if (!canManage(resource.getId(), session)) return Result.error(403, "无权修改该资源");
        CourseResource existing = courseResourceService.getById(resource.getId());
        if (existing != null) {
            resource.setTeacherid(existing.getTeacherid());
            resource.setCourseid(existing.getCourseid());
        }
        return courseResourceService.update(resource);
    }

    /**
     * 删除资源
     */
    @DeleteMapping("/delete/{id}")
    @RequireRole({"teacher", "admin"})
    public Result<Void> delete(@PathVariable("id") Integer id, HttpSession session) {
        if (!canManage(id, session)) return Result.error(403, "无权删除该资源");
        return courseResourceService.delete(id);
    }

    /**
     * 下载资源
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable("id") Integer id, HttpSession session) {
        CourseResource resource = courseResourceService.getById(id);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canViewCourse(resource.getCourseid(), session)) {
            return ResponseEntity.status(403).build();
        }

        File file = courseResourceService.resolveStoredPath(resource.getFilepath()).toFile();
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        // 增加下载次数
        courseResourceService.incrementDownloadCount(id);

        String filename = resource.getFilename();
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(new FileSystemResource(file));
    }

    private boolean canManage(Integer resourceId, HttpSession session) {
        if (resourceId == null || session == null) return false;
        if (session.getAttribute("adminid") != null) return true;
        Integer teacherId = (Integer) session.getAttribute("teacherid");
        CourseResource resource = courseResourceService.getById(resourceId);
        return teacherId != null && resource != null && teacherId.equals(resource.getTeacherid());
    }

    private boolean canViewCourse(Integer courseId, HttpSession session) {
        if (courseId == null || session == null) return false;
        if (session.getAttribute("adminid") != null) return true;
        Integer teacherId = (Integer) session.getAttribute("teacherid");
        if (teacherId != null) return teacherOwnsCourse(courseId, teacherId);
        Integer classId = (Integer) session.getAttribute("classid");
        if (classId == null) return false;
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM courseclass WHERE courseid=? AND classid=?",
                    Integer.class, courseId, classId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean teacherOwnsCourse(Integer courseId, Integer teacherId) {
        if (courseId == null || teacherId == null) return false;
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM course WHERE courseid=? AND teacherid=?",
                    Integer.class, courseId, teacherId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
