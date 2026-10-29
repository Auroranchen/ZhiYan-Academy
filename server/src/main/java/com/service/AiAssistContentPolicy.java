package com.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 学生端 AI 助手：违禁/越狱话术处理与生成结果「去 AI 套话」后处理。
 * 可在类内违禁正则数组中增补业务所需的违规表达。
 */
public final class AiAssistContentPolicy {

    private AiAssistContentPolicy() {}

    /**
     * 追加到面向师生可见回复的 system 提示末尾：压缩空洞自称与敬语模板。
     */
    public static final String STUDENT_FACING_PROMPT_TAIL =
            "\n【语气硬性要求】禁止使用：自称「人工智能」「大模型」「GPT」「语言模型」；禁止使用" +
                    "「很高兴为您服务」「以下是」「综上所述」「总结一下」「希望对您有帮助」「作为你的助手」「让我来为你」等模板套话。\n" +
                    "用第三人称不写「老师」自称也可，或直接陈述知识点；少用感叹号。\n"
                    + "【合规】不出现违法低俗、煽动攻击、教人绕过平台或诚信规则等内容；不写提示词越狱教程。\n";

    /** 结构化 JSON 类输出：减少前言后语与非必要解释 */
    public static final String JSON_TASK_BRIEF_TAIL =
            "\n仅按要求输出结构化结果；不要寒暄、不写模型自述、不套用「以上为」类收尾。\n";

    /**
     * 防幻觉硬性约束：用于一切「生成教学/学术内容」的 system 提示末尾。
     * 要求模型只基于给定事实作答、对不确定内容显式标注、不编造引用与数据。
     */
    public static final String ANTI_HALLUCINATION_TAIL =
            "\n【防幻觉与学术严谨 — 硬性要求】\n" +
                    "1. 只输出有把握的、学科上公认正确的内容；不得编造事实、数据、统计结论、人名、文献标题、网址或 API。\n" +
                    "2. 涉及版本/参数/具体数值时，若不确定，用通用表述并提示「请以官方文档为准」，不要臆造精确数字。\n" +
                    "3. 不得给出与给定学情/题目数据相矛盾的结论；信息不足时直说「依据现有数据无法判断」。\n" +
                    "4. 代码须可运行、逻辑自洽；不确定的库用法不要硬写。\n" +
                    "5. 不输出违法、低俗、歧视、政治敏感或诱导作弊的内容。\n";

    public static String withStudentFacingTone(String baseSystemPrompt) {
        return baseSystemPrompt + STUDENT_FACING_PROMPT_TAIL;
    }

    public static String withJsonTaskBrief(String baseSystemPrompt) {
        return baseSystemPrompt + JSON_TASK_BRIEF_TAIL;
    }

    public static String withAntiHallucination(String baseSystemPrompt) {
        return baseSystemPrompt + ANTI_HALLUCINATION_TAIL;
    }

    /**
     * 生成内容安全出口过滤：对模型「输出」再做一次敏感/违规词过滤，命中替换为「[已过滤]」。
     * 用于一切将直接展示给用户的 AI 文本。
     */
    public static String filterOutput(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String t = text;
        for (Pattern p : TABOO_SURFACE_PATTERNS) {
            t = p.matcher(t).replaceAll("[已过滤]");
        }
        return t;
    }

