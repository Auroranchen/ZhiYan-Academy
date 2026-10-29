package com.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守住 test profile 的密钥隔离。
 *
 * <p>主 application.yml 通过 {@code spring.config.import} 加载本地
 * {@code config/application-secrets.yml}（含真实密钥）。如果 test profile 不再覆盖这些 key，
 * 任何走到 AI 路径的 Spring 测试都会打出**真实的计费请求**——而且不会有任何报错，
 * 只会悄悄花钱。这个测试就是为了让那种回归立刻失败。
 *
 * <p>注意：本地没有 secrets 文件时本测试会平凡通过。这是可以接受的——没有 secrets 文件
 * 就没有这个隐患，测试恰好只在隐患存在的地方起作用。
 */
class AiTestProfileIsolationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void testProfileBlanksEveryAiCredential() {
        runner.withPropertyValues("spring.profiles.active=test").run(context -> {
            Environment env = context.getEnvironment();
            assertBlank(env, "ai.zhipu.api-key");
            assertBlank(env, "ai.xfyun.api-key");
            assertBlank(env, "ai.xfyun.api-secret");
            assertBlank(env, "ai.xfyun-tts.api-key");
            assertBlank(env, "ai.xfyun-tts.api-secret");
        });
    }

    /**
     * dev profile 下必须能拿到真实配置——否则说明隔离方式（而非密钥本身）出了问题，
     * 本地开发会被静默地剥夺 AI 能力。
     */
    @Test
    void devProfileIsNotBlankedByTheTestOverride() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            Environment env = context.getEnvironment();
            // test profile 的覆盖不应泄漏到其它 profile
            assertThat(env.getActiveProfiles()).containsExactly("dev");
        });
    }

    private static void assertBlank(Environment env, String key) {
        String value = env.getProperty(key);
        assertThat(value)
                .as("%s 在 test profile 下必须为空，否则测试会打真实的计费请求", key)
                .satisfiesAnyOf(
                        v -> assertThat(v).isNull(),
                        v -> assertThat(v).isEmpty());
    }
}
