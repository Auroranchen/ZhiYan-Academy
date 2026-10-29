package com.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 修复模型产出 JSON 的两类常见瑕疵。
 *
 * <p>存在理由是一条实测：{@code agent.codecase} 返回了 17,033 token 的产物，
 * 因为内容里少转义了一个引号而解析失败，于是 {@code callJson} **重发了整次调用**——
 * 72.2s + 82.0s，其中 72s 纯属浪费。原始报错：
 *
 * <pre>Unexpected character ('+' (code 43)): was expecting comma to separate Object entries</pre>
 *
 * <p>这个错误恰好能由 {@code "code":"sum = " + str(a+b)"} 复现：字符串值里出现了
 * 未转义的引号，解析器以为字符串到此结束，紧接着就撞上了 {@code +}。
 *
 * <p>本地修复是**零成本**的：能修好就不必重发请求，修不好再走原有的重试。
 */
class JsonRepairTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Map<String, Object> parse(String json) throws Exception {
        return MAPPER.readValue(JsonRepair.repair(json), Map.class);
    }

    @Test
    @DisplayName("复现线上那条报错：字符串值里未转义的引号被补上转义")
    void unescapedQuoteInsideAValueIsEscaped() throws Exception {
        String broken = "{\"cases\":[{\"code\":\"sum = \" + str(a+b)\",\"lang\":\"python\"}]}";

        Map<String, Object> parsed = parse(broken);

        assertThat(parsed).containsKey("cases");
        @SuppressWarnings("unchecked")
        var cases = (java.util.List<Map<String, Object>>) parsed.get("cases");
        assertThat(cases.get(0).get("code")).isEqualTo("sum = \" + str(a+b)");
    }

    @Test
    @DisplayName("本来就合法的 JSON 一个字节都不改")
    void validJsonIsLeftUntouched() {
        String valid = "{\"title\":\"二叉树\",\"scenes\":[{\"no\":1,\"text\":\"我们来看\"}]}";

        assertThat(JsonRepair.repair(valid)).isEqualTo(valid);
    }

    @Test
    @DisplayName("正确转义的引号与反斜杠保持原样")
    void alreadyEscapedSequencesSurviveIntact() throws Exception {
        String valid = "{\"code\":\"printf(\\\"hi\\\")\\n\"}";

        assertThat(JsonRepair.repair(valid)).isEqualTo(valid);
        assertThat(parse(valid).get("code")).isEqualTo("printf(\"hi\")\n");
    }

    @Test
    @DisplayName("字符串里的裸换行符按 JSON 规范转义")
    void rawControlCharactersAreEscaped() throws Exception {
        String broken = "{\"code\":\"line1\nline2\"}";

        Map<String, Object> parsed = parse(broken);

        assertThat(parsed.get("code")).isEqualTo("line1\nline2");
    }

    @Test
    @DisplayName("连续多个未转义引号也能逐个补上")
    void multipleStrayQuotesAreAllRepaired() throws Exception {
        String broken = "{\"a\":\"say \"x\" and \"y\" now\"}";

        Map<String, Object> parsed = parse(broken);

        assertThat(parsed.get("a")).isEqualTo("say \"x\" and \"y\" now");
    }

    @Test
    @DisplayName("结尾的引号仍是字符串结束符，不会被误当内容")
    void trailingQuoteStillTerminatesTheString() throws Exception {
        String broken = "{\"a\":\"he said \"hi\"\"}";

        Map<String, Object> parsed = parse(broken);

        assertThat(parsed.get("a")).isEqualTo("he said \"hi\"");
    }

    @Test
    @DisplayName("对象键与嵌套结构不受影响")
    void keysAndNestingArePreserved() throws Exception {
        String valid = "{\"outer\":{\"inner\":[1,2,{\"k\":\"v\"}]},\"flag\":true,\"n\":null}";

        Map<String, Object> parsed = parse(valid);

        assertThat(parsed).containsKeys("outer", "flag", "n");
        assertThat(parsed.get("flag")).isEqualTo(true);
        assertThat(parsed.get("n")).isNull();
    }

    @Test
    @DisplayName("空串与 null 不炸")
    void blankInputsAreTolerated() {
        assertThat(JsonRepair.repair(null)).isNull();
        assertThat(JsonRepair.repair("")).isEmpty();
        assertThat(JsonRepair.repair("{}")).isEqualTo("{}");
    }

    @Test
    @DisplayName("修复结果必须仍可被解析——否则调用方只是把报错推迟了一步")
    void repairedOutputIsParseable() throws Exception {
        String broken = "{\"expectedOutput\":\"sum = \" + str(a+b)\",\"explanation\":\"用 \"+\" 连接\"}";

        assertThat(MAPPER.readTree(JsonRepair.repair(broken))).isNotNull();
    }
}
