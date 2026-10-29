package com.controller;

import com.mapper.AdminMapper;
import com.mapper.StudentMapper;
import com.mapper.TeacherMapper;
import com.model.Admin;
import com.model.Student;
import com.model.Teacher;
import com.response.Result;
import com.service.FileService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 头像控制器
 * 处理用户头像上传，支持管理员、教师、学生三种角色
 */
@RestController
@RequestMapping("/avatar")
public class AvatarController {

    private static final Logger logger = LoggerFactory.getLogger(AvatarController.class);

    /**
     * 允许的图片文件类型
     */
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    /**
     * 最大文件大小：2MB
     */
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;

    @Autowired
    private FileService fileService;

    @Autowired
    private AdminMapper adminMapper;

    @Autowired
    private TeacherMapper teacherMapper;

    @Autowired
    private StudentMapper studentMapper;

    /**
     * 上传头像
     *
     * @param file    上传的图片文件
     * @param session HTTP会话
     * @return 包含新头像URL的结果
     */
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file, HttpSession session) {
        // 1. 从 session 获取用户角色和 ID
        String role = (String) session.getAttribute("role");
        Integer userId = null;

        if ("student".equals(role)) {
            userId = (Integer) session.getAttribute("studentid");
        } else if ("teacher".equals(role)) {
            userId = (Integer) session.getAttribute("teacherid");
        } else {
            // 管理员登录时未设置 role 属性，通过 adminid 判断
            userId = (Integer) session.getAttribute("adminid");
            if (userId != null) {
                role = "admin";
            }
        }

        // 未登录检查
        if (userId == null || role == null) {
            return Result.error(401, "请先登录");
        }

        // 2. 验证文件是否为空
        if (file == null || file.isEmpty()) {
            return Result.error("请选择要上传的文件");
        }

        // 3. 验证文件类型（小程序 chooseAvatar 常上报 application/octet-stream，需按扩展名兜底）
        String contentType = file.getContentType();
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean allowedByType = contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType);
        boolean allowedByExt = originalName.endsWith(".jpg") || originalName.endsWith(".jpeg")
                || originalName.endsWith(".png") || originalName.endsWith(".gif")
                || originalName.endsWith(".webp");
        boolean likelyImage = "application/octet-stream".equals(contentType)
                || contentType == null
                || contentType.isBlank();
        if (!allowedByType && !(likelyImage && (allowedByExt || originalName.isEmpty()))) {
            return Result.error("仅支持 JPEG、PNG、GIF、WebP 格式的图片");
        }

        // 4. 验证文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小不能超过 2MB");
        }

        try {
            // 5. 调用 FileService 保存图片（兼容无扩展名的微信临时文件）
            String filePath = fileService.saveImageFile(file);

            // 6. 查询并删除旧头像文件
            String oldAvatar = getOldAvatar(role, userId);
            if (oldAvatar != null && !oldAvatar.isEmpty()) {
                try {
                    fileService.deleteFile(oldAvatar);
                } catch (Exception e) {
                    // 旧头像删除失败不阻塞新头像保存流程
                    logger.warn("删除旧头像文件失败: {}", oldAvatar, e);
                }
            }

            // 7. 更新数据库中的头像字段
            updateAvatarInDb(role, userId, filePath);

            // 8. 返回 API 方式的头像访问 URL（避免静态资源映射路径问题）
            String avatarUrl = "/avatar/get/" + role + "/" + userId + "?t=" + System.currentTimeMillis();
            Map<String, String> data = new HashMap<>();
            data.put("avatarUrl", avatarUrl);

            return Result.success("头像上传成功", data);

        } catch (IOException e) {
            logger.error("头像上传失败", e);
            return Result.error("头像上传失败，请稍后重试");
        }
    }

    /**
     * 获取头像图片
     * 通过 API 直接返回图片流，避免静态资源映射在不同部署环境下的路径问题
     *
     * @param role 用户角色（admin/teacher/student）
     * @param id   用户ID
     * @return 图片文件流
     */
    @GetMapping("/get/{role}/{id}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String role, @PathVariable Integer id) {
        String avatarPath = getOldAvatar(role, id);
        if (avatarPath == null || avatarPath.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        String absolutePath = fileService.getAbsolutePath(avatarPath);
        File file = new File(absolutePath);
        if (!file.exists()) {
            return ResponseEntity.noContent().build();
        }

        // 根据文件扩展名确定 Content-Type
        String filename = file.getName().toLowerCase();
        MediaType mediaType = MediaType.IMAGE_JPEG;
        if (filename.endsWith(".png")) {
            mediaType = MediaType.IMAGE_PNG;
        } else if (filename.endsWith(".gif")) {
            mediaType = MediaType.IMAGE_GIF;
        } else if (filename.endsWith(".webp")) {
            mediaType = MediaType.parseMediaType("image/webp");
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }

    /**
     * 获取用户当前的旧头像路径
     *
     * @param role   用户角色
     * @param userId 用户ID
     * @return 旧头像路径，不存在则返回 null
     */
    private String getOldAvatar(String role, Integer userId) {
        switch (role) {
            case "admin":
                Admin admin = adminMapper.findById(userId);
                return admin != null ? admin.getAvatar() : null;
            case "teacher":
                Teacher teacher = teacherMapper.findById(userId);
                return teacher != null ? teacher.getAvatar() : null;
            case "student":
                Student student = studentMapper.findById(userId);
                return student != null ? student.getAvatar() : null;
            default:
                return null;
        }
    }

    /**
     * 更新数据库中的头像字段
     *
     * @param role     用户角色
     * @param userId   用户ID
     * @param filePath 新头像文件路径
     */
    private void updateAvatarInDb(String role, Integer userId, String filePath) {
        switch (role) {
            case "admin":
                adminMapper.updateAvatar(userId, filePath);
                break;
            case "teacher":
                teacherMapper.updateAvatar(userId, filePath);
                break;
            case "student":
                studentMapper.updateAvatar(userId, filePath);
                break;
        }
    }
}
