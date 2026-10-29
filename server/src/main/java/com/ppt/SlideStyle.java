package com.ppt;

import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 幻灯片排版。
 *
 * 字体、配色、文本框写入原先重复出现在 PPTService 的十来个方法里，
 * 每处都要重来一遍 clearText → addNewTextParagraph → addNewTextRun → 设字体设颜色。
 * 视觉规范集中在这里，PPTService 只负责编排内容。
 */
public final class SlideStyle {

    private SlideStyle() {
    }

    /* ==================== 视觉规范 ==================== */

    public static final Color BRAND = new Color(24, 100, 171);
    public static final Color TITLE_TEXT = new Color(51, 51, 51);
    public static final Color BODY_TEXT = new Color(66, 66, 66);
    public static final Color ON_BRAND = Color.WHITE;
    public static final Color ACCENT_SOFT = new Color(180, 220, 255);
    public static final Color ACCENT_BRIGHT = new Color(230, 245, 255);

    /** 完全透明，用于去掉自动图形的默认描边 */
    public static final Color TRANSPARENT = new Color(255, 255, 255, 0);

    public static final String HEADER_BAR_HEIGHT_NOTE = "顶栏高度与标题纵向位置绑定，改一个要同时改另一个";
    public static final double HEADER_BAR_HEIGHT = 80;

    private static final double HEADER_FONT_SIZE = 20.0;
    private static final double TITLE_FONT_SIZE = 24.0;
    private static final double BODY_FONT_SIZE = 18.0;

    /** 正文优先用微软雅黑；图表另走 chartFont()，因为服务器上不一定装了这套字体 */
    private static final String TEXT_FONT = "Microsoft YaHei";

    public static final String SYSTEM_NAME = "智学云课堂";

    /* ==================== 字体探测 ==================== */

    /** 按可用性依次降级，最后回落到 JDK 一定有的 SansSerif */
    private static final List<String> CJK_FONT_CANDIDATES = List.of(
            "Microsoft YaHei", "SimHei", "WenQuanYi Micro Hei",
            "WenQuanYi Zen Hei", "Noto Sans CJK SC", "Droid Sans Fallback", "SansSerif");

    private static final String CHART_FONT = detectChartFont();

    /**
     * 图表由 JFreeChart 在服务端渲染成图片，用不存在的字体会画出方框。
     * 结果缓存为常量，字体列表在进程生命周期内不会变。
     */
    public static String chartFont() {
        return CHART_FONT;
    }

    private static String detectChartFont() {
        Set<String> installed = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        return CJK_FONT_CANDIDATES.stream().filter(installed::contains).findFirst().orElse("SansSerif");
    }

    /* ==================== 文本写入 ==================== */

    /** 页眉：白字，衬在品牌色横条上 */
    public static void writeHeader(XSLFTextShape box, String text) {
        writeSingleLine(box, text, HEADER_FONT_SIZE, ON_BRAND, true);
    }

    public static void writeTitle(XSLFTextShape box, String text) {
        writeSingleLine(box, text, TITLE_FONT_SIZE, TITLE_TEXT, true);
    }

    /** 正文：按换行拆成多个段落，POI 不会自动处理 \n */
    public static void writeBody(XSLFTextShape box, String text) {
        box.clearText();
        for (String line : splitLines(text)) {
            var run = box.addNewTextParagraph().addNewTextRun();
            run.setText(line);
            run.setFontFamily(TEXT_FONT);
            run.setFontSize(BODY_FONT_SIZE);
            run.setFontColor(BODY_TEXT);
        }
    }

