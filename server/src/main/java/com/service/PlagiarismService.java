package com.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.PlagiarismMapper;
import com.model.PlagiarismReport;
import com.response.Result;
import com.util.TextSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 作业查重业务层。
 * 基于学生主观作答文本，使用字符 3-gram Jaccard 相似度进行两两比对，
 * 输出相似对明细并标记超过阈值的疑似抄袭对。
 */
@Service
public class PlagiarismService {

    private static final Logger logger = LoggerFactory.getLogger(PlagiarismService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_PAIRS_STORED = 200;

    @Autowired
    private PlagiarismMapper plagiarismMapper;

    @Autowired
    private ZhipuAIService zhipuAIService;

    @Autowired
    private AiSafeCallService aiSafeCallService;

    /**
     * 对指定作业执行查重。
     */
    public Result<PlagiarismReport> check(Integer homeworkid, Integer teacherid, BigDecimal threshold) {
        if (homeworkid == null) {
            return Result.error("缺少作业ID");
        }
        if (threshold == null) {
            threshold = new BigDecimal("70");
        }
        List<Map<String, Object>> submissions = plagiarismMapper.findSubmissionsForCheck(homeworkid);

        // 构建每个学生的文本与 shingle 集合
        List<Doc> docs = new ArrayList<>();
        for (Map<String, Object> s : submissions) {
            String text = extractText(s.get("content"));
            if (text.length() < TextSimilarity.SHINGLE) {
                continue; // 文本过短无法比对（如纯客观题或文档上传）
            }
            Doc d = new Doc();
            d.studentid = toInt(s.get("studentid"));
            d.studentno = str(s.get("studentno"));
            d.studentname = str(s.get("studentname"));
            d.text = text;
            d.shingles = TextSimilarity.shingles(text);
            docs.add(d);
        }

        List<Map<String, Object>> pairs = new ArrayList<>();
        double maxSim = 0;
        int flagged = 0;
        double thr = threshold.doubleValue();
        for (int i = 0; i < docs.size(); i++) {
            for (int j = i + 1; j < docs.size(); j++) {
                double sim = TextSimilarity.jaccard(docs.get(i).shingles, docs.get(j).shingles) * 100;
                if (sim <= 0) {
                    continue;
                }
                Map<String, Object> pair = new HashMap<>();
                pair.put("studentidA", docs.get(i).studentid);
                pair.put("studentnoA", docs.get(i).studentno);
                pair.put("studentnameA", docs.get(i).studentname);
                pair.put("studentidB", docs.get(j).studentid);
                pair.put("studentnoB", docs.get(j).studentno);
                pair.put("studentnameB", docs.get(j).studentname);
                double simRound = Math.round(sim * 10) / 10.0;
                pair.put("similarity", simRound);
                pair.put("flagged", simRound >= thr);
                if (simRound >= thr) {
                    flagged++;
                }
                if (simRound > maxSim) {
                    maxSim = simRound;
                }
                pairs.add(pair);
            }
        }

        pairs.sort((a, b) -> Double.compare((double) b.get("similarity"), (double) a.get("similarity")));
        int totalPairs = pairs.size();
        if (pairs.size() > MAX_PAIRS_STORED) {
            pairs = pairs.subList(0, MAX_PAIRS_STORED);
        }

        PlagiarismReport report = new PlagiarismReport();
        report.setHomeworkid(homeworkid);
        report.setTeacherid(teacherid);
        report.setPairCount(totalPairs);
        report.setFlaggedCount(flagged);
        report.setMaxSimilarity(BigDecimal.valueOf(maxSim).setScale(2, RoundingMode.HALF_UP));
        report.setThreshold(threshold);
        try {
            report.setResultJson(MAPPER.writeValueAsString(pairs));
        } catch (Exception e) {
            report.setResultJson("[]");
        }
        plagiarismMapper.insertReport(report);

        if (docs.size() < 2) {
            report.setResultJson("[]");
            return Result.success("可比对的主观作答不足2份，建议对含简答/填空题的作业进行查重", report);
        }
        return Result.success("查重完成，共比对 " + totalPairs + " 对，疑似 " + flagged + " 对", report);
    }

    /**
     * AI 原创性 / 代写检测：对作业的主观作答进行一次性批量分析，
     * 评估每位学生答案的「AI 生成可能性」与「原创度」。
     */
    public Result<List<Map<String, Object>>> aiDetect(Integer homeworkid) {
        if (homeworkid == null) {
            return Result.error("缺少作业ID");
        }
        List<Map<String, Object>> submissions = plagiarismMapper.findSubmissionsForCheck(homeworkid);

        // 收集有足够主观文本的作答（最多 20 份，单份截断 600 字，控制提示词长度与成本）
        List<Map<String, Object>> docs = new ArrayList<>();
        StringBuilder corpus = new StringBuilder();
        int idx = 0;
        for (Map<String, Object> s : submissions) {
            String text = rawText(s.get("content"));
            if (text.length() < 15) continue;
            if (text.length() > 600) text = text.substring(0, 600);
            idx++;
            Map<String, Object> d = new HashMap<>();
            d.put("seq", idx);
            d.put("studentid", toInt(s.get("studentid")));
            d.put("studentno", str(s.get("studentno")));
            d.put("studentname", str(s.get("studentname")));
            docs.add(d);
            corpus.append("【").append(idx).append("】学生：").append(str(s.get("studentname")))
                    .append("\n答案：").append(text).append("\n\n");
            if (docs.size() >= 20) break;
        }

        if (docs.isEmpty()) {
            return Result.error("没有可分析的主观作答（简答/填空文本过短或为客观题）");
        }

        String systemPrompt = "你是一名学术诚信审查专家，擅长识别 AI 代写与判断文本原创性。\n" +
                "请对每位学生的作答进行评估，严格只返回如下 JSON 数组，不要包含任何额外文字或解释：\n" +
                "[{\"seq\":1, \"aiScore\":0, \"originality\":100, \"verdict\":\"人工原创\", \"reason\":\"简要依据\"}]\n" +
                "字段说明：\n" +
                "- seq：对应题号编号（与输入一致）\n" +
                "- aiScore：AI 生成可能性，0-100 的整数，越高越可能为 AI 代写\n" +
                "- originality：原创度，0-100 的整数\n" +
                "- verdict：从「人工原创」「疑似AI辅助」「高度疑似AI代写」三者中选一个\n" +
                "- reason：不超过40字的中文依据（如：表述高度模板化、术语堆砌、缺乏个人化表达等）\n" +
                "判断参考：AI 文本通常结构工整、用词规范统一、缺乏口语化与个性化、信息密度均匀。";

        String userPrompt = "请评估以下 " + docs.size() + " 位学生的作答：\n\n" + corpus;

        String aiRaw;
        try {
            // 走安全网关：此前这里既不记 ai_call_log，也不做输入清洗与输出过滤
            aiRaw = aiSafeCallService.callText("plagiarism.aiDetect", systemPrompt, userPrompt);
        } catch (Exception e) {
            logger.error("AI原创性检测失败: {}", e.getMessage());
            return Result.error("AI 检测失败：" + e.getMessage());
        }

        List<Map<String, Object>> result = mergeAiResult(docs, aiRaw);
        if (result.isEmpty()) {
            return Result.error("AI 返回结果无法解析，请重试");
        }
        return Result.success("AI 检测完成，共分析 " + result.size() + " 份作答", result);
    }

    /** 将 AI 返回的评估按 seq 合并回学生信息。 */
    private List<Map<String, Object>> mergeAiResult(List<Map<String, Object>> docs, String aiRaw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (aiRaw == null) return out;
        String json = aiRaw.trim();
        int lb = json.indexOf('[');
        int rb = json.lastIndexOf(']');
        if (lb >= 0 && rb > lb) {
            json = json.substring(lb, rb + 1);
        }
        try {
            JsonNode arr = MAPPER.readTree(json);
            Map<Integer, JsonNode> bySeq = new HashMap<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    bySeq.put(n.path("seq").asInt(-1), n);
                }
            }
            for (Map<String, Object> d : docs) {
                JsonNode n = bySeq.get((Integer) d.get("seq"));
                Map<String, Object> row = new HashMap<>(d);
                if (n != null) {
                    row.put("aiScore", clamp(n.path("aiScore").asInt(0)));
                    row.put("originality", clamp(n.path("originality").asInt(0)));
                    row.put("verdict", n.path("verdict").asText("未知"));
                    row.put("reason", n.path("reason").asText(""));
                } else {
                    row.put("aiScore", 0);
                    row.put("originality", 0);
                    row.put("verdict", "未评估");
                    row.put("reason", "");
                }
                out.add(row);
            }
            out.sort((a, b) -> Integer.compare((int) b.get("aiScore"), (int) a.get("aiScore")));
        } catch (Exception e) {
            logger.error("解析AI检测结果失败: {}", e.getMessage());
        }
        return out;
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    /** 提取主观作答原文（保留可读文本，用于喂给 AI）。 */
    private String rawText(Object contentObj) {
        if (contentObj == null) return "";
        String content = contentObj.toString();
        StringBuilder sb = new StringBuilder();
        try {
            JsonNode answers = MAPPER.readTree(content).path("answers");
            if (answers.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = answers.fields();
                while (it.hasNext()) {
                    JsonNode v = it.next().getValue();
                    if (v.isTextual()) {
                        String t = v.asText().trim();
                        if (t.length() >= 4) sb.append(t).append('\n');
                    }
                }
            } else {
                sb.append(content);
            }
        } catch (Exception e) {
            sb.append(content);
        }
        return sb.toString().trim();
    }

