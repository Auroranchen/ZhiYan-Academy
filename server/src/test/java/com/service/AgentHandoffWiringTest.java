package com.service;

import com.agent.ArtifactKey;
import com.agent.HandoffPlanner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 交接关系与流水线的接线。
 *
 * <p>重点在「可证明」：{@code handoff_records} 里的每条边都要带 {@code injected} 与
 * {@code digestChars}，让「上游产物确实进了下游提示词」是可验证的，
 * 而不是只能听信一句声明——那正是改造前 {@code handoffs} 的状态。
 */
class AgentHandoffWiringTest {

    private final AgentPipelineService pipeline = new AgentPipelineService();

    private static HandoffPlanner.Plan planOf(HandoffPlanner.Handoff... edges) {
        return HandoffPlanner.plan(List.of(edges));
    }

    private static Map<String, Object> stepsWith(Object... keysAndValues) {
        Map<String, Object> steps = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            steps.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return steps;
    }

    // ---------- 产物注入 ----------

    @Test
    @DisplayName("没有交接边指向自己时，不注入任何东西")
    void noUpstreamMeansNoDigest() {
        HandoffPlanner.Plan plan = planOf();
        Map<String, Object> steps = stepsWith("plan", Map.of("stages", List.of("阶段一")));

        String digest = ReflectionTestUtils.invokeMethod(
                pipeline, "handoffDigest", ArtifactKey.QUIZ, plan, steps);

        assertThat(digest).isEmpty();
    }

    @Test
    @DisplayName("有交接边时，上游产物的内容会真的出现在下游提示词里")
    void upstreamArtifactIsInjected() {
        HandoffPlanner.Plan plan = planOf(
                new HandoffPlanner.Handoff(ArtifactKey.PLAN, ArtifactKey.QUIZ, "学习路径", "题目要贴合路径"));
        Map<String, Object> steps = stepsWith(
                "plan", Map.of("stages", List.of("先掌握概念", "再做练习")));

        String digest = ReflectionTestUtils.invokeMethod(
                pipeline, "handoffDigest", ArtifactKey.QUIZ, plan, steps);

        assertThat(digest)
                .as("这才是「交接」有实际含义的地方：下游提示词里必须真的含有上游产出")
                .contains("规划智能体")
                .contains("先掌握概念")
                .contains("先再做练习".substring(1));
    }

    @Test
    @DisplayName("上游产物过长时被截断，避免挤占提示词预算")
    void longArtifactIsTruncated() {
        HandoffPlanner.Plan plan = planOf(
                new HandoffPlanner.Handoff(ArtifactKey.PLAN, ArtifactKey.QUIZ, "", ""));
        Map<String, Object> steps = stepsWith("plan", Map.of("blob", "x".repeat(5000)));

        String digest = ReflectionTestUtils.invokeMethod(
                pipeline, "handoffDigest", ArtifactKey.QUIZ, plan, steps);

        assertThat(digest.length())
                .as("单条上游产物应被截断到上限附近")
                .isLessThan(2000);
        assertThat(digest).contains("…");
    }

    @Test
    @DisplayName("上游数量封顶，防止提示词被撑爆")
    void upstreamCountIsCapped() {
        HandoffPlanner.Plan plan = planOf(
                new HandoffPlanner.Handoff(ArtifactKey.PLAN, ArtifactKey.EXPLAINER, "", ""),
                new HandoffPlanner.Handoff(ArtifactKey.RESOURCE, ArtifactKey.EXPLAINER, "", ""),
                new HandoffPlanner.Handoff(ArtifactKey.QUIZ, ArtifactKey.EXPLAINER, "", ""),
                new HandoffPlanner.Handoff(ArtifactKey.READING, ArtifactKey.EXPLAINER, "", ""),
                new HandoffPlanner.Handoff(ArtifactKey.CODECASE, ArtifactKey.EXPLAINER, "", ""));
        Map<String, Object> steps = stepsWith(
                "plan", "A", "resource", "B", "quiz", "C", "reading", "D", "codecase", "E");

        String digest = ReflectionTestUtils.invokeMethod(
                pipeline, "handoffDigest", ArtifactKey.EXPLAINER, plan, steps);

        long labeled = digest.lines().filter(line -> line.startsWith("- ")).count();
        assertThat(labeled).as("最多注入 3 条上游产物").isEqualTo(3);
    }

