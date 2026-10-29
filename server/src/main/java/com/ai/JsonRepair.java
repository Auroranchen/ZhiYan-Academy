package com.ai;

/**
 * 修复模型产出的 JSON。
 *
 * <p>背景：{@code AiSafeCallService.callJson} 在解析失败时会**重发整次调用**。
 * 对于 codecase 这种一次就要生成 17k token 的重活，重发意味着再等 80 秒——
 * 实测一次 72.2s 的产物仅仅因为少转义一个引号就被丢弃重来。
 *
 * <p>所以先在这里做一次零成本的本地修复：能修好就直接用，修不好才让调用方重试。
 */
public final class JsonRepair {

    private JsonRepair() {
    }

    /**
     * 修复字符串值里未转义的引号与裸控制字符。
     *
     * <p>判定依据是一个 JSON 的事实：字符串结束引号的后面，只可能是 {@code , } ] :}
     * 或文本结尾。若后面跟着别的字符，那个引号就不可能是结束符——它其实是内容的一部分，
     * 补上转义即可。
     *
     * <p>刻意不做更激进的事（补括号、补逗号、改单引号）：那些修复一旦猜错会**静默改坏数据**，
     * 比解析失败更糟。修不好就让调用方照旧重试。
     *
     * @param json 模型产出的原文；null 或空串原样返回
     */
    public static String repair(String json) {
        if (json == null || json.isEmpty()) return json;

        StringBuilder out = new StringBuilder(json.length() + 16);
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (!inString) {
                out.append(c);
                if (c == '"') inString = true;
                continue;
            }

            if (c == '\\') {
                // 转义序列整体搬过去，其中的引号不参与「是不是结束符」的判定
                out.append(c);
                if (i + 1 < json.length()) out.append(json.charAt(++i));
                continue;
            }

            if (c == '"') {
                if (endsString(json, i + 1)) {
                    out.append(c);
                    inString = false;
                } else {
                    out.append("\\\"");
                }
                continue;
            }

            // JSON 字符串里不允许出现裸控制字符；代码片段里的换行最容易踩到
            if (c < 0x20) {
                switch (c) {
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> out.append(String.format("\\u%04x", (int) c));
                }
                continue;
            }

            out.append(c);
        }
        return out.toString();
    }

    /** 结束引号之后跳过空白，只可能看到 {@code , } ] :}；看到别的就说明它不是结束引号。 */
    private static boolean endsString(String json, int from) {
        for (int i = from; i < json.length(); i++) {
            char c = json.charAt(i);
            if (Character.isWhitespace(c)) continue;
            return c == ',' || c == '}' || c == ']' || c == ':';
        }
        return true;
    }
}
