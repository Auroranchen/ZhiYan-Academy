package com.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 金标准集：JSONL（一行一题）。
 *
 * <p>用 JSONL 而不是单个 JSON 数组，是为了让「加一道题」在 diff 里只增一行，
 * 也便于人工逐行校对——出题是这个项目里最需要人盯着看的部分。
 */
public class EvalDataset {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final String version;
    private final List<EvalCase> cases;

    public EvalDataset(String name, String version, List<EvalCase> cases) {
        this.name = name;
        this.version = version;
        this.cases = List.copyOf(cases);
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    public List<EvalCase> cases() {
        return cases;
    }

    public List<EvalCase> answerable() {
        return cases.stream().filter(c -> !c.negative()).toList();
    }

    public List<EvalCase> negatives() {
        return cases.stream().filter(EvalCase::negative).toList();
    }

    /** 供结果表按主题分组用。 */
    public List<String> topics() {
        return cases.stream().map(EvalCase::topic).distinct().sorted().toList();
    }

    public static EvalDataset load(Path jsonl, String name) throws IOException {
        List<EvalCase> loaded = new ArrayList<>();
        List<String> malformed = new ArrayList<>();
        int lineNo = 0;
        for (String line : Files.readAllLines(jsonl, StandardCharsets.UTF_8)) {
            lineNo++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            try {
                loaded.add(MAPPER.readValue(trimmed, EvalCase.class));
            } catch (Exception e) {
                // 出题是人工活，格式错的题要明确指出是哪一行，而不是整体失败
                malformed.add("第 " + lineNo + " 行: " + e.getMessage());
            }
        }
        if (!malformed.isEmpty()) {
            throw new IOException("金标准集格式有误（" + jsonl + "）:\n  "
                    + String.join("\n  ", malformed));
        }
        if (loaded.isEmpty()) {
            throw new IOException("金标准集为空: " + jsonl);
        }
        return new EvalDataset(name, extractVersion(jsonl), loaded);
    }

    private static String extractVersion(Path jsonl) {
        String fileName = jsonl.getFileName().toString();
        return fileName.endsWith(".jsonl")
                ? fileName.substring(0, fileName.length() - ".jsonl".length())
                : fileName;
    }

    /** 反序列化时忽略未知字段，便于出题人加备注而不改模型。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IgnoreUnknown() {
    }
}