    @Test
    @DisplayName("上游步骤尚未产出时不报错，只是不注入")
    void missingUpstreamIsTolerated() {
        HandoffPlanner.Plan plan = planOf(
                new HandoffPlanner.Handoff(ArtifactKey.PLAN, ArtifactKey.QUIZ, "", ""));

        String digest = ReflectionTestUtils.invokeMethod(
                pipeline, "handoffDigest", ArtifactKey.QUIZ, plan, new LinkedHashMap<String, Object>());

        assertThat(digest).isEmpty();
    }

    // ---------- 可证明的交接记录 ----------

    @Test
    @DisplayName("交接记录标出每条边是否真的注入了产物")
    void recordsProveInjection() {
        HandoffPlanner.Plan plan = planOf(
                new HandoffPlanner.Handoff(ArtifactKey.PLAN, ArtifactKey.QUIZ, "路径", "贴合"),
                new HandoffPlanner.Handoff(ArtifactKey.READING, ArtifactKey.MINDMAP, "读物", "补充"));
        Map<String, String> digests = Map.of("quiz", "有内容", "mindmap", "");

        List<Map<String, Object>> records = ReflectionTestUtils.invokeMethod(
                pipeline, "handoffRecords", plan, digests);

        assertThat(records).hasSize(2);
        Map<String, Object> first = records.get(0);
        assertThat(first.get("from")).isEqualTo("plan");
        assertThat(first.get("to")).isEqualTo("quiz");
        assertThat(first.get("injected")).isEqualTo(true);
        assertThat((Integer) first.get("digestChars")).isPositive();

        Map<String, Object> second = records.get(1);
        assertThat(second.get("injected"))
                .as("没有注入的边必须如实标 false——否则这个字段就成了装饰")
                .isEqualTo(false);
        assertThat(second.get("digestChars")).isEqualTo(0);
    }

    // ---------- 服务端布局 ----------

    @Test
    @DisplayName("布局：结构段分层固定，内容段按交接深度排")
    void layoutKeepsStructureFixedAndLayersContent() {
        HandoffPlanner.Plan plan = planOf(
                new HandoffPlanner.Handoff(ArtifactKey.PLAN, ArtifactKey.QUIZ, "", ""));

        Map<String, Object> graph = ReflectionTestUtils.invokeMethod(
                pipeline, "handoffLayout", plan, Map.<String, String>of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        assertThat(nodes).hasSize(ArtifactKey.DEFAULT_ORDER.size());
        assertThat(nodes.stream().map(n -> n.get("key")).toList())
                .as("节点顺序应与执行顺序一致，前端才能直接按序画")
                .containsExactlyElementsOf(
                        plan.order().stream().map(ArtifactKey::key).toList());

        Map<String, Integer> layer = new LinkedHashMap<>();
        nodes.forEach(n -> layer.put(String.valueOf(n.get("key")), (Integer) n.get("layer")));

        assertThat(layer.get("diagnose")).isLessThan(layer.get("coordinate"));
        assertThat(layer.get("quiz"))
                .as("声明了 plan -> quiz，quiz 应比 plan 深一层")
                .isGreaterThan(layer.get("plan"));
        assertThat(layer.get("assemble"))
                .as("编排必须排在最末层")
                .isGreaterThan(layer.get("explainer"));
    }

    @Test
    @DisplayName("布局的边携带 injected，且与交接记录一致")
    void layoutEdgesCarryInjectionFlag() {
        HandoffPlanner.Plan plan = planOf(
                new HandoffPlanner.Handoff(ArtifactKey.PLAN, ArtifactKey.QUIZ, "路径", "贴合"));

        Map<String, Object> graph = ReflectionTestUtils.invokeMethod(
                pipeline, "handoffLayout", plan, Map.of("quiz", "内容"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");
        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).get("from")).isEqualTo("plan");
        assertThat(edges.get(0).get("to")).isEqualTo("quiz");
        assertThat(edges.get(0).get("injected")).isEqualTo(true);
    }

    @Test
    @DisplayName("无交接时布局仍完整（默认顺序、无边）")
    void layoutWithNoHandoffsIsStillComplete() {
        Map<String, Object> graph = ReflectionTestUtils.invokeMethod(
                pipeline, "handoffLayout", planOf(), Map.<String, String>of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");

        assertThat(nodes).hasSize(ArtifactKey.DEFAULT_ORDER.size());
        assertThat(edges).isEmpty();
        List<Object> keys = new ArrayList<>();
        nodes.forEach(n -> keys.add(n.get("key")));
        assertThat(keys).containsExactlyElementsOf(
                ArtifactKey.DEFAULT_ORDER.stream().map(ArtifactKey::key).toList());
    }
}
