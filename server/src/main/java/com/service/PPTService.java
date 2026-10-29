package com.service;

import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.sl.usermodel.ShapeType;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import com.fasterxml.jackson.databind.JsonNode;
import com.ppt.SlideStyle;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.model.Homework;
import com.util.ObjectiveGrading;

import java.util.concurrent.*;

@Service
public class PPTService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PPTService.class);
    static {
        System.setProperty("java.awt.headless", "true");
    }

    @Autowired
    private ZhipuAIService zhipuAIService;
    @Autowired
    private AiSafeCallService aiSafeCallService;
    @Autowired
    private FileService fileService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // 使用线程池并发调用AI接口，避免串行导致超时，同时控制并发数防止API限流
    private static final ExecutorService AI_EXECUTOR = Executors.newFixedThreadPool(6);
    // 全局超时时间：50秒（留出10秒缓冲给文件生成和传输，防止504 Gateway Timeout）
    private static final long GLOBAL_TIMEOUT_MS = 50000;
    
    public String readPPTContent(String filePath) throws IOException {
        if (filePath == null || filePath.isEmpty()) {
            throw new IOException("文件路径不能为空");
        }
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("文件不存在: " + filePath);
        }
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".pptx")) {
            return readPptx(file);
        } else if (lower.endsWith(".ppt")) {
            return readPpt(file);
        } else {
            throw new IOException("不支持的文件格式，仅支持.ppt和.pptx");
        }
    }




    public String readLimitedContent(String filePath, int maxSlides, int maxLinesPerSlide) throws IOException {
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".pptx")) {
            return readPptxLimited(new File(filePath), maxSlides, maxLinesPerSlide);
        } else if (lower.endsWith(".ppt")) {
            return readPptLimited(new File(filePath), maxSlides, maxLinesPerSlide);
        } else {
            return readPPTContent(filePath);
        }
    }

    private String readPptx(File file) throws IOException {
        try {
            return readPptxViaPoi(file);
        } catch (Exception e) {
            // POI 的 XSLFSheet.registerShapeId 会把形状 id 强转成 int 存进 SparseBitSet。
            // pptx 里若存在超出 int 范围的形状 id（实测有 2147484766，强转后为负数），
            // 位集直接抛 IndexOutOfBoundsException，整个文件读不出来。
            //
            // 这类文件本身是合法 OOXML，正文就躺在 slide XML 里。与其让用户的资料
            // 静默降级成一句描述，不如退一步直接从包里抽文本。
            log.warn("POI 解析 pptx 失败，改用原始 XML 抽取 {}: {}", file.getName(), e.getMessage());
            return readPptxViaRawXml(file);
        }
    }

    private String readPptxViaPoi(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            List<XSLFSlide> slides = ppt.getSlides();
            for (XSLFSlide slide : slides) {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape ts) {
                        String text = ts.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            content.append(text.trim()).append("\n");
                        }
                    }
                }
            }
        }
        return content.toString().trim();
    }

    private static final Pattern SLIDE_ENTRY = Pattern.compile("ppt/slides/slide(\\d+)\\.xml");
    private static final Pattern TEXT_RUN = Pattern.compile("<a:t>(.*?)</a:t>", Pattern.DOTALL);

    /**
     * 直接从 pptx（zip）里读 slide XML，抽 {@code <a:t>} 文本节点。
     *
     * <p>刻意用正则而不是 DOM 解析：这些是**用户上传的不可信文件**，
     * 用 {@code DocumentBuilder} 解析会引入 XXE 面（外部实体注入）。
     * 只取文本节点的话，正则既够用又没有这个风险。
     */
    private String readPptxViaRawXml(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        for (List<String> slide : rawPptxSlides(file)) {
            for (String line : slide) content.append(line).append("\n");
        }
        return content.toString().trim();
    }

    /** 限长版本的原始 XML 回退：按页与行数截断，语义与 POI 版本保持一致。 */
    private String readPptxLimitedViaRawXml(File file, int maxSlides, int maxLinesPerSlide)
            throws IOException {
        StringBuilder content = new StringBuilder();
        List<List<String>> slides = rawPptxSlides(file);
        int count = Math.min(maxSlides, slides.size());
        for (int i = 0; i < count; i++) {
            int lineCount = 0;
            for (String line : slides.get(i)) {
                content.append(line).append("\n");
                if (++lineCount >= maxLinesPerSlide) break;
            }
        }
        return content.toString().trim();
    }

    /**
     * 按幻灯片抽 {@code <a:t>} 文本行。
     *
     * @return 外层按 slide 编号升序，内层是该页的文本行
     */
    private static List<List<String>> rawPptxSlides(File file) throws IOException {
        List<List<String>> slides = new ArrayList<>();
        try (ZipFile zip = new ZipFile(file)) {
            List<? extends ZipEntry> entries = zip.stream()
                    .filter(entry -> SLIDE_ENTRY.matcher(entry.getName()).matches())
                    .sorted(Comparator.comparingInt(entry -> slideNumberOf(entry.getName())))
                    .toList();
            for (ZipEntry slide : entries) {
                List<String> lines = new ArrayList<>();
                try (InputStream in = zip.getInputStream(slide)) {
                    String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    Matcher runs = TEXT_RUN.matcher(xml);
                    while (runs.find()) {
                        String text = unescapeXml(runs.group(1)).trim();
                        if (!text.isEmpty()) lines.add(text);
                    }
                }
                slides.add(lines);
            }
        }
        return slides;
    }

    private static int slideNumberOf(String entryName) {
        Matcher m = SLIDE_ENTRY.matcher(entryName);
        if (!m.matches()) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static String unescapeXml(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'")
                // &amp; 必须最后替换，否则 "&amp;lt;" 会被二次解码成 "<"
                .replace("&amp;", "&");
    }

    private String readPptxLimited(File file, int maxSlides, int maxLinesPerSlide) throws IOException {
        try {
            return readPptxLimitedViaPoi(file, maxSlides, maxLinesPerSlide);
        } catch (Exception e) {
            // 与 readPptx 同样的回退：形状 id 溢出会让 POI 整个读不出来
            log.warn("POI 解析 pptx（限长）失败，改用原始 XML 抽取 {}: {}",
                    file.getName(), e.getMessage());
            return readPptxLimitedViaRawXml(file, maxSlides, maxLinesPerSlide);
        }
    }

    private String readPptxLimitedViaPoi(File file, int maxSlides, int maxLinesPerSlide) throws IOException {
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            List<XSLFSlide> slides = ppt.getSlides();
            int count = Math.min(maxSlides, slides.size());
            for (int i = 0; i < count; i++) {
                XSLFSlide slide = slides.get(i);
                int lineCount = 0;
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape ts) {
                        String[] lines = String.valueOf(ts.getText()).split("\\r?\\n");
                        for (String line : lines) {
                            String t = line.trim();
                            if (!t.isEmpty()) {
                                content.append(t).append("\n");
                                lineCount++;
                                if (lineCount >= maxLinesPerSlide) break;
                            }
                        }
                    }
                    if (lineCount >= maxLinesPerSlide) break;
                }
            }
        }
        return content.toString().trim();
    }

    private String readPpt(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             HSLFSlideShow ppt = new HSLFSlideShow(fis)) {
            List<HSLFSlide> slides = ppt.getSlides();
            for (HSLFSlide slide : slides) {
                String title = slide.getTitle();
                if (title != null && !title.trim().isEmpty()) {
                    content.append(title.trim()).append("\n");
                }
                for (var shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape ts) {
                        String text = ts.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            content.append(text.trim()).append("\n");
                        }
                    }
                }
            }
        }
        return content.toString().trim();
    }

    private String readPptLimited(File file, int maxSlides, int maxLinesPerSlide) throws IOException {
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             HSLFSlideShow ppt = new HSLFSlideShow(fis)) {
            List<HSLFSlide> slides = ppt.getSlides();
            int count = Math.min(maxSlides, slides.size());
            for (int i = 0; i < count; i++) {
                HSLFSlide slide = slides.get(i);
                int lineCount = 0;
                String title = slide.getTitle();
                if (title != null && !title.trim().isEmpty()) {
                    content.append(title.trim()).append("\n");
                    lineCount++;
                }
                for (var shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape ts) {
                        String[] lines = String.valueOf(ts.getText()).split("\\r?\\n");
                        for (String line : lines) {
                            String t = line.trim();
                            if (!t.isEmpty()) {
                                content.append(t).append("\n");
                                lineCount++;
                                if (lineCount >= maxLinesPerSlide) break;
                            }
                        }
                    }
                    if (lineCount >= maxLinesPerSlide) break;
                }
            }
        }
        return content.toString().trim();
    }
    
    public String exportHomeworkPPT(Homework homework, Map<String, Object> stats, String logoAbsPath, String templateAbsPath, String style) throws IOException {
        long t0 = System.currentTimeMillis();
        log.info("开始生成 PPT，作业 {}，样式 {}", homework != null ? homework.getHomeworkid() : null, style);
        if (homework == null || homework.getContent() == null) {
            throw new IOException("作业内容为空");
        }
        XMLSlideShow ppt;
        boolean useTemplate = templateAbsPath != null && !templateAbsPath.isEmpty() && new File(templateAbsPath).exists()
                && style != null && "template".equalsIgnoreCase(style);
        if (useTemplate) {
            try (FileInputStream fis = new FileInputStream(templateAbsPath)) {
                ppt = new XMLSlideShow(fis);
            }
        } else {
            ppt = new XMLSlideShow();
        }
        java.awt.Dimension pgsize = ppt.getPageSize();
        
        // 检查是否为文档作业 (Type 2) 或无法解析为JSON
        JsonNode hwNode = null;
        boolean isDocType = (homework.getType() != null && homework.getType() == 2);
        
        if (!isDocType) {
            try {
                hwNode = OBJECT_MAPPER.readTree(homework.getContent());
                if (!hwNode.has("questions") || !hwNode.path("questions").isArray()) {
                    isDocType = true; // 虽然不是Type 2，但没有题目列表，按文档处理
                }
            } catch (Exception e) {
                isDocType = true; // 解析失败，按文档处理
            }
        }

        if (isDocType) {
            return exportDocHomeworkPPT(ppt, homework, stats, style);
        }

        JsonNode questions = hwNode.path("questions");
        if (!questions.isArray()) {
            throw new IOException("题目列表为空");
        }
        
        java.util.Map<String, java.util.Map<String, Object>> perQuestionStats = new java.util.LinkedHashMap<>();
        if (stats != null && stats.get("questions") instanceof List) {
            @SuppressWarnings("unchecked")
            List<java.util.Map<String, Object>> qstats = (List<java.util.Map<String, Object>>) stats.get("questions");
            for (java.util.Map<String, Object> item : qstats) {
                String id = String.valueOf(item.get("id"));
                perQuestionStats.put(id, item);
            }
        }

        boolean useAI = style == null || "ai".equalsIgnoreCase(style);
        // 记录开始时间，用于全局超时控制
        long startTime = System.currentTimeMillis();
        
        // 并发预加载AI内容
        Map<Integer, Future<String>> kpFutures = new ConcurrentHashMap<>();
        Map<Integer, Future<String>> expFutures = new ConcurrentHashMap<>();
        
        if (useAI) {
            for (int i = 0; i < questions.size(); i++) {
                JsonNode q = questions.get(i);
                String title = q.path("title").asText();
                String type = q.path("type").asText();
                java.util.List<String> options = new java.util.ArrayList<>();
                if (q.has("options") && q.get("options").isArray()) {
                    for (JsonNode opt : q.get("options")) {
                        options.add(opt.asText());
                    }
                }
                String correctAnswer = q.has("answer") && !q.get("answer").isNull() ? q.get("answer").toString() : "";
                
                kpFutures.put(i, AI_EXECUTOR.submit(() -> generateKnowledgePoints(title, type, options)));
                expFutures.put(i, AI_EXECUTOR.submit(() -> explainQuestion(title, type, options, correctAnswer)));
            }
        }

        for (int i = 0; i < questions.size(); i++) {
            JsonNode q = questions.get(i);
            String qid = q.path("id").asText();
            String title = q.path("title").asText();
            String type = q.path("type").asText();
            java.util.List<String> options = new java.util.ArrayList<>();
            if (q.has("options") && q.get("options").isArray()) {
                for (JsonNode opt : q.get("options")) {
                    options.add(opt.asText());
                }
            }
            // String correctAnswer = q.has("answer") && !q.get("answer").isNull() ? q.get("answer").toString() : "";
            
            XSLFSlide s1 = ppt.createSlide();
            SlideStyle.addHeaderBar(ppt, s1, homework.getCoursename(), "题目");
            XSLFTextShape box1 = s1.createTextBox();
            box1.setAnchor(new Rectangle2D.Double(60, 120, pgsize.getWidth() - 120, 60));
            SlideStyle.writeTitle(box1, "第" + (i + 1) + "题");
            XSLFTextShape box1b = s1.createTextBox();
            box1b.setAnchor(new Rectangle2D.Double(60, 200, pgsize.getWidth() - 120, 300));
            StringBuilder sb = new StringBuilder();
            sb.append(title);
            if (!options.isEmpty()) {
                sb.append("\n");
                for (int j = 0; j < options.size(); j++) {
                    sb.append((char)('A' + j)).append(". ").append(options.get(j)).append("\n");
                }
            }
            SlideStyle.writeBody(box1b, sb.toString());
            SlideStyle.addSystemName(ppt, s1);
            
            XSLFSlide s2 = ppt.createSlide();
            SlideStyle.addHeaderBar(ppt, s2, homework.getCoursename(), "知识点");
            String kp;
            if (useAI) {
                try {
                    // 计算剩余时间
                    long elapsed = System.currentTimeMillis() - startTime;
                    long remaining = GLOBAL_TIMEOUT_MS - elapsed;
                    if (remaining <= 0) {
                         kp = "生成超时（已自动跳过以确保下载）";
                         // 取消任务以释放资源
                         Future<String> f = kpFutures.get(i);
                         if (f != null) f.cancel(true);
                    } else {
                        // 缩短单项超时时间，避免总耗时过长，保留异常堆栈打印
                        kp = kpFutures.get(i).get(remaining, TimeUnit.MILLISECONDS);
                    }
                } catch (Exception e) {
                    // TimeoutException or other
                    if (!(e instanceof java.util.concurrent.TimeoutException)) {
                        log.warn("第 {} 题的知识点生成失败，将跳过该题", i + 1, e);
                    }
                    kp = "知识点生成超时或失败（请检查网络或稍后重试）";
                }
            } else {
                kp = "知识点：\n- 核心概念\n- 易错点\n- 规律与方法\n- 典型例题";
            }
            XSLFTextShape box2 = s2.createTextBox();
            box2.setAnchor(new Rectangle2D.Double(60, 120, pgsize.getWidth() - 120, 380));
            SlideStyle.writeBullets(box2, SlideStyle.clamp(kp, 12, 38), 12, 18.0);
            SlideStyle.addSystemName(ppt, s2);
            
            XSLFSlide s3 = ppt.createSlide();
            SlideStyle.addHeaderBar(ppt, s3, homework.getCoursename(), "正确率/得分率");
            java.util.Map<String, Object> st = perQuestionStats.getOrDefault(qid, new java.util.HashMap<>());
            int attempts = parseInt(st.get("attempts"));
            int correct = parseInt(st.get("correct"));
            int wrong = parseInt(st.get("wrong"));
            double rate = attempts > 0 ? (double) correct / attempts : 0.0;
            byte[] chart = createRateChart(type, correct, wrong, rate);
            if (chart != null) {
                org.apache.poi.xslf.usermodel.XSLFPictureData picData = ppt.addPicture(chart, PictureData.PictureType.PNG);
                XSLFPictureShape pic = s3.createPicture(picData);
                pic.setAnchor(new Rectangle2D.Double(160, 160, pgsize.getWidth() - 320, 300));
            }
            XSLFTextShape box3 = s3.createTextBox();
            box3.setAnchor(new Rectangle2D.Double(60, 120, pgsize.getWidth() - 120, 40));
            SlideStyle.writeTitle(box3, "正确率/得分率分析");
            SlideStyle.addSystemName(ppt, s3);
            
            XSLFSlide s4 = ppt.createSlide();
            SlideStyle.addHeaderBar(ppt, s4, homework.getCoursename(), "解析");
            String exp;
            if (useAI) {
                try {
                    // 计算剩余时间
                    long elapsed = System.currentTimeMillis() - startTime;
                    long remaining = GLOBAL_TIMEOUT_MS - elapsed;
                    if (remaining <= 0) {
                        exp = "生成超时（已自动跳过以确保下载）";
                        Future<String> f = expFutures.get(i);
                        if (f != null) f.cancel(true);
                    } else {
                        exp = expFutures.get(i).get(remaining, TimeUnit.MILLISECONDS);
                    }
                } catch (Exception e) {
                    if (!(e instanceof java.util.concurrent.TimeoutException)) {
                        log.warn("第 {} 题的解析生成失败，将跳过该题", i + 1, e);
                    }
                    exp = "解析生成超时或失败（请检查网络或稍后重试）";
                }
            } else {
                exp = "解析：\n- 理清题意\n- 关键步骤\n- 通用方法\n- 易错提醒";
            }
            XSLFTextShape box4 = s4.createTextBox();
            box4.setAnchor(new Rectangle2D.Double(60, 120, pgsize.getWidth() - 120, 380));
            SlideStyle.writeBullets(box4, SlideStyle.clamp(exp, 14, 42), 14, 18.0);
            SlideStyle.addSystemName(ppt, s4);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ppt.write(out);
        out.close();
        String rel = fileService.saveBytes(out.toByteArray(), "pptx");
        log.info("题目型 PPT 生成完成，耗时 {} ms，文件 {}", System.currentTimeMillis() - t0, rel);
        return rel;
    }
    

    private int parseInt(Object o) {
        if (o == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }
    
    private byte[] createRateChart(String type, int correct, int wrong, double scoreRate) {
        try {
            int w = 800, h = 500;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (ObjectiveGrading.isObjective(type)) {
                int total = Math.max(1, correct + wrong);
                double angleCorrect = 360.0 * correct / total;
                double angleWrong = 360.0 - angleCorrect;
                int cx = 200, cy = 250, r = 150;
                g.setColor(new Color(76, 175, 80));
                g.fillArc(cx - r, cy - r, 2 * r, 2 * r, 90, (int) -angleCorrect);
                g.setColor(new Color(244, 67, 54));
                g.fillArc(cx - r, cy - r, 2 * r, 2 * r, (int) (90 - angleCorrect), (int) -angleWrong);
                g.setColor(Color.DARK_GRAY);
                g.setFont(new Font(SlideStyle.chartFont(), Font.BOLD, 18));
                g.drawString("正确: " + correct, 400, 220);
                g.drawString("错误: " + wrong, 400, 260);
                g.drawString("正确率: " + String.format("%.2f%%", (double) correct * 100.0 / Math.max(1, correct + wrong)), 400, 300);
            } else {
                int barW = 300;
                int x = 200, y = 220;
                g.setColor(new Color(230, 230, 230));
                g.fillRect(x, y, barW, 40);
                g.setColor(new Color(33, 150, 243));
                int filled = (int) (barW * Math.min(1.0, Math.max(0.0, scoreRate)));
                g.fillRect(x, y, filled, 40);
                g.setColor(Color.DARK_GRAY);
                g.setFont(new Font(SlideStyle.chartFont(), Font.BOLD, 18));
                g.drawString("得分率: " + String.format("%.2f%%", scoreRate * 100.0), x, y - 10);
            }
            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
    
    private String generateKnowledgePoints(String title, String type, java.util.List<String> options) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("题目：").append(title).append("\n");
            if (options != null && !options.isEmpty()) {
                prompt.append("选项：").append(String.join("；", options));
            }
            String system = "请用要点列出与该题相关的核心知识点，5-8条，分条输出，涵盖：核心概念、必备方法/公式、易错点、解题策略、应用示例（简要），每条不超过25字，适合用于讲解PPT。";
            return aiSafeCallService.callText("ppt.knowledgePoints", system, prompt.toString());
        } catch (Exception e) {
            log.warn("PPT 知识点生成失败，改用模板内容: {}", e.getMessage());
            return "知识点生成失败: " + e.getMessage();
        }
    }

    private String explainQuestion(String title, String type, java.util.List<String> options, String correctAnswer) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("题目：").append(title).append("\n");
            if (options != null && !options.isEmpty()) {
                prompt.append("选项：").append(String.join("；", options)).append("\n");
            }
            if (correctAnswer != null && !correctAnswer.isEmpty()) {
                prompt.append("答案：").append(correctAnswer);
            }
            String system = "生成该题的解析，请分条输出，结构包含：题意分析、解题步骤（2-4步）、关键知识点、示例/代码片段（如适用）、常见错误与纠正、总结与课堂提示；每条1-2句，避免空泛，适合PPT展示。";
            return aiSafeCallService.callText("ppt.explainQuestion", system, prompt.toString());
        } catch (Exception e) {
            log.warn("PPT 解析生成失败，改用模板内容: {}", e.getMessage());
            return "解析生成失败: " + e.getMessage();
        }
    }



    private String exportDocHomeworkPPT(XMLSlideShow ppt, Homework homework, Map<String, Object> stats, String style) throws IOException {
        long t0 = System.currentTimeMillis();
        log.info("开始生成文档型 PPT，作业 {}", homework.getHomeworkid());
        java.awt.Dimension pgsize = ppt.getPageSize();
        boolean useAI = style == null || "ai".equalsIgnoreCase(style);

        // 1. 封面页
        XSLFSlide s1 = ppt.createSlide();
        XSLFTextShape titleBox = s1.createTextBox();
        titleBox.setAnchor(new Rectangle2D.Double(60, 200, pgsize.getWidth() - 120, 100));
        SlideStyle.writeTitle(titleBox, homework.getTitle());
        
        XSLFTextShape subBox = s1.createTextBox();
        subBox.setAnchor(new Rectangle2D.Double(60, 320, pgsize.getWidth() - 120, 50));
        var p = subBox.addNewTextParagraph();
        var r = p.addNewTextRun();
        r.setText("作业讲解与指导");
        r.setFontFamily("Microsoft YaHei");
        r.setFontSize(24.0);
        r.setFontColor(Color.GRAY);
        SlideStyle.addSystemName(ppt, s1);

        // 2. 作业要求页
        XSLFSlide s2 = ppt.createSlide();
        SlideStyle.addHeaderBar(ppt, s2, homework.getCoursename(), "作业要求");
        XSLFTextShape reqBox = s2.createTextBox();
        reqBox.setAnchor(new Rectangle2D.Double(60, 120, pgsize.getWidth() - 120, 380));
        
        String cleanContent = stripHtml(homework.getContent());
        SlideStyle.writeBullets(reqBox, SlideStyle.clamp(cleanContent, 12, 40), 12, 18.0);
        SlideStyle.addSystemName(ppt, s2);

        // 3. AI指导页
        XSLFSlide s3 = ppt.createSlide();
        SlideStyle.addHeaderBar(ppt, s3, homework.getCoursename(), "作业指导");
        String guide;
        if (useAI) {
            try {
                guide = AI_EXECUTOR.submit(() -> generateDocGuide(homework.getTitle(), cleanContent)).get(GLOBAL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                 guide = "指导生成超时或失败，请参考作业要求完成。";
            }
        } else {
            guide = "作业指导：\n- 请仔细阅读要求\n- 注意提交截止时间\n- 格式要求请参照文档";
        }
        XSLFTextShape guideBox = s3.createTextBox();
        guideBox.setAnchor(new Rectangle2D.Double(60, 120, pgsize.getWidth() - 120, 380));
        SlideStyle.writeBullets(guideBox, SlideStyle.clamp(guide, 12, 40), 12, 18.0);
        SlideStyle.addSystemName(ppt, s3);

        // 4. 提交统计页 (如果有数据)
        if (stats != null) {
            XSLFSlide s4 = ppt.createSlide();
            SlideStyle.addHeaderBar(ppt, s4, homework.getCoursename(), "提交情况");
            
            int submitCount = parseInt(stats.get("submitCount"));
            int totalCount = parseInt(stats.get("totalCount"));
            int unSubmitCount = Math.max(0, totalCount - submitCount);
            double rate = totalCount > 0 ? (double) submitCount / totalCount : 0.0;
            
            byte[] chart = createSubmitChart(submitCount, unSubmitCount, rate);
            if (chart != null) {
                org.apache.poi.xslf.usermodel.XSLFPictureData picData = ppt.addPicture(chart, PictureData.PictureType.PNG);
                XSLFPictureShape pic = s4.createPicture(picData);
                pic.setAnchor(new Rectangle2D.Double(160, 160, pgsize.getWidth() - 320, 300));
            }
            
            XSLFTextShape statBox = s4.createTextBox();
            statBox.setAnchor(new Rectangle2D.Double(60, 120, pgsize.getWidth() - 120, 40));
            SlideStyle.writeTitle(statBox, "提交概况");
            SlideStyle.addSystemName(ppt, s4);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ppt.write(out);
        out.close();
        String rel = fileService.saveBytes(out.toByteArray(), "pptx");
        log.info("文档型 PPT 生成完成，耗时 {} ms，文件 {}", System.currentTimeMillis() - t0, rel);
        return rel;
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", "").replaceAll("&nbsp;", " ").trim();
    }

    private String generateDocGuide(String title, String content) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("作业标题：").append(title).append("\n");
            prompt.append("作业要求：").append(content.length() > 500 ? content.substring(0, 500) + "..." : content).append("\n");
            String system = "你是一位资深的教育助教。请根据作业要求，为学生生成一份作业指导大纲，适合PPT展示。请侧重于：1.作业目标 2.核心难点提示 3.推荐资源或方向。请不要包含评分标准（将单独生成）。分条输出，每条不超过30字。";
            return aiSafeCallService.callText("ppt.docGuide", system, prompt.toString());
        } catch (Exception e) {
            log.warn("PPT 指导内容生成失败，改用模板内容: {}", e.getMessage());
            return "指导生成失败: " + e.getMessage();
        }
    }

    private String generateDocGradingCriteria(String title, String content) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("作业标题：").append(title).append("\n");
            prompt.append("作业要求：").append(content.length() > 500 ? content.substring(0, 500) + "..." : content).append("\n");
            String system = "你是一位资深的教育助教。请根据作业要求，为这份作业量身定制一份详细的评分标准（Rubric）。请避免使用通用的套话，而是结合作业的具体主题和要求来设定评价维度。\n请输出3-5个评分维度，包含：\n1. 维度名称与分值占比。\n2. 该维度下的具体得分点或扣分点（例如：是否准确运用了XX理论，数据分析是否详尽等）。\n输出格式适合PPT展示，条理清晰，每条说明控制在45字以内。\n格式示例：\n- 核心观点 (30%): 论点明确，能准确运用SWOT模型进行分析，论据充分。";
            return aiSafeCallService.callText("ppt.gradingCriteria", system, prompt.toString());
        } catch (Exception e) {
            log.warn("PPT 评分标准生成失败，改用模板内容: {}", e.getMessage());
            return "评分标准生成失败: " + e.getMessage();
        }
    }

    private byte[] createSubmitChart(int submitted, int unsubmitted, double rate) {
        try {
            int w = 800, h = 500;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int total = Math.max(1, submitted + unsubmitted);
            double angleSubmit = 360.0 * submitted / total;
            double angleUnsubmit = 360.0 - angleSubmit;
            int cx = 200, cy = 250, r = 150;
            
            g.setColor(new Color(33, 150, 243)); // Blue for submitted
            g.fillArc(cx - r, cy - r, 2 * r, 2 * r, 90, (int) -angleSubmit);
            g.setColor(new Color(224, 224, 224)); // Grey for unsubmitted
            g.fillArc(cx - r, cy - r, 2 * r, 2 * r, (int) (90 - angleSubmit), (int) -angleUnsubmit);
            
            g.setColor(Color.DARK_GRAY);
            g.setFont(new Font(SlideStyle.chartFont(), Font.BOLD, 18));
            g.drawString("已提交: " + submitted, 400, 220);
            g.drawString("未提交: " + unsubmitted, 400, 260);
            g.drawString("提交率: " + String.format("%.2f%%", rate * 100.0), 400, 300);
            
            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}

