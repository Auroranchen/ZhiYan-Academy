package com.controller;

import com.config.RequireRole;
import com.service.FileService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 受 Session 保护的通用生成文件下载入口。
 * 业务资源应优先使用其按 ID 下载接口。
 */
@RestController
@RequestMapping("/files")
public class SecureFileController {

    private final FileService fileService;

    public SecureFileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/download")
    @RequireRole({"teacher", "admin"})
    public ResponseEntity<Resource> download(@RequestParam String path) {
        Path safePath = fileService.resolveSafePath(path);
        if (safePath == null || !Files.isRegularFile(safePath)) {
            return ResponseEntity.notFound().build();
        }
        String filename = URLEncoder.encode(safePath.getFileName().toString(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(new FileSystemResource(safePath));
    }
}
