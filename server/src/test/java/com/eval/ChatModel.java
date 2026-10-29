package com.eval;

import java.io.IOException;

/**
 * 评测用的对话模型抽象。
 *
 * <p>刻意独立于 {@code ZhipuAIService}：评测需要**固定**模型、温度并独立记录用量，
 * 而生产客户端会随应用配置漂移（默认模型、重试、限流都在那儿）。评测若跟着漂，
 * 历史结果就不可比。
 *
 * <p>藏在接口后面还有一个更实际的原因：**冒烟测试必须能离线跑**。
 * 用桩实现就能在 CI 里验证整条链路（加载→检索→打分→出表）而不打任何计费请求。
 */
public interface ChatModel {

    /** 模型标识，写进结果表。 */
    String name();

    Reply chat(String system, String user) throws IOException, InterruptedException;

    record Reply(String content, int promptTokens, int completionTokens, long elapsedMs) {

        public int totalTokens() {
            return promptTokens + completionTokens;
        }
    }
}
