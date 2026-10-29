package com.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Word文档服务
 * 处理Word文档的读取和内容提取
 */
@Service
public class WordService {

    /**
     * 读取Word文档内容
     * 支持.doc和.docx格式
     * 
     * @param filePath 文件绝对路径
     * @return 文档文本内容
     * @throws IOException 读取异常
     */
    public String readWordContent(String filePath) throws IOException {
        if (filePath == null || filePath.isEmpty()) {
            throw new IOException("文件路径不能为空");
        }
        
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("文件不存在: " + filePath);
        }
        
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".docx") || lowerPath.endsWith(".doc")) {
            return readWordByContent(file);
        } else if (lowerPath.endsWith(".pdf")) {
            return readPdf(file);
        } else {
            throw new IOException("不支持的文件格式，仅支持.doc、.docx和.pdf");
        }
    }

    /**
     * 按**文件内容**而不是扩展名选择解析方式。
     *
     * <p>来历：语料里有两个 223 字节的 {@code .docx}，内容其实是 UTF-8 纯文本
     * （首位字节 {@code E8 BF 99} = 「这」）。按扩展名走 {@code XWPFDocument} 会抛
     * {@code NotOfficeXmlFileException}，上游再降级成一句资料描述——**用户的正文就这么丢了**。
     *
     * <p>反之亦然：真正被存成 {@code .doc} 的 OOXML 也不该报错。所以这里看魔数：
     * {@code PK..} 走 OOXML，OLE2 复合文档头走旧版 .doc，两者都不是就按纯文本读。
     */
    private String readWordByContent(File file) throws IOException {
        byte[] magic = readMagic(file);
        if (isZipContainer(magic)) return readDocx(file);
        if (isOle2Compound(magic)) return readDoc(file);
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    private static byte[] readMagic(File file) throws IOException {
        byte[] buf = new byte[4];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read = fis.read(buf);
            if (read <= 0) return new byte[0];
            return read == 4 ? buf : java.util.Arrays.copyOf(buf, read);
        }
    }

    /** OOXML（.docx/.pptx/.xlsx）都是 zip 容器，头四个字节为 "PK"。 */
    static boolean isZipContainer(byte[] magic) {
        return magic.length >= 4 && magic[0] == 'P' && magic[1] == 'K'
                && magic[2] == 0x03 && magic[3] == 0x04;
    }

    /** 旧版 Office 二进制格式的 OLE2 复合文档头 D0 CF 11 E0。 */
    static boolean isOle2Compound(byte[] magic) {
        return magic.length >= 4 && (magic[0] & 0xFF) == 0xD0 && (magic[1] & 0xFF) == 0xCF
                && (magic[2] & 0xFF) == 0x11 && (magic[3] & 0xFF) == 0xE0;
    }

    /**
     * 读取.docx格式文档
     */
    private String readDocx(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {
            
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    content.append(text).append("\n");
                }
            }
        }
        
        return content.toString().trim();
    }

    /**
     * 读取.doc格式文档
     */
    private String readDoc(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             HWPFDocument document = new HWPFDocument(fis);
             WordExtractor extractor = new WordExtractor(document)) {
            
            return extractor.getText().trim();
        }
    }

    /**
     * 读取PDF文档文本
     */
    private String readPdf(File file) throws IOException {
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document).trim();
        }
    }

    /**
     * 创建Word文档（.docx）
     * 
     * @param title 作业标题
     * @param content 作业内容（可能是HTML）
     * @return 生成的临时文件
     * @throws IOException IO异常
     */
    public File createWord(String title, String content) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            // 1. 添加标题
            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
            org.apache.poi.xwpf.usermodel.XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText(title);
            titleRun.setBold(true);
            titleRun.setFontSize(20);
            titleRun.addBreak();

            // 2. 处理内容（简单去除HTML标签）
            String plainText = stripHtml(content);
            
            // 3. 添加内容
            String[] lines = plainText.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                
                XWPFParagraph p = document.createParagraph();
                org.apache.poi.xwpf.usermodel.XWPFRun r = p.createRun();
                r.setText(line.trim());
                r.setFontSize(12);
            }

            // 4. 保存到临时文件
            File tempFile = File.createTempFile("homework_", ".docx");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile)) {
                document.write(out);
            }
            return tempFile;
        }
    }

    /**
     * 简单的HTML标签去除
     */
    private String stripHtml(String html) {
        if (html == null) return "";
        // 替换常见块级标签为换行
        String text = html.replaceAll("</?(div|p|br|h[1-6]|li|ul|ol)[^>]*>", "\n");
        // 去除其他标签
        text = text.replaceAll("<[^>]+>", "");
        // 处理HTML实体
        text = text.replace("&nbsp;", " ")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&amp;", "&")
                   .replace("&quot;", "\"")
                   .replace("&apos;", "'");
        return text.trim();
    }
}
