package com.controller;

import com.config.RequireRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.model.Homework;
import com.response.Result;
import com.service.FileService;
import com.service.HomeworkService;
import com.service.WordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 作业控制器
 * 处理作业的增删改查请求
 */
@RestController
@RequestMapping("/homework")
public class HomeworkController {

    private static final Logger log = LoggerFactory.getLogger(HomeworkController.class);

    @Autowired
    private HomeworkService homeworkService;

    @Autowired
    private WordService wordService;

    @Autowired
    private FileService fileService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取作业列表（分页、搜索）
     * 
     * @param keyword  搜索关键字（作业标题）
     * @param page     页码，默认1
     * @param pageSize 每页数量，默认10
     * @return 作业列表
     */
    @GetMapping("/list")
    @RequireRole("admin")
    public Result<List<Homework>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        // 查询列表
        List<Homework> list = homeworkService.getList(keyword, page, pageSize);
        // 查询总数
        Long total = homeworkService.getCount(keyword);
        return Result.success(list, total);
    }

    /**
     * 获取作业详情
     * 
     * @param id 作业ID
     * @return 作业对象
     */
    @GetMapping("/{id}")
    public Result<Homework> getById(@PathVariable("id") Integer id, HttpSession session) {
        Homework homework = homeworkService.getById(id);
        if (homework == null) {
            return Result.error("作业不存在");
        }
        if (!canView(homework, session)) return Result.error(403, "无权查看该作业");
        return Result.success(forViewer(homework, session));
    }

    /**
     * 根据课程ID获取作业列表
     * 
     * @param id 课程ID
     * @return 作业列表
     */
    @GetMapping("/byCourse/{id}")
    public Result<List<Homework>> getByCourseId(@PathVariable("id") Integer id, HttpSession session) {
        if (!canViewCourse(id, session)) return Result.error(403, "无权查看该课程作业");
        List<Homework> list = homeworkService.getByCourseId(id);
        if (isStudent(session) && list != null) list.forEach(this::redactAnswers);
        return Result.success(list);
    }

    /**
     * 根据教师ID获取作业列表（分页）
     * 
     * @param id       教师ID
     * @param keyword  搜索关键字
     * @param semester 学期
     * @param page     页码
     * @param pageSize 每页数量
     * @return 作业列表
     */
    @GetMapping("/byTeacher/{id}")
    public Result<List<Homework>> getByTeacherId(
            @PathVariable("id") Integer id,
            @RequestParam(required = false) Integer classid,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String semester,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {
        Integer teacherId = (Integer) session.getAttribute("teacherid");
        boolean admin = session.getAttribute("adminid") != null;
        if (!admin && (teacherId == null || !teacherId.equals(id))) {
            return Result.error(403, "无权查看其他教师的作业");
        }
        List<Homework> list = homeworkService.getByTeacherId(id, classid, keyword, semester, page, pageSize);
        Long total = homeworkService.getCountByTeacherId(id, classid, keyword, semester);
        return Result.success(list, total);
    }

    /**
     * 根据班级ID获取作业列表（学生可见的作业）
     * 
     * @param id       班级ID
     * @param semester 学期
     * @param page     页码
     * @param pageSize 每页数量
     * @return 作业列表
     */
    @GetMapping("/byClass/{id}")
    @RequireRole({"student", "admin"})
    public Result<List<Homework>> getByClassId(
            @PathVariable("id") Integer id,
            @RequestParam(required = false) String semester,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {
        Integer studentClassId = (Integer) session.getAttribute("classid");
        boolean privileged = session.getAttribute("adminid") != null || session.getAttribute("teacherid") != null;
        if (!privileged && (studentClassId == null || !studentClassId.equals(id))) {
            return Result.error(403, "无权查看其他班级的作业");
        }
        List<Homework> list = homeworkService.getByClassId(id, semester, page, pageSize);
        if (isStudent(session) && list != null) list.forEach(this::redactAnswers);
        Long total = homeworkService.getCountByClassId(id, semester);
        return Result.success(list, total);
    }

    /**
     * 添加作业
     * 
     * @param homework 作业对象
     * @return 操作结果
     */
    @PostMapping("/add")
    @RequireRole({"teacher", "admin"})
    public Result<Void> add(@RequestBody Homework homework, HttpSession session) {
        Integer teacherId = (Integer) session.getAttribute("teacherid");
        if (teacherId != null) {
            if (!teacherOwnsCourse(homework.getCourseid(), teacherId)) {
                return Result.error(403, "无权向该课程发布作业");
            }
            homework.setTeacherid(teacherId);
        }
        return homeworkService.add(homework);
    }

    /**
     * 更新作业
     * 
     * @param homework 作业对象
     * @return 操作结果
     */
    @PutMapping("/update")
    @RequireRole({"teacher", "admin"})
    public Result<Void> update(@RequestBody Homework homework, HttpSession session) {
        if (!canManage(homework.getHomeworkid(), session)) return Result.error(403, "无权修改该作业");
        Integer teacherId = (Integer) session.getAttribute("teacherid");
        if (teacherId != null && !teacherOwnsCourse(homework.getCourseid(), teacherId)) {
            return Result.error(403, "无权将作业移动到该课程");
        }
        Homework existing = homeworkService.getById(homework.getHomeworkid());
        if (existing != null) homework.setTeacherid(existing.getTeacherid());
        return homeworkService.update(homework);
    }

    /**
     * 删除作业
     * 
     * @param id 作业ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{id}")
    @RequireRole({"teacher", "admin"})
    public Result<Void> delete(@PathVariable("id") Integer id, HttpSession session) {
        if (!canManage(id, session)) return Result.error(403, "无权删除该作业");
        return homeworkService.delete(id);
    }

    @GetMapping("/download-doc/{id}")
    public ResponseEntity<InputStreamResource> downloadHomeworkDoc(@PathVariable("id") Integer id,
                                                                    HttpSession session) {
        try {
            Homework homework = homeworkService.getById(id);
            if (homework == null) {
                log.warn("下载作业文档失败，作业不存在: {}", id);
                return ResponseEntity.notFound().build();
            }
            if (!canView(homework, session)) return ResponseEntity.status(403).build();
            homework = forViewer(homework, session);

            File wordFile = wordService.createWord(homework.getTitle(), homework.getContent());
            long contentLength = wordFile.length();
            // 临时文件在响应流关闭后立即删除，避免每次下载都在系统临时目录堆积文件。
            InputStreamResource resource = new InputStreamResource(new FileInputStream(wordFile) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        if (!wordFile.delete()) {
                            wordFile.deleteOnExit();
                        }
                    }
                }
            });

            // URL编码文件名，防止中文乱码
            String filename = URLEncoder.encode(homework.getTitle() + "_作业说明.docx", StandardCharsets.UTF_8.toString());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(contentLength)
                    .body(resource);
        } catch (Exception e) {
            log.error("生成作业文档失败，作业ID: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 上传作业附件（Word/PDF）
     */
    @PostMapping("/uploadAttachment")
    @RequireRole({"teacher", "admin"})
    public Result<Map<String, String>> uploadAttachment(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return Result.error("文件不能为空");
            }
            String originalName = file.getOriginalFilename();
            if (originalName == null) {
                return Result.error("文件名不能为空");
            }
            String lowerName = originalName.toLowerCase();
            if (!(lowerName.endsWith(".doc") || lowerName.endsWith(".docx") || lowerName.endsWith(".pdf"))) {
                return Result.error("仅支持Word(.doc/.docx)和PDF文件");
            }
            if (file.getSize() > 20 * 1024 * 1024) {
                return Result.error("文件大小不能超过20MB");
            }
            String filepath = fileService.saveFile(file);
            String url = fileService.getDownloadUrl(filepath);
            Map<String, String> data = new HashMap<>();
            data.put("filepath", filepath);
            data.put("url", url);
            data.put("originalName", originalName);
            return Result.success(data);
        } catch (Exception e) {
            log.error("作业附件上传失败", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 读取附件文本内容（Word文档提取文字）
     */
    @GetMapping("/readAttachmentContent")
    public Result<String> readAttachmentContent(@RequestParam("path") String path,
                                                @RequestParam(value = "homeworkId", required = false) Integer homeworkId,
                                                HttpSession session) {
        try {
            if (path == null || path.isEmpty()) {
                return Result.error("文件路径不能为空");
            }
            if (isStudent(session)) {
                Homework homework = homeworkService.getById(homeworkId);
                if (homework == null || !canView(homework, session) || !samePath(path, homework.getAttachment())) {
                    return Result.error(403, "无权读取该附件");
                }
            } else if (session.getAttribute("teacherid") == null && session.getAttribute("adminid") == null) {
                return Result.error(403, "无权读取附件");
            }
            String absolutePath = fileService.getAbsolutePath(path);
            if (absolutePath == null) {
                return Result.error("文件路径无效");
            }
            File file = new File(absolutePath);
            if (!file.exists()) {
                return Result.error("文件不存在");
            }
            String lowerPath = path.toLowerCase();
            if (lowerPath.endsWith(".doc") || lowerPath.endsWith(".docx") || lowerPath.endsWith(".pdf")) {
                String content = wordService.readWordContent(absolutePath);
                return Result.success(content);
            } else {
                return Result.error("不支持的文件格式");
            }
        } catch (Exception e) {
            log.error("解析作业文件内容失败", e);
            return Result.error("读取文件内容失败: " + e.getMessage());
        }
    }

    /**
     * 下载作业附件
     */
    @GetMapping("/downloadAttachment")
    public ResponseEntity<InputStreamResource> downloadAttachment(
            @RequestParam(value = "homeworkId", required = false) Integer homeworkId,
            @RequestParam(value = "path", required = false) String path,
            HttpSession session) {
        try {
            if (homeworkId != null) {
                Homework homework = homeworkService.getById(homeworkId);
                if (homework == null) return ResponseEntity.notFound().build();
                if (!canView(homework, session)) return ResponseEntity.status(403).build();
                path = homework.getAttachment();
            } else if (session.getAttribute("teacherid") == null && session.getAttribute("adminid") == null) {
                return ResponseEntity.status(403).build();
            }
            if (path == null || path.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            String absolutePath = fileService.getAbsolutePath(path);
            if (absolutePath == null) return ResponseEntity.notFound().build();
            File file = new File(absolutePath);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
            String filename = file.getName();
            String encodedName = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(file.length())
                    .body(resource);
        } catch (Exception e) {
            log.error("下载作业附件失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Homework forViewer(Homework homework, HttpSession session) {
        if (homework != null && isStudent(session)) redactAnswers(homework);
        return homework;
    }

    void redactAnswers(Homework homework) {
        if (homework == null || homework.getContent() == null || homework.getType() == null
                || homework.getType() != 1) return;
        try {
            JsonNode root = objectMapper.readTree(homework.getContent());
            JsonNode questions = root.path("questions");
            if (!questions.isArray()) return;
            for (JsonNode question : questions) {
                if (question instanceof ObjectNode object) {
                    object.remove(List.of("answer", "correctAnswer", "referenceAnswer"));
                }
            }
            homework.setContent(objectMapper.writeValueAsString(root));
        } catch (Exception ignored) {
            // Legacy rich-text homework is not structured exam JSON and is returned unchanged.
        }
    }

    private boolean isStudent(HttpSession session) {
        return session != null && session.getAttribute("studentid") != null;
    }

    private boolean canView(Homework homework, HttpSession session) {
        if (homework == null || session == null) return false;
        if (session.getAttribute("adminid") != null) return true;
        Integer teacherId = (Integer) session.getAttribute("teacherid");
        if (teacherId != null) return teacherId.equals(homework.getTeacherid());
        return canViewCourse(homework.getCourseid(), session);
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

    private boolean samePath(String left, String right) {
        if (left == null || right == null) return false;
        return left.replace('\\', '/').equals(right.replace('\\', '/'));
    }

    private boolean canManage(Integer homeworkId, HttpSession session) {
        if (homeworkId == null || session == null) return false;
        if (session.getAttribute("adminid") != null) return true;
        Integer teacherId = (Integer) session.getAttribute("teacherid");
        Homework homework = homeworkService.getById(homeworkId);
        return teacherId != null && homework != null && teacherId.equals(homework.getTeacherid());
    }
}
