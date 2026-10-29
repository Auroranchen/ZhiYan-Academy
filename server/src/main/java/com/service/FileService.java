package com.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文件服务层
 * 处理文件上传和下载
 */
@Service
public class FileService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".doc", ".docx", ".pdf", ".ppt", ".pptx",
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".txt", ".csv"
    );

    /**
     * 文件存储目录
     */
    @Value("${file.server.dir:upload/}")
    private String uploadDir;

    /**
     * 文件访问路径前缀
     */
    @Value("${file.server.path:/upload/}")
    private String uploadPath;

    /**
     * 获取上传目录的绝对路径
     */
    private String getUploadDirAbsolute() {
        return getUploadRoot().toString() + File.separator;
    }

    private Path getUploadRoot() {
        return Paths.get(System.getProperty("user.dir"))
                .resolve(uploadDir)
                .toAbsolutePath()
                .normalize();
    }

    /**
     * 保存上传的文件
     * 
     * @param file 上传的文件
     * @return 文件保存路径（相对路径）
     * @throws IOException 文件保存异常
     */
    /**
     * 保存图片文件（头像等）。无扩展名时默认按 JPEG 处理，兼容小程序 chooseAvatar。
     */
    public String saveImageFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("文件不能为空");
        }
        if (file.getSize() > getMaxFileSize()) {
            throw new IOException("文件大小不能超过 " + (getMaxFileSize() / 1024 / 1024) + "MB");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = ".jpg";
        if (originalFilename != null) {
            int dotIndex = originalFilename.lastIndexOf(".");
            if (dotIndex > 0) {
                String ext = originalFilename.substring(dotIndex).toLowerCase();
                if (Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp").contains(ext)) {
                    extension = ext;
                }
            }
        }

        String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String newFilename = dateStr + "_" + UUID.randomUUID().toString().replace("-", "") + extension;
        String datePath = new SimpleDateFormat("yyyy/MM").format(new Date());
        File destDir = new File(getUploadDirAbsolute() + datePath.replace("/", File.separator));
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        File destFile = new File(destDir, newFilename);
        file.transferTo(destFile);
        return datePath + "/" + newFilename;
    }

    public String saveFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("文件不能为空");
        }

        // 文件大小校验
        if (file.getSize() > getMaxFileSize()) {
            throw new IOException("文件大小不能超过 " + (getMaxFileSize() / 1024 / 1024) + "MB");
        }
        
        // 获取原始文件名
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IOException("文件名不能为空");
        }
        
        // 获取文件扩展名
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf(".");
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IOException("不支持的文件类型");
        }
        
        // 生成新文件名（日期 + UUID）
        String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String newFilename = dateStr + "_" + UUID.randomUUID().toString().replace("-", "") + extension;
        
        // 创建日期目录
        String datePath = new SimpleDateFormat("yyyy/MM").format(new Date());
        File destDir = new File(getUploadDirAbsolute() + datePath.replace("/", File.separator));
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        
        // 保存文件
        File destFile = new File(destDir, newFilename);
        file.transferTo(destFile);
        
        // 返回相对路径
        return datePath + "/" + newFilename;
    }

    /**
     * 获取文件下载URL
     * 
     * @param filepath 文件相对路径
     * @return 下载URL
     */
    public String getDownloadUrl(String filepath) {
        if (filepath == null || filepath.isEmpty()) {
            return null;
        }
        return "/files/download?path=" + URLEncoder.encode(filepath, StandardCharsets.UTF_8);
    }

    /**
     * 获取文件的绝对路径
     * 
     * @param filepath 文件相对路径
     * @return 绝对路径
     */
    public String getAbsolutePath(String filepath) {
        Path path = resolveSafePath(filepath);
        return path == null ? null : path.toString();
    }

    /**
     * 将相对路径解析到上传根目录内，拒绝绝对路径和目录穿越。
     */
    public Path resolveSafePath(String filepath) {
        if (filepath == null || filepath.isBlank()) return null;
        try {
            Path relative = Paths.get(filepath.replace("\\", "/"));
            if (relative.isAbsolute()) return null;
            Path root = getUploadRoot();
            Path resolved = root.resolve(relative).normalize();
            return resolved.startsWith(root) ? resolved : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检查文件是否存在
     * 
     * @param filepath 文件相对路径
     * @return true-存在，false-不存在
     */
    public boolean exists(String filepath) {
        if (filepath == null || filepath.isEmpty()) {
            return false;
        }
        Path path = resolveSafePath(filepath);
        return path != null && java.nio.file.Files.isRegularFile(path);
    }

    /**
     * 删除文件
     * 
     * @param filepath 文件相对路径
     * @return true-删除成功，false-删除失败
     */
    public boolean deleteFile(String filepath) {
        if (filepath == null || filepath.isEmpty()) {
            return false;
        }
        Path path = resolveSafePath(filepath);
        if (path == null) return false;
        File file = path.toFile();
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    /**
     * 验证文件类型是否为Word文档
     * 
     * @param filename 文件名
     * @return true-是Word文档，false-不是
     */
    public boolean isWordFile(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        String lowerName = filename.toLowerCase();
        return lowerName.endsWith(".doc") || lowerName.endsWith(".docx");
    }

    public boolean isPptFile(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        String lowerName = filename.toLowerCase();
        return lowerName.endsWith(".ppt") || lowerName.endsWith(".pptx");
    }

    /** 使用文件签名与 Office ZIP 结构校验公开上传内容，拒绝仅伪造扩展名的文件。 */
    public boolean isSafePublicDocument(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > getMaxFileSize()) return false;
        String name = file.getOriginalFilename();
        String lower = name == null ? "" : name.toLowerCase();
        try {
            byte[] header;
            try (InputStream headerStream = file.getInputStream()) {
                header = headerStream.readNBytes(8);
            }
            if (lower.endsWith(".pdf")) {
                return header.length >= 5 && header[0] == '%' && header[1] == 'P'
                        && header[2] == 'D' && header[3] == 'F' && header[4] == '-';
            }
            if (lower.endsWith(".doc")) {
                byte[] ole = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                        (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
                return java.util.Arrays.equals(header, ole);
            }
            if (lower.endsWith(".docx")) {
                if (header.length < 4 || header[0] != 'P' || header[1] != 'K') return false;
                boolean contentTypes = false;
                boolean wordDocument = false;
                int entries = 0;
                try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null && entries++ < 2000) {
                        String entryName = entry.getName();
                        if ("[Content_Types].xml".equals(entryName)) contentTypes = true;
                        if ("word/document.xml".equals(entryName)) wordDocument = true;
                        if (contentTypes && wordDocument) return true;
                    }
                }
            }
        } catch (IOException ignored) {
            return false;
        }
        return false;
    }

    /**
     * 获取文件大小限制（MB）
     * 
     * @return 文件大小限制
     */
    public long getMaxFileSize() {
        return 10 * 1024 * 1024; // 10MB
    }

    public String saveBytes(byte[] data, String extension) throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("数据不能为空");
        }
        if (extension == null) {
            extension = "";
        } else if (!extension.startsWith(".")) {
            extension = "." + extension;
        }
        extension = extension.toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IOException("不支持的文件类型");
        }
        String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String newFilename = dateStr + "_" + UUID.randomUUID().toString().replace("-", "") + extension;
        String datePath = new SimpleDateFormat("yyyy/MM").format(new Date());
        File destDir = new File(getUploadDirAbsolute() + datePath.replace("/", File.separator));
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        File destFile = new File(destDir, newFilename);
        java.nio.file.Files.write(destFile.toPath(), data);
        return datePath + "/" + newFilename;
    }
}
