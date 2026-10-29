package com.service;

import com.mapper.CourseResourceMapper;
import com.model.CourseResource;
import com.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 课程资源业务逻辑层
 */
@Service
public class CourseResourceService {

    @Autowired
    private CourseResourceMapper courseResourceMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String UPLOAD_DIR = "uploads/resources";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".doc", ".docx", ".pdf", ".ppt", ".pptx", ".txt", ".md", ".png", ".jpg", ".jpeg"
    );

    /**
     * 根据课程ID获取资源列表
     */
    public List<CourseResource> getByCourseid(Integer courseid, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        return courseResourceMapper.findByCourseid(courseid, offset, pageSize);
    }

    public Long getCountByCourseid(Integer courseid) {
        return courseResourceMapper.countByCourseid(courseid);
    }

    /**
     * 根据教师ID获取资源列表
     */
    public List<CourseResource> getByTeacherid(Integer teacherid, String keyword, String semester, Integer courseid, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        if (semester != null && semester.trim().isEmpty()) {
            semester = null;
        }
        return courseResourceMapper.findByTeacherid(teacherid, keyword, semester, courseid, offset, pageSize);
    }

    public Long getCountByTeacherid(Integer teacherid, String keyword, String semester, Integer courseid) {
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        if (semester != null && semester.trim().isEmpty()) {
            semester = null;
        }
        return courseResourceMapper.countByTeacherid(teacherid, keyword, semester, courseid);
    }

    /**
     * 获取资源详情
     */
    public CourseResource getById(Integer id) {
        return courseResourceMapper.findById(id);
    }

    /**
     * 上传资源
     */
    @CacheEvict(value = "courseKnowledge", key = "#courseid")
    public Result<CourseResource> upload(Integer courseid, Integer teacherid, String title, String description, 
                                          String filetype, MultipartFile file) {
        if (courseid == null) {
            return Result.error("课程ID不能为空");
        }
        if (teacherid == null) {
            return Result.error("教师ID不能为空");
        }
        if (title == null || title.trim().isEmpty()) {
            return Result.error("标题不能为空");
        }
        if (file == null || file.isEmpty()) {
            return Result.error("请选择文件");
        }
        if (file.getSize() > 50L * 1024 * 1024) {
            return Result.error("文件大小不能超过50MB");
        }

        try {
            // 生成文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            }
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                return Result.error("不支持的课程资源类型");
            }
            String newFilename = UUID.randomUUID().toString() + extension;
            String filepath = UPLOAD_DIR + "/" + newFilename;

            // 保存文件
            Path uploadDir = resolveStoredPath(UPLOAD_DIR);
            Files.createDirectories(uploadDir);
            Path targetPath = uploadDir.resolve(newFilename);
            file.transferTo(targetPath.toFile());

            // 保存记录
            CourseResource resource = new CourseResource();
            resource.setCourseid(courseid);
            resource.setTeacherid(teacherid);
            resource.setTitle(title.trim());
            resource.setDescription(description);
            resource.setFiletype(filetype);
            resource.setFilepath(filepath);
            resource.setFilename(originalFilename);
            resource.setFilesize(file.getSize());
            resource.setCreatetime(new Date());

            int rows = courseResourceMapper.insert(resource);
            if (rows > 0) {
                return Result.success(resource);
            }
            return Result.error("保存失败");
        } catch (IOException e) {
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 更新资源信息
     */
    @CacheEvict(value = "courseKnowledge", allEntries = true)
    public Result<Void> update(CourseResource resource) {
        if (resource.getId() == null) {
            return Result.error("资源ID不能为空");
        }
        if (resource.getTitle() == null || resource.getTitle().trim().isEmpty()) {
            return Result.error("标题不能为空");
        }

        CourseResource existing = courseResourceMapper.findById(resource.getId());
        if (existing == null) {
            return Result.error("资源不存在");
        }

        resource.setTitle(resource.getTitle().trim());
        int rows = courseResourceMapper.update(resource);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("更新失败");
    }

    /**
     * 删除资源
     */
    @CacheEvict(value = "courseKnowledge", allEntries = true)
    public Result<Void> delete(Integer id) {
        if (id == null) {
            return Result.error("资源ID不能为空");
        }

        CourseResource resource = courseResourceMapper.findById(id);
        if (resource == null) {
            return Result.error("资源不存在");
        }

        // 删除文件
        File file = resolveStoredPath(resource.getFilepath()).toFile();
        if (file.exists()) {
            file.delete();
        }

        int rows = courseResourceMapper.delete(id);
        if (rows > 0) {
            try { jdbcTemplate.update("DELETE FROM course_knowledge_chunk WHERE resource_id=?", id); }
            catch (Exception ignored) {}
            return Result.success();
        }
        return Result.error("删除失败");
    }

    /**
     * 增加下载次数
     */
    public void incrementDownloadCount(Integer id) {
        courseResourceMapper.incrementDownloadCount(id);
    }

    public Path resolveStoredPath(String storedPath) {
        Path root = Paths.get(System.getProperty("user.dir"), UPLOAD_DIR).toAbsolutePath().normalize();
        if (UPLOAD_DIR.equals(storedPath)) return root;
        Path path = Paths.get(storedPath == null ? "" : storedPath);
        Path resolved = path.isAbsolute()
                ? path.toAbsolutePath().normalize()
                : Paths.get(System.getProperty("user.dir")).resolve(path).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("非法课程资源路径");
        }
        return resolved;
    }
}
