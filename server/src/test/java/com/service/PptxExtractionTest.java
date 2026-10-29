package com.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 课程语料可读性的两个前提。
 *
 * <p>背景：{@code CourseKnowledgeContextService.extract} 原先只认
 * {@code .pdf/.doc/.docx}，{@code .ppt/.pptx} 会**静默落到「描述」分支**——不抛异常、
 * 不记错误，只是永远读不到正文。而 {@code upload/} 下 45MB 的真实语料里绝大部分是 pptx，
 * 于是这批资料在检索里等同于不存在。这属于「不报错的错」，只能靠测试盯。
 *
 * <p>第二件事是体积下界：{@code uploads/resources/} 里曾有 10 个文件是 4 字节、
 * 内容就是字面量 {@code test} 的占位物。它们能通过原来的「不超过上限」检查，
 * 会被切成向量塞进检索索引。
 */
class PptxExtractionTest {

    private static final Path UPLOAD_DIR = Path.of("upload");
    private static final Path STUB_DIR = Path.of("uploads/resources");

    @Test
    @DisplayName("真实的 pptx 能被抽出非空正文（而不是落到描述分支）")
    void realPptxYieldsExtractableText() throws Exception {
        Optional<Path> sample = findRealPptx();
        assumeTrue(sample.isPresent(), "本地没有可用的真实 pptx 样本，跳过");

        String text = new PPTService().readPPTContent(sample.get().toString());

        assertThat(text)
                .as("pptx 抽取结果不应为空——为空说明仍走了描述降级")
                .isNotBlank();
        assertThat(text.length())
                .as("抽出的正文至少应有几百字符，过短说明只拿到了文件名之类的东西")
                .isGreaterThan(200);
    }

    @Test
    @DisplayName("4 字节占位文件被体积下界挡掉")
    void stubFilesAreRejectedBySizeFloor() throws Exception {
        assumeTrue(Files.isDirectory(STUB_DIR), "本地没有 uploads/resources，跳过");

        try (Stream<Path> files = Files.list(STUB_DIR)) {
            long stubCount = files.filter(Files::isRegularFile)
                    .filter(PptxExtractionTest::isTiny)
                    .count();
            assertThat(stubCount)
                    .as("该目录应当仍有 4 字节占位文件，否则这个测试失去意义")
                    .isPositive();
        }

        assertThat(CourseKnowledgeContextService.hasUsableSize(4))
                .as("4 字节占位文件必须被判为不可用")
                .isFalse();
        assertThat(CourseKnowledgeContextService.hasUsableSize(51))
                .as("短小的合法内容不应被误伤——门槛针对的是占位文件，不是文档长度规范")
                .isTrue();
        assertThat(CourseKnowledgeContextService.hasUsableSize(9_216))
                .as("正常大小的文档必须通过")
                .isTrue();
        assertThat(CourseKnowledgeContextService.hasUsableSize(20L * 1024 * 1024))
                .as("超过上限的仍应被拒")
                .isFalse();
    }

    private static boolean isTiny(Path path) {
        try {
            return Files.size(path) < 64;
        } catch (IOException e) {
            return false;
        }
    }

    private static Optional<Path> findRealPptx() throws IOException {
        if (!Files.isDirectory(UPLOAD_DIR)) return Optional.empty();
        try (Stream<Path> files = Files.walk(UPLOAD_DIR)) {
            return files.filter(p -> p.toString().toLowerCase().endsWith(".pptx"))
                    .filter(p -> !isTiny(p))
                    .findFirst();
        }
    }
}
