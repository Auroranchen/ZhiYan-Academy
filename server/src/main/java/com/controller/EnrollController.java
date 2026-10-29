package com.controller;

import com.config.RequireRole;
import com.dto.EnrollApplicationRequest;
import com.response.Result;
import com.service.EnrollService;
import com.service.FileService;
import com.service.LoginSecurityService;
import com.service.PublicRequestRateLimitService;
import com.service.SliderCaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 高校入驻申请控制器。
 * /enroll/apply 为公开提交入口；/enroll/list 供管理员查看。
 */
@RestController
@RequestMapping("/enroll")
public class EnrollController {
    private static final Logger logger = LoggerFactory.getLogger(EnrollController.class);

    private final EnrollService enrollService;
    private final FileService fileService;
    private final SliderCaptchaService captchaService;
    private final PublicRequestRateLimitService rateLimitService;
    private final LoginSecurityService loginSecurityService;

    public EnrollController(EnrollService enrollService, FileService fileService,
                            SliderCaptchaService captchaService,
                            PublicRequestRateLimitService rateLimitService,
                            LoginSecurityService loginSecurityService) {
        this.enrollService = enrollService;
        this.fileService = fileService;
        this.captchaService = captchaService;
        this.rateLimitService = rateLimitService;
        this.loginSecurityService = loginSecurityService;
    }

    /** 提交入驻申请（落地页公开调用） */
    @PostMapping("/apply")
    public Result<Void> apply(@Valid @RequestBody EnrollApplicationRequest body,
                              HttpServletRequest request) {
        String ip = loginSecurityService.resolveIp(request);
        if (!rateLimitService.allowApply(ip)) {
            return Result.error(429, "提交过于频繁，请稍后再试");
        }
        if (!captchaService.consumeToken(body.captchaToken())) {
            return Result.error(412, "滑动验证已失效，请重新验证");
        }
        return enrollService.apply(
            body.school(), body.name(), body.role(), body.phone(), body.remark(),
            body.attachment(), body.attachmentName()
        );
    }

    /** 上传需求文档（仅 Word / PDF，最大 10MB，落地页公开调用） */
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam("captchaToken") String captchaToken,
                                              HttpServletRequest request) {
        String ip = loginSecurityService.resolveIp(request);
        if (!rateLimitService.allowUpload(ip)) {
            return Result.error(429, "上传过于频繁，请稍后再试");
        }
        if (!captchaService.isTokenValid(captchaToken)) {
            return Result.error(412, "请先完成滑动验证");
        }
        if (file == null || file.isEmpty()) return Result.error("请选择文件");
        String name = file.getOriginalFilename();
        String lower = name == null ? "" : name.toLowerCase();
        if (!lower.endsWith(".doc") && !lower.endsWith(".docx") && !lower.endsWith(".pdf")) {
            return Result.error("仅支持上传 Word（.doc/.docx）或 PDF 文档");
        }
        if (!fileService.isSafePublicDocument(file)) {
            return Result.error(400, "文件内容与格式不匹配，已拒绝上传");
        }
        try {
            String path = fileService.saveFile(file);
            Map<String, String> data = new HashMap<>();
            data.put("path", path);
            data.put("name", name);
            return Result.success(data);
        } catch (Exception e) {
            logger.warn("公开入驻附件上传失败: {}", e.getMessage());
            return Result.error("上传失败，请稍后重试");
        }
    }

    /** 管理员下载申请附件 */
    @GetMapping("/attachment/{id}")
    @RequireRole("admin")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable("id") Integer id, HttpSession session) {
        Integer adminid = (Integer) session.getAttribute("adminid");
        if (adminid == null) return ResponseEntity.status(401).build();

        Map<String, Object> app = enrollService.getById(id);
        if (app == null || app.get("attachment") == null) return ResponseEntity.notFound().build();

        String absolutePath = fileService.getAbsolutePath((String) app.get("attachment"));
        if (absolutePath == null) return ResponseEntity.badRequest().build();
        File file = new File(absolutePath);
        if (!file.exists()) return ResponseEntity.notFound().build();

        String filename = app.get("attachmentName") != null ? (String) app.get("attachmentName") : file.getName();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(new FileSystemResource(file));
    }

    /** 管理员查看入驻申请列表 */
    @GetMapping("/list")
    @RequireRole("admin")
    public Result<List<Map<String, Object>>> list(HttpSession session) {
        Integer adminid = (Integer) session.getAttribute("adminid");
        if (adminid == null) {
            return Result.error(401, "请先登录管理员账号");
        }
        return Result.success(enrollService.list());
    }

    /** 管理员更新申请状态：0待审 / 1已处理 / 2驳回 */
    @PutMapping("/{id}/status")
    @RequireRole("admin")
    public Result<Void> updateStatus(@PathVariable("id") Integer id,
                                     @RequestBody Map<String, Object> body,
                                     HttpSession session) {
        Integer adminid = (Integer) session.getAttribute("adminid");
        if (adminid == null) {
            return Result.error(401, "请先登录管理员账号");
        }
        Object raw = body == null ? null : body.get("status");
        Integer status = null;
        if (raw instanceof Number) {
            status = ((Number) raw).intValue();
        } else if (raw != null) {
            try {
                status = Integer.valueOf(String.valueOf(raw).trim());
            } catch (NumberFormatException ignored) {
                return Result.error("status 必须为整数");
            }
        }
        return enrollService.updateStatus(id, status);
    }
}
