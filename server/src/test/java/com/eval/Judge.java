package com.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.util.JsonExtractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * LLM-as-judge：评判生成内容的正确性、依据性与引用支撑度。
 *
 * <p><b>它填补的缺口（这才是研究增量所在）：</b>
 * 项目里已有的 {@code CourseKnowledgeContextService.validateCitations} 只能机械校验
 * 「引用的标签是否存在」——它判断不了**被引用的片段是否真的支撑该论断**。
 * 模型完全可以引用一个真实存在但与结论无关的片段，从而骗过机械检查。
 * 引用支撑度必须由语义判断来测，这就是本类的职责。
 *
 * <p>输出固定为 JSON，温度 0，且强制「先引用原句再打分」，以减少空口评分。
 */
public class Judge {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 评判提示词：固定不变，改动需同步更新历史结果的可比性说明。 */
    public static final String SYSTEM_PROMPT = """
            你是严格的技术内容评审专家。你只能依据【参考资料】判断【学生回答】，禁止使用你自己的领域知识。
            评分前必须先在 rationale 中引用【参考资料】中支撑该结论的原句。
            若【学生回答】包含【参考资料】中找不到依据的断言，必须逐条列入 unsupported_claims。
            只输出 JSON，不要任何其他文字。""";

    private static final String OUTPUT_CONTRACT = """

            只输出如下 JSON：
            {"correctness":0-5,"grounding":0-5,"citationSupport":0-5,
             "unsupported_claims":["..."],"missing_facts":["..."],
             "verdict":"correct|partial|wrong|refusal_correct|refusal_wrong",
             "rationale":"不超过200字，且必须先引用参考资料中的原句"}

            评分口径：
            - correctness：学生回答与参考答案的一致程度
            - grounding：回答中的断言在参考资料里能找到依据的程度
            - citationSupport：回答所引用的片段是否**真的支撑**了对应的论断（0=引用了但无关，5=完全支撑）
            - verdict：若题目在参考资料里没有答案，而学生回答明确说明了这一点，记 refusal_correct；
              若资料没有答案却仍然编造了内容，记 refusal_wrong
            """;

    /**
     * @param context 检索到的课程资料（含「资料N-M」标签与引用要求）
     * @param answer  被测系统的回答
     */
    public Verdict judge(ChatModel model, EvalCase evalCase, String context, String answer)
            throws IOException, InterruptedException {
        String user = buildUserPrompt(evalCase, context, answer);
        ChatModel.Reply reply = model.chat(SYSTEM_PROMPT, user);
        return parse(reply.content());
    }

    String buildUserPrompt(EvalCase evalCase, String context, String answer) {
        StringBuilder sb = new StringBuilder();
        sb.append("【参考资料】\n").append(orNone(context)).append("\n\n");
        sb.append("【题目】\n").append(evalCase.question()).append("\n\n");
        if (!evalCase.negative()) {
            sb.append("【参考答案】\n").append(evalCase.referenceAnswer()).append("\n\n");
        }
        sb.append("【学生回答】\n").append(orNone(answer));
        sb.append(OUTPUT_CONTRACT);
        return sb.toString();
    }

    Verdict parse(String raw) throws IOException {
        JsonNode root = MAPPER.readTree(JsonExtractor.extractObject(raw));
        return new Verdict(
                clamp(root.path("correctness").asInt(0)),
                clamp(root.path("grounding").asInt(0)),
                clamp(root.path("citationSupport").asInt(0)),
                strings(root.path("unsupported_claims")),
                strings(root.path("missing_facts")),
                root.path("verdict").asText("unknown"),
                root.path("rationale").asText(""));
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(5, score));
    }

    private static List<String> strings(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> out.add(item.asText()));
        }
        return out;
    }

    private static String orNone(String text) {
        return (text == null || text.isBlank()) ? "（无）" : text;
    }

    /**
     * 评判结果。
     *
     * @param citationSupport 0-5，**本项目的核心贡献点**：引用片段是否真的支撑论断
     * @param unsupportedClaims 资料里找不到依据的断言，幻觉的直接证据
     */
    public record Verdict(int correctness, int grounding, int citationSupport,
                          List<String> unsupportedClaims, List<String> missingFacts,
                          String verdict, String rationale) {

        public boolean refusedCorrectly() {
            return "refusal_correct".equals(verdict);
        }
    }
}
