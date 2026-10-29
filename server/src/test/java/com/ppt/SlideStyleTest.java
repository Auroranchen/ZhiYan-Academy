package com.ppt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 幻灯片文本裁剪。放不下的内容会溢出到页面外，导出的 PPT 直接不可用，
 * 而这在服务端生成时看不出来，只有打开文件才发现。
 */
class SlideStyleTest {

    @Test
    @DisplayName("超出行数的内容被丢弃")
    void dropsExtraLines() {
        String clamped = SlideStyle.clamp("一\n二\n三\n四\n五", 3, 100);

        assertEquals("一\n二\n三", clamped);
    }

    @Test
    @DisplayName("过长的行截断并加省略号")
    void truncatesLongLines() {
        String clamped = SlideStyle.clamp("abcdefghij", 5, 6);

        assertEquals("abcde…", clamped);
        assertEquals(6, clamped.length(), "截断后总长不应超过上限");
    }

    @Test
    @DisplayName("空行不占用行数配额")
    void skipsBlankLines() {
        assertEquals("一\n二", SlideStyle.clamp("一\n\n   \n二", 2, 100));
    }

    @Test
    @DisplayName("去掉模型偶尔套上的 markdown 代码块标记")
    void stripsMarkdownFence() {
        String clamped = SlideStyle.clamp("```python\nprint(1)\n```", 5, 100);

        assertEquals("print(1)", clamped);
    }

    @Test
    @DisplayName("兼容 Windows 换行")
    void handlesCrlf() {
        assertEquals("一\n二", SlideStyle.clamp("一\r\n二", 5, 100));
    }

    @Test
    @DisplayName("空输入返回空串而不是 null，调用方不必再判空")
    void nullBecomesEmpty() {
        assertEquals("", SlideStyle.clamp(null, 3, 10));
        assertEquals("", SlideStyle.clamp("   ", 3, 10));
    }

    @Test
    @DisplayName("图表字体一定能解析出可用值，否则中文会渲染成方框")
    void chartFontAlwaysResolves() {
        String font = SlideStyle.chartFont();

        assertNotNull(font);
        assertFalse(font.isBlank());
    }
}
