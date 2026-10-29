package com.eval;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 评测用的密钥读取。
 *
 * <p>顺序：环境变量 → 本地 gitignored 的 {@code config/application-secrets.yml}。
 * 刻意不引入 YAML 解析器：评测是 test 作用域的独立工具，只为取一个值而拖进依赖不划算。
 * 这里按缩进定位 {@code app.rag.embedding.api-key}，够用且无副作用。
 *
 * <p>取不到就返回空串，由调用方决定是回落到本地向量器还是明确失败——
 * 绝不返回一个占位值，那会让评测跑出「看起来成功但用的不是真 embedding」的结果。
 */
final class EvalSecrets {

    private static final Path SECRETS = Path.of("config/application-secrets.yml");

    private EvalSecrets() {
    }

    /** 智谱向量化 Key。注意与 AI_API_KEY（DeepSeek）不是同一个。 */
    static String zhipuEmbeddingKey() {
        String fromEnv = System.getenv("ZHIPU_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv.trim();
        return fromSecretsFile();
    }

    static boolean hasZhipuEmbeddingKey() {
        return !zhipuEmbeddingKey().isBlank();
    }

    private static String fromSecretsFile() {
        if (!Files.exists(SECRETS)) return "";
        try {
            List<String> lines = Files.readAllLines(SECRETS, StandardCharsets.UTF_8);
            boolean inApp = false;
            boolean inRag = false;
            boolean inEmbedding = false;

            for (String raw : lines) {
                String line = stripComment(raw);
                if (line.isBlank()) continue;
                int indent = indentOf(line);
                String trimmed = line.trim();

                if (indent == 0) {
                    inApp = trimmed.startsWith("app:");
                    inRag = false;
                    inEmbedding = false;
                    continue;
                }
                if (!inApp) continue;

                if (indent == 2) {
                    inRag = trimmed.startsWith("rag:");
                    inEmbedding = false;
                    continue;
                }
                if (inRag && indent == 4) {
                    inEmbedding = trimmed.startsWith("embedding:");
                    continue;
                }
                if (inRag && inEmbedding && trimmed.startsWith("api-key:")) {
                    return unquote(trimmed.substring("api-key:".length()).trim());
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return i;
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
