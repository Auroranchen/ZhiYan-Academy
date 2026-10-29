package com.controller;

import com.config.RequireRole;
import com.model.StudentHomework;
import com.response.Result;
import com.service.FileService;
import com.service.StudentHomeworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 学生作业控制器
 * 处理学生作业的提交、批改等请求
 */
@RestController
@RequestMapping("/studenthomework")
public class StudentHomeworkController {

    @Autowired
    private StudentHomeworkService studentHomeworkService;

    @Autowired
    private FileService fileService;

    @Autowired
    private com.service.MessageService messageService;

    @Autowired
    private com.service.GamificationService gamificationService;

    @Autowired
    private com.service.ResourceAccessService resourceAccess;

    /**
     * 获取学生作业列表（分页、搜索）
     * 
     * @param keyword  搜索关键字（学生姓名或作业标题）
     * @param page     页码，默认1
     * @param pageSize 每页数量，默认10
     * @return 学生作业列表
     */
    @GetMapping("/list")
    @RequireRole("admin")
    public Result<List<StudentHomework>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        List<StudentHomework> list = studentHomeworkService.getList(keyword, page, pageSize);
        Long total = studentHomeworkService.getCount(keyword);
        return Result.success(list, total);
    }

    /**
     * 获取学生作业详情
     * 
     * @param id 学生作业ID
     * @return 学生作业对象
     */
    @GetMapping("/{id}")
    public Result<StudentHomework> getById(@PathVariable("id") Integer id, HttpSession session) {
        StudentHomework studentHomework = studentHomeworkService.getById(id);
        if (studentHomework == null) {
            return Result.error("学生作业不存在");
        }
        if (!canViewSubmission(id, session)) {
            return Result.error(403, "无权查看该作业");
        }
        return Result.success(studentHomework);
    }

    /**
     * 根据作业ID获取学生提交列表
     * 
     * @param id       作业ID
     * @param keyword  搜索关键字
     * @param page     页码
     * @param pageSize 每页数量
     * @return 学生作业列表
     */
    @GetMapping("/byHomework/{id}")
    public Result<List<StudentHomework>> getByHomeworkId(
            @PathVariable("id") Integer id,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {
        if (!canManageHomework(id, session)) return Result.error(403, "无权查看该作业");
        List<StudentHomework> list = studentHomeworkService.getByHomeworkId(id, keyword, page, pageSize);
        Long total = studentHomeworkService.getCountByHomeworkId(id, keyword);
        return Result.success(list, total);
    }

    /**
     * 根据学生ID获取作业列表
     * 
     * @param id       学生ID
     * @param courseid 课程ID
     * @param semester 学期
     * @param page     页码
     * @param pageSize 每页数量
     * @return 学生作业列表
     */
    @GetMapping("/byStudent/{id}")
    public Result<List<StudentHomework>> getByStudentId(
            @PathVariable("id") Integer id,
            @RequestParam(required = false) Integer courseid,
            @RequestParam(required = false) String semester,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {
        Integer sessionStudentId = (Integer) session.getAttribute("studentid");
        boolean admin = session.getAttribute("adminid") != null;
        if (!admin && (sessionStudentId == null || !sessionStudentId.equals(id))) {
            return Result.error(403, "无权查看其他学生的作业");
        }
        List<StudentHomework> list = studentHomeworkService.getByStudentId(id, courseid, semester, page, pageSize);
        Long total = studentHomeworkService.getCountByStudentId(id, courseid, semester);
        return Result.success(list, total);
    }


    /**
     * 提交作业（在线答题）
     * 
     * @param studentHomework 学生作业对象
     * @return 操作结果
     */
    @PostMapping("/submit")
    @RequireRole("student")
    public Result<StudentHomework> submit(@RequestBody StudentHomework studentHomework, HttpSession session) {
        Integer studentId = (Integer) session.getAttribute("studentid");
        studentHomework.setStudentid(studentId);
        Result<StudentHomework> result = studentHomeworkService.submit(studentHomework);
        // 提交成功后发放积分（异常不影响主流程）
        if (result != null && result.getCode() != null && result.getCode() == 200 && studentHomework.getStudentid() != null) {
            try {
                gamificationService.onHomeworkSubmit(studentHomework.getStudentid());
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    /**
     * 上传作业文件（文档上传）
     * 
     * @param file       上传的文件
     * @param homeworkid 作业ID
     * @param studentid  学生ID
     * @return 操作结果
     */
    @PostMapping("/upload")
    public Result<StudentHomework> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("homeworkid") Integer homeworkid,
            @RequestParam(value = "studentid", required = false) Integer ignoredStudentid,
            HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(401, "请先登录学生账号");
        // 验证文件类型
        String originalFilename = file.getOriginalFilename();
        if (!fileService.isWordFile(originalFilename)) {
            return Result.error("只支持上传Word文档（.doc或.docx）");
        }
        
        // 验证文件大小
        if (file.getSize() > fileService.getMaxFileSize()) {
            return Result.error("文件大小不能超过10MB");
        }
        
        try {
            // 保存文件
            String filepath = fileService.saveFile(file);
            
            // 创建学生作业记录
            StudentHomework studentHomework = new StudentHomework();
            studentHomework.setHomeworkid(homeworkid);
            studentHomework.setStudentid(studentid);
            studentHomework.setFilepath(filepath);
            
            Result<StudentHomework> result = studentHomeworkService.upload(studentHomework);
            if (result != null && result.getCode() != null && result.getCode() == 200) {
                try {
                    gamificationService.onHomeworkSubmit(studentid);
                } catch (Exception ignored) {
                }
            }
            return result;
        } catch (IOException e) {
            return Result.error("文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 下载学生作业文件
     * 
     * @param id 学生作业ID
     * @return 文件资源
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable("id") Integer id, HttpSession session) {
        if (!canViewSubmission(id, session)) {
            return ResponseEntity.status(403).build();
        }
        // 获取学生作业
        StudentHomework studentHomework = studentHomeworkService.getById(id);
        if (studentHomework == null || studentHomework.getFilepath() == null) {
            return ResponseEntity.notFound().build();
        }
        
        // 获取文件
        String absolutePath = fileService.getAbsolutePath(studentHomework.getFilepath());
        File file = new File(absolutePath);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        
        // 构建文件名
        String filename = studentHomework.getStudentname() + "_" + studentHomework.getHomeworktitle();
        String extension = studentHomework.getFilepath().substring(studentHomework.getFilepath().lastIndexOf("."));
        filename = filename + extension;
        
        try {
            // 编码文件名
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");
            
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename*=UTF-8''" + encodedFilename)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 批改作业
     * 
     * @param studentHomework 学生作业对象（包含成绩和评语）
     * @return 结果
     */
    @RequestMapping(value = "/grade", method = {org.springframework.web.bind.annotation.RequestMethod.POST, org.springframework.web.bind.annotation.RequestMethod.PUT})
    public Result<?> grade(@RequestBody StudentHomework studentHomework, HttpSession session) {
        if (studentHomework.getId() == null) {
            return Result.error("ID不能为空");
        }
        if (studentHomework.getScore() == null) {
            return Result.error("成绩不能为空");
        }
        if (!canManageSubmission(studentHomework.getId(), session)) {
            return Result.error(403, "无权批改该作业");
        }
        
        return studentHomeworkService.grade(studentHomework);
    }

    /**
     * 打回作业
     * 
     * @param id 学生作业ID
     * @return 结果
     */
    @PostMapping("/return/{id}")
    public Result<?> returnHomework(@PathVariable("id") Integer id, HttpSession session) {
        if (!canManageSubmission(id, session)) return Result.error(403, "无权操作该作业");
        return studentHomeworkService.returnHomework(id);
    }

    /**
     * 删除学生作业
     * 
     * @param id 学生作业ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id, HttpSession session) {
        if (!canManageSubmission(id, session)) return Result.error(403, "无权删除该作业");
        return studentHomeworkService.delete(id);
    }

    /**
     * 根据教师ID获取学生作业列表
     * 
     * @param id         教师ID
     * @param homeworkid 作业ID（可选）
     * @param keyword    搜索关键字
     * @param semester   学期
     * @param page       页码
     * @param pageSize   每页数量
     * @return 学生作业列表
     */
    @GetMapping("/byTeacher/{id}")
    public Result<List<StudentHomework>> getByTeacherId(
            @PathVariable("id") Integer id,
            @RequestParam(required = false) Integer homeworkid,
            @RequestParam(required = false) Integer courseid,
            @RequestParam(required = false) Integer classid,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String semester,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {
        Integer sessionTeacherId = (Integer) session.getAttribute("teacherid");
        boolean admin = session.getAttribute("adminid") != null;
        if (!admin && (sessionTeacherId == null || !sessionTeacherId.equals(id))) {
            return Result.error(403, "无权查看其他教师的作业");
        }
        List<StudentHomework> list = studentHomeworkService.getByTeacherId(id, homeworkid, courseid, classid, keyword, semester, page, pageSize);
        Long total = studentHomeworkService.getCountByTeacherId(id, homeworkid, courseid, classid, keyword, semester);
        return Result.success(list, total);
    }

    /**
     * 获取某作业未提交的学生列表
     */
    @GetMapping("/unsubmitted/{homeworkid}")
    public Result<List<com.model.Student>> getUnsubmittedStudents(@PathVariable("homeworkid") Integer homeworkid,
                                                                  HttpSession session) {
        if (!canManageHomework(homeworkid, session)) return Result.error(403, "无权查看该作业");
        List<com.model.Student> list = studentHomeworkService.getUnsubmittedStudents(homeworkid);
        return Result.success(list);
    }

    /**
     * 批量发送提醒消息给未提交学生
     */
    @PostMapping("/remindUnsubmitted")
    public Result<Void> remindUnsubmitted(@RequestBody java.util.Map<String, Object> params, HttpSession session) {
        Integer homeworkid = (Integer) params.get("homeworkid");
        String title = (String) params.get("title");
        String content = (String) params.get("content");

        if (homeworkid == null) {
            return Result.error("作业ID不能为空");
        }
        if (!canManageHomework(homeworkid, session)) return Result.error(403, "无权操作该作业");
        Integer teacherId = (Integer) session.getAttribute("teacherid");

        List<com.model.Student> students = studentHomeworkService.getUnsubmittedStudents(homeworkid);
        if (students == null || students.isEmpty()) {
            return Result.error("没有未提交的学生");
        }

        // 逐个发送消息
        java.util.Date now = new java.util.Date();
        for (com.model.Student student : students) {
            com.model.Message message = new com.model.Message();
            message.setTitle(title);
            message.setContent(content);
            boolean admin = session.getAttribute("adminid") != null;
            message.setSendertype(admin ? 1 : 2);
            message.setSenderid(admin ? (Integer) session.getAttribute("adminid") : teacherId);
            message.setReceivertype(1); // 学生
            message.setReceiverid(student.getStudentid());
            message.setCreatetime(now);
            messageService.send(message);
        }

        return Result.success();
    }

    /**
     * 手动重试AI评分
     */
    @PostMapping("/retryGrade/{id}")
    public Result<Void> retryGrade(@PathVariable Integer id, HttpSession session) {
        if (!canRetrySubmission(id, session)) return Result.error(403, "无权操作该作业");
        return studentHomeworkService.retryGrade(id);
    }

    /* 归属校验统一收敛到 ResourceAccessService，这里只保留语义化的转发 */

    private boolean canRetrySubmission(Integer submissionId, HttpSession session) {
        return resourceAccess.canManageSubmission(submissionId, session)
                || resourceAccess.isSubmissionOwner(submissionId, session);
    }

    private boolean canViewSubmission(Integer submissionId, HttpSession session) {
        return resourceAccess.canViewSubmission(submissionId, session);
    }

    private boolean canManageSubmission(Integer submissionId, HttpSession session) {
        return resourceAccess.canManageSubmission(submissionId, session);
    }

    private boolean canManageHomework(Integer homeworkId, HttpSession session) {
        return resourceAccess.canManageHomework(homeworkId, session);
    }
}
