package com.util;

/**
 * 从模型返回的自由文本里抠出 JSON 对象。
 *
 * <p><b>为什么需要它：</b>当前调用链没有开启 JSON mode，模型经常把 JSON 包在
 * markdown 代码块里、或前后加一句寒暄。这类文本直接 {@code readTree} 会失败。
 *
 * <p><b>为什么单独抽一个类：</b>全仓原本有三份各自实现的「提取 + 修补」逻辑
 * （{@code ZhipuAIService}、{@code AiInsightService}、{@code AiSafeCallService}），
 * 行为略有出入，改一处漏两处。这里是公共实现。
 *
 * <p>注意：这些都是**权宜手段**。正解是在请求里带
 * {@code response_format: {"type":"json_object"}}，让模型直接吐合法 JSON——
 * 届时本类的大部分逻辑可以退化成一句 {@code trim()}。
 */
public final class JsonExtractor {

    private JsonExtractor() {
    }

    /**
     * 尽力从文本中取出一个 JSON 对象串。
     *
     * @return 提取到的 JSON 片段；取不到时返回 {@code "{}"}，由调用方按空对象处理
     */
    public static String extractObject(String raw) {
        if (raw == null) return "{}";
        String s = stripCodeFence(raw.trim());
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) return s.substring(start, end + 1);
        return "{}";
    }

    /** 剥掉 markdown 代码块围栏，如 ```json ... ```。 */
    static String stripCodeFence(String text) {
        String s = text;
        if (s.startsWith("```")) {
            int newline = s.indexOf('\n');
            if (newline >= 0) s = s.substring(newline + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
            s = s.trim();
        }
        return s;
    }
}
