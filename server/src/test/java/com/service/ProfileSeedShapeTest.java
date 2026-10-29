package com.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守住演示种子的画像 JSON 形状。
 *
 * <p>背景：{@code zhzyai.sql} 里两条 {@code student_profile} 种子曾经用的是
 * {@code learningStyle / pace / preferredTime / interests / goals / weakPoints / strengths}
 * 这套 key，而代码读写的是带 {@code dimensions} 外层、内含六维的另一种结构，两边**零重叠**。
 *
 * <p>危险之处在于它**不报错**：{@code LearningProfileService.getProfile} 会把整个
 * profile_json 当成 dimensions 塞进响应，前端再按六维 key 取值取不到，于是静默落到
 * {@code score=0 / label="待完善"}——表现是「画像雷达全零」，而不是任何异常。
 * 这类问题只能靠测试盯。
 *
 * <p>这里直接从 {@code LearningProfileService.DIMENSIONS} 取权威 key 集合，
 * 再解析 SQL 文本里的真实 JSON 做比对，避免两边任何一侧单独漂移。
 */
class ProfileSeedShapeTest {

    private static final Path SEED_SQL = Path.of("zhzyai.sql");

    /** 匹配 INSERT 行里的 (student_id, '<profile_json>' 部分 */
    private static final Pattern PROFILE_ROW = Pattern.compile(
            "INSERT INTO `student_profile` VALUES \\((\\d+), '(\\{.*?\\})', '",
            Pattern.DOTALL);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("种子画像的六维 key 与代码契约完全一致")
    void seededProfileKeysMatchTheCodeContract() throws Exception {
        Set<String> expectedKeys = Arrays.stream(LearningProfileService.DIMENSIONS)
                .map(d -> d[0])
                .collect(Collectors.toSet());

        Matcher matcher = PROFILE_ROW.matcher(readSeedSql());
        int checked = 0;
        while (matcher.find()) {
            String studentId = matcher.group(1);
            // SQL 单引号串里的 \" 就是字面量双引号
            String json = matcher.group(2).replace("\\\"", "\"");
            JsonNode dimensions = MAPPER.readTree(json).path("dimensions");

            assertThat(dimensions.isObject())
                    .as("学生 %s 的画像必须包一层 dimensions（前端按 dimensions[key] 取值）", studentId)
                    .isTrue();

            Set<String> actualKeys = new java.util.HashSet<>();
            dimensions.fieldNames().forEachRemaining(actualKeys::add);
            assertThat(actualKeys)
                    .as("学生 %s 的六维 key", studentId)
                    .isEqualTo(expectedKeys);

            dimensions.forEach(dim -> assertThat(dim.hasNonNull("score") && dim.hasNonNull("label"))
                    .as("维度 %s 必须带 score 与 label，否则前端雷达没有数值可画", dim.path("name").asText())
                    .isTrue());
            checked++;
        }

        assertThat(checked)
                .as("没有在 %s 里找到任何 student_profile 种子行——解析逻辑可能已经失效", SEED_SQL)
                .isPositive();
    }

    @Test
    @DisplayName("种子存在且分数不是全零（否则雷达仍是平的）")
    void seededScoresAreActuallyPopulated() throws Exception {
        Matcher matcher = PROFILE_ROW.matcher(readSeedSql());
        boolean sawNonZero = false;
        while (matcher.find()) {
            String json = matcher.group(2).replace("\\\"", "\"");
            for (JsonNode dim : MAPPER.readTree(json).path("dimensions")) {
                if (dim.path("score").asInt(0) > 0) sawNonZero = true;
            }
        }
        assertThat(sawNonZero)
                .as("演示种子的画像分数全为 0，雷达看起来仍像坏了")
                .isTrue();
    }

    private static String readSeedSql() throws Exception {
        assertThat(Files.exists(SEED_SQL))
                .as("找不到 %s（测试工作目录应为 zya-server/）", SEED_SQL.toAbsolutePath())
                .isTrue();
        return Files.readString(SEED_SQL, StandardCharsets.UTF_8);
    }
}