    /**
     * 项目符号列表。以「-」开头的行渲染成带符号的条目。
     *
     * @param maxLines 超出的行直接丢弃——幻灯片放不下，截断比溢出好
     */
    public static void writeBullets(XSLFTextShape box, String text, int maxLines, double fontSize) {
        box.clearText();
        int written = 0;

        for (String line : splitLines(text)) {
            if (written >= maxLines) break;

            String content = line.trim();
            if (content.isEmpty()) continue;

            var paragraph = box.addNewTextParagraph();
            if (content.startsWith("-")) {
                content = content.substring(1).trim();
                paragraph.setBullet(true);
            }

            var run = paragraph.addNewTextRun();
            run.setText(content);
            run.setFontFamily(TEXT_FONT);
            run.setFontSize(fontSize);
            run.setFontColor(BODY_TEXT);
            written++;
        }
    }

    private static void writeSingleLine(XSLFTextShape box, String text, double size, Color color, boolean bold) {
        box.clearText();
        var run = box.addNewTextParagraph().addNewTextRun();
        run.setText(text);
        run.setFontFamily(TEXT_FONT);
        run.setFontSize(size);
        run.setBold(bold);
        run.setFontColor(color);
    }

    private static String[] splitLines(String text) {
        return String.valueOf(text).split("\\r?\\n");
    }

    /* ==================== 版面元素 ==================== */

    /** 每页顶部的品牌色横条与页眉文字 */
    public static void addHeaderBar(XMLSlideShow ppt, XSLFSlide slide, String courseName, String section) {
        Dimension page = ppt.getPageSize();

        XSLFAutoShape bar = slide.createAutoShape();
        bar.setShapeType(ShapeType.RECT);
        bar.setAnchor(new Rectangle2D.Double(0, 0, page.getWidth(), HEADER_BAR_HEIGHT));
        bar.setLineColor(TRANSPARENT);
        bar.setFillColor(BRAND);

        XSLFTextShape title = slide.createTextBox();
        title.setAnchor(new Rectangle2D.Double(20, 20, page.getWidth() - 40, 40));
        String prefix = courseName != null && !courseName.isEmpty() ? courseName + " · " : "";
        writeHeader(title, prefix + section);
    }

    /** 右上角的系统署名，装饰性内容，失败不影响导出 */
    public static void addSystemName(XMLSlideShow ppt, XSLFSlide slide) {
        try {
            Dimension page = ppt.getPageSize();
            XSLFTextShape box = slide.createTextBox();
            box.setAnchor(new Rectangle2D.Double(page.getWidth() - 240, 14, 220, 52));
            box.clearText();

            var paragraph = box.addNewTextParagraph();
            paragraph.setTextAlign(TextParagraph.TextAlign.RIGHT);

            var left = paragraph.addNewTextRun();
            left.setText("◆ ");
            left.setFontSize(10.0);
            left.setFontColor(ACCENT_SOFT);

            var name = paragraph.addNewTextRun();
            name.setText(SYSTEM_NAME);
            name.setFontFamily("STXingkai");
            name.setFontSize(18.0);
            name.setBold(true);
            name.setItalic(true);
            name.setFontColor(ACCENT_BRIGHT);

            var right = paragraph.addNewTextRun();
            right.setText(" ◆");
            right.setFontSize(10.0);
            right.setFontColor(ACCENT_SOFT);
        } catch (Exception ignored) {
            // 署名是装饰，缺了不影响 PPT 可用
        }
    }

    /* ==================== 文本裁剪 ==================== */

    /**
     * 把 AI 生成的文本裁到幻灯片放得下的尺寸。
     * 模型偶尔会把内容包在 markdown 代码块里，这里一并去掉。
     */
    public static String clamp(String text, int maxLines, int maxCharsPerLine) {
        if (text == null) return "";

        String cleaned = text.replace("```python", "").replace("```", "");
        StringBuilder result = new StringBuilder();
        int lines = 0;

        for (String line : splitLines(cleaned)) {
            String content = line.trim();
            if (content.isEmpty()) continue;

            if (content.length() > maxCharsPerLine) {
                content = content.substring(0, maxCharsPerLine - 1) + "…";
            }
            if (lines > 0) result.append('\n');
            result.append(content);

            if (++lines >= maxLines) break;
        }
        return result.toString();
    }
}
