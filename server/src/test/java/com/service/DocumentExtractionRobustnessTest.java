package com.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 文档抽取的两类健壮性问题。
 *
 * <p>两者共同的特征是**不报错、只是丢掉正文**：抽取失败会被上游 catch 住
 * （见 {@code CourseKnowledgeContextService.extract}），然后静默降级成一句资料描述。
 * 老师不会收到任何提示，只会发现"我传的资料 AI 好像读不到"。
 *
 * <ol>
 *   <li><b>扩展名与内容不符</b>：语料里有两个 223 字节的 {@code .docx}，
 *       开头是 UTF-8 的「这」而不是 OOXML 的 {@code PK}——纯文本被改名成了 .docx。</li>
 *   <li><b>POI 的形状 id 溢出</b>：{@code XSLFSheet.registerShapeId} 把形状 id 强转成
 *       {@code int} 存进 SparseBitSet，遇到超出 int 范围的 id（实测 2147484766）会抛
 *       {@code IndexOutOfBoundsException: i=-2147482530}。文件本身完全合法，
 *       正文就在 slide XML 里。</li>
 * </ol>
 */
class DocumentExtractionRobustnessTest {

    @TempDir
    Path tempDir;

    private final WordService wordService = new WordService();

    // ---------- 魔数识别 ----------

    @Test
    @DisplayName("OOXML 与 OLE2 的魔数识别")
    void recognisesContainerMagicBytes() {
        assertThat(WordService.isZipContainer(new byte[]{'P', 'K', 0x03, 0x04})).isTrue();
        assertThat(WordService.isOle2Compound(
                new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0})).isTrue();

        // 纯文本与 PDF 都不是 Office 容器
        assertThat(WordService.isZipContainer("这是".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(WordService.isOle2Compound("%PDF".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(WordService.isZipContainer(new byte[0])).isFalse();
        assertThat(WordService.isOle2Compound(new byte[]{1, 2})).isFalse();
    }

    // ---------- 扩展名与内容不符 ----------

    @Test
    @DisplayName("纯文本被改名成 .docx 时按文本读出，而不是抛 NotOfficeXmlFileException")
    void plainTextMasqueradingAsDocxIsStillReadable() throws Exception {
        String body = "这是一个测试文档，用于测试文件上传功能。学生信息：学号：2023001001";
        Path fake = tempDir.resolve("mislabeled.docx");
        Files.writeString(fake, body, StandardCharsets.UTF_8);

        String text = wordService.readWordContent(fake.toString());

        assertThat(text).contains("2023001001");
        assertThat(text).contains("测试文档");
    }

    @Test
    @DisplayName("按内容分派后，真正损坏的文件仍应抛错而不是静默返回空")
    void trulyBrokenFileStillRaises() throws Exception {
        // 有 OOXML 魔数但内部不是合法 zip / 缺 content types —— 该失败就得失败
        Path broken = tempDir.resolve("broken.docx");
        Files.write(broken, new byte[]{'P', 'K', 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0});

        // 注意断言的是 Exception 而非 IOException：POI 的 NotOfficeXmlFileException
        // 继承自 RuntimeException，**不是** IOException 的子类。也就是说
        // readWordContent 声明的 throws IOException 并不可靠——写 catch (IOException)
        // 的调用方接不住这类失败。当前唯一的调用方 CourseKnowledgeContextService
        // 用的是 catch (Exception) 才侥幸没出事。
        // 本次不改这个契约（会牵动所有调用方的异常语义），仅在此记录。
        assertThrows(Exception.class, () -> wordService.readWordContent(broken.toString()));
    }

    // ---------- POI 形状 id 溢出 ----------

    @Test
    @DisplayName("含超范围形状 id 的 pptx 仍能抽出正文（走原始 XML 回退）")
    void pptxWithOutOfRangeShapeIdStillYieldsText() throws Exception {
        Optional<Path> pathological = findPptxThatBreaksPoi();
        assumeTrue(pathological.isPresent(),
                "本地没有能触发 POI 形状 id 溢出的 pptx 样本，跳过");

        String text = new PPTService().readPPTContent(pathological.get().toString());

        assertThat(text)
                .as("回退路径必须能拿到正文，否则整份资料会静默丢成一句描述")
                .isNotBlank();
        assertThat(text.length())
                .as("这类文件实测含数千字正文，过短说明回退没真正读到内容")
                .isGreaterThan(500);
    }

    @Test
    @DisplayName("限长路径（教师出题用）遇到同一个文件也不该失败")
    void limitedPathAlsoSurvivesTheSameFile() throws Exception {
        Optional<Path> pathological = findPptxThatBreaksPoi();
        assumeTrue(pathological.isPresent(), "本地没有病态 pptx 样本，跳过");

        String text = new PPTService().readLimitedContent(pathological.get().toString(), 20, 12);

        assertThat(text).isNotBlank();
        // 上限生效：20 页 × 12 行，单行通常几十字，远小于全文
        assertThat(text.split("\n").length).isLessThanOrEqualTo(20 * 12);
    }

    /**
     * 找一个「POI 直解会崩、但原始 XML 回退能读」的 pptx。
     * 找不到就跳过——这个测试的价值在于针对真实的病态样本，而非构造出来的假样本。
     */
    private static Optional<Path> findPptxThatBreaksPoi() throws IOException {
        Path upload = Path.of("upload");
        if (!Files.isDirectory(upload)) return Optional.empty();
        try (Stream<Path> files = Files.walk(upload)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".pptx")).toList()) {
                try {
                    Files.size(p);
                } catch (IOException e) {
                    continue;
                }
                // 只看那批已知会崩的文件：POI 直解抛异常，回退能读
                if (p.getFileName().toString().startsWith("20260105_90cba")) return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