    public List<PlagiarismReport> listByHomework(Integer homeworkid) {
        return plagiarismMapper.findByHomework(homeworkid);
    }

    public List<PlagiarismReport> listByTeacher(Integer teacherid) {
        return plagiarismMapper.findByTeacher(teacherid);
    }

    public PlagiarismReport getById(Integer id) {
        return plagiarismMapper.findById(id);
    }

    // ---------------- 内部工具 ----------------

    private static class Doc {
        Integer studentid;
        String studentno;
        String studentname;
        String text;
        Set<String> shingles;
    }

    /** 从作答JSON中提取全部主观文本（简答/填空等字符串答案）。 */
    private String extractText(Object contentObj) {
        if (contentObj == null) {
            return "";
        }
        String content = contentObj.toString();
        StringBuilder sb = new StringBuilder();
        try {
            JsonNode answers = MAPPER.readTree(content).path("answers");
            if (answers.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = answers.fields();
                while (it.hasNext()) {
                    JsonNode v = it.next().getValue();
                    if (v.isTextual()) {
                        String t = v.asText().trim();
                        if (t.length() >= 4) { // 仅纳入足够长的文本，过滤单字/选项
                            sb.append(t).append(' ');
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 非JSON则直接使用原文本
            sb.append(content);
        }
        return TextSimilarity.normalize(sb.toString());
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
