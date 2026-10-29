package com.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 结构护栏：泛型 LLM 入口只允许被安全网关调用。
 *
 * <p>背景：改造前有 22 处调用直接绕过 {@code AiSafeCallService}，后果是既不在
 * {@code ai_call_log} 里留痕（管理端的 AI 监控面板数字因此失真），也没有输入清洗与
 * 输出过滤。这类问题靠人工审查很难守住——加个新功能时顺手调一下是最自然的写法。
 *
 * <p>所以这里用一个 30 行的结构断言把它变成**编译不过一样的硬约束**：
 * {@code callPublic} / {@code callOnce} / {@code callStream} 出现
 * {@code ZhipuAIService} 与 {@code AiSafeCallService} 之外即为失败。
 *
 * <p><b>覆盖边界（重要）：</b>本测试只管这三个泛型入口。
 * {@code ZhipuAIService} 上还有一批领域方法（{@code gradeHomework} /
 * {@code explainQuestion} / {@code generateQuestions} / {@code analyzeWrongAnswer} 等）
 * 仍被直接调用——它们把提示词构造与策略处理封在 {@code ZhipuAIService} 内部，
 * 收口的前提是先把这些逻辑拆出来（计划里的 F2）。届时它们的调用点应一并迁移，
 * 并把这些方法名加入下面的清单。
 */
class AiGatewayBypassTest {

    /** 仅这两个类可以直呼泛型入口：一个定义它们，一个网关封装它们。 */
    private static final Set<String> ALLOWED_FILES = Set.of(
            "ZhipuAIService.java",
            "AiSafeCallService.java");

    /** 泛型入口：一旦绕过它们，就等于绕过了日志、输入清洗与输出过滤。 */
    private static final List<String> FORBIDDEN_CALLS = List.of(
            "callPublic(",
            "callOnce(",
            "callStream(");

    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    @Test
    @DisplayName("除安全网关外，任何类都不得直呼 LLM 泛型入口")
    void genericLlmEntryPointsAreOnlyReachableThroughTheGateway() throws Exception {
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (ALLOWED_FILES.contains(file.getFileName().toString())) continue;
                collectViolations(file, violations);
            }
        }

        assertThat(violations)
                .as("以下调用绕过了 AiSafeCallService：它们不会写入 ai_call_log，" +
                        "也不经过输入清洗与输出过滤。请改走 callText / callJson / callJsonRaw / callStreamText。")
                .isEmpty();
    }

    private static void collectViolations(Path file, List<String> violations) throws Exception {
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            // 跳过注释行，避免文档里提到方法名就被判违规
            if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) continue;
            for (String forbidden : FORBIDDEN_CALLS) {
                if (trimmed.contains(forbidden)) {
                    violations.add(file + ":" + (i + 1) + "  " + trimmed);
                }
            }
        }
    }

    @Test
    @DisplayName("网关自身确实提供了这些入口的替代品（防止有人删掉方法让测试空转）")
    void gatewayExposesReplacementEntryPoints() {
        Set<String> methods = java.util.Arrays.stream(AiSafeCallService.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(methods)
                .contains("callText", "callJson", "callJsonRaw", "callStreamText");
    }
}
