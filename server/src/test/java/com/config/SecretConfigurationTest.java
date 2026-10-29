package com.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 检查敏感字段
 */
class SecretConfigurationTest {

    /**
     * 禁止提交 OpenAI 兼容格式密钥
     * 禁止提交明文敏感字段
     */
    private static final Pattern OPENAI_STYLE_KEY =
            Pattern.compile("sk-[A-Za-z0-9_-]{16,}");
    private static final Pattern HARDCODED_SECRET_FIELD = Pattern.compile(
            "(?m)^[ \\t]*(?:api-key|api-secret|password):[ \\t]*(?!\\$\\{)[^#\\s].+$");

    /**
     * 禁止提交明文敏感字段
     */
    @Test
    void sharedApplicationConfigContainsNoHardcodedSecrets() throws Exception {
        String config = Files.readString(
                Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);

        assertFalse(OPENAI_STYLE_KEY.matcher(config).find(),
                "application.yml 中禁止提交 OpenAI 兼容格式密钥");
        assertFalse(HARDCODED_SECRET_FIELD.matcher(config).find(),
                "application.yml 中的敏感字段必须通过环境变量注入");
    }
}