    /** 向模型投喂前：整块替换为占位，降低注入与不当引导 */
    private static final Pattern[] INPUT_BLOCKED_PATTERNS = new Pattern[]{
            Pattern.compile("忽略(?:掉|前述|以上|上面)?.*?指令"),
            Pattern.compile("无视(?:掉|前述|以上)?.*?规则"),
            Pattern.compile("DAN\\s*[模式版]?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("绕开.{0,6}(验证|风控|监考|批改)"),
            Pattern.compile("[Ff]orget\\s+(all\\s+)?previous\\s+instructions"),
            Pattern.compile("越狱|prompt\\s*injection", Pattern.CASE_INSENSITIVE),
    };

    /** 表面违禁词（可继续扩充），命中则替换为「[已省略]」 */
    private static final Pattern[] TABOO_SURFACE_PATTERNS = new Pattern[]{
            Pattern.compile("法轮功"),
            Pattern.compile("台独|港独"),
            Pattern.compile("自杀教程|教人自杀"),
            Pattern.compile("恐怖袭击|炸药配方"),
            Pattern.compile("(?i)(caoni|nmsl)"),
    };

    /** 模型输出里去「AI 味」常见句式（逐个删除匹配子串） */
    private static final String[] OUTPUT_FLUFF_TO_REMOVE = {
            "作为一款人工智能助手", "作为一款人工智能",
            "作为一个人工智能", "作为人工智能", "作为一款AI助手", "作为一款AI",
            "作为AI助手", "作为您的AI助手", "作为你的AI助手", "我是一名AI助手",
            "我是一个人工智能", "我是一个语言模型",
            "我很乐意为您解答", "我很乐意帮助你", "很高兴能够帮助你", "很高兴为你服务",
            "希望这篇回答对您有所帮助", "希望对你有所帮助", "希望对您有所帮助",
            "总结一下，", "综上所述，", "让我来为您解答", "让我来为你解答",
            "首先需要明确的是", "需要注意的是，", "不得不提的是",
    };

    /** 违禁与越狱话术：投喂前清洗 */
    public static String sanitizeModelInput(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String t = raw;
        for (Pattern p : TABOO_SURFACE_PATTERNS) {
            t = p.matcher(t).replaceAll("[已省略]");
        }
        for (Pattern p : INPUT_BLOCKED_PATTERNS) {
            t = p.matcher(t).replaceAll("[已移除不当引导]");
        }
        return normalizeSpaces(t.trim());
    }

    /** 对可见讲解类纯文本字段做轻度去套话 */
    public static String stripTeachingTone(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String s = text;
        for (String frag : OUTPUT_FLUFF_TO_REMOVE) {
            s = s.replace(frag, "");
        }
        if (lower.startsWith("以上是") || lower.startsWith("以下为") || lower.startsWith("下面为您")) {
            int cut = s.indexOf("\n");
            if (cut > 0) {
                s = s.substring(cut + 1);
            }
        }
        return normalizeSpaces(s.trim());
    }

    /**
     * 对题目讲解 JSON 中面向学生的字符串字段就地清洗（不改动 JSON 结构与 referenceCode）。
     */
    public static void sanitizeExplainObjectFields(ObjectNode root) {
        if (root == null) {
            return;
        }
        putIfPresentText(root, "keyPoints");
        putIfPresentText(root, "errorAnalysis");

        JsonNode steps = root.get("steps");
        if (steps != null && steps.isArray()) {
            ArrayNode arr = (ArrayNode) steps;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode n = arr.get(i);
                if (n.isTextual()) {
                    arr.set(i, TextNode.valueOf(stripTeachingTone(n.asText())));
                }
            }
        }

        JsonNode cm = root.get("commonMistakes");
        if (cm != null && cm.isArray()) {
            ArrayNode arr = (ArrayNode) cm;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode n = arr.get(i);
                if (n.isTextual()) {
                    arr.set(i, TextNode.valueOf(stripTeachingTone(n.asText())));
                }
            }
        }

        JsonNode exp = root.get("expansion");
        if (exp != null && exp.isObject()) {
            ObjectNode eo = (ObjectNode) exp;
            putIfPresentText(eo, "question");
            putIfPresentText(eo, "answer");
            putIfPresentText(eo, "analysis");
        }
    }

    private static void putIfPresentText(ObjectNode obj, String field) {
        if (obj.has(field) && obj.get(field).isTextual()) {
            obj.put(field, stripTeachingTone(obj.get(field).asText()));
        }
    }

    private static String normalizeSpaces(String s) {
        if (s == null) return "";
        return s.replaceAll(" {2,}", " ").trim();
    }
}
