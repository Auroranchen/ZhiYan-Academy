package com.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 交接规划器的行为与**不变式**。
 *
 * <p>不变式是这里的重点：{@link HandoffPlanner.Plan#order()} 必须是默认全序的一个排列——
 * 不跳过、不重复、不发明。模型产出的交接是**不可信输入**，无论它给出什么，
 * 流水线都必须还能跑完。最后那个随机化测试就是来打这条不变式的。
 */
class HandoffPlannerTest {

    private static List<String> keys(List<ArtifactKey> order) {
        return order.stream().map(ArtifactKey::key).toList();
    }

    @Test
    @DisplayName("没有交接时，顺序就是默认顺序")
    void noHandoffsKeepsDefaultOrder() {
        HandoffPlanner.Plan plan = HandoffPlanner.plan(List.of());

        assertThat(plan.order()).isEqualTo(ArtifactKey.DEFAULT_ORDER);
        assertThat(plan.reordered()).isFalse();
        assertThat(plan.accepted()).isEmpty();
    }

    @Test
    @DisplayName("合法交接会真正改变内容段顺序——否则「消费 handoffs」只是句空话")
    void validHandoffActuallyReorders() {
        // 声明 quiz 先于 plan：把出题提到规划之前
        HandoffPlanner.Plan plan = HandoffPlanner.plan(List.of(
                new HandoffPlanner.Handoff(ArtifactKey.QUIZ, ArtifactKey.PLAN, "题目", "先出题再规划")));

        assertThat(plan.reordered()).isTrue();
        assertThat(plan.contentOrder().indexOf(ArtifactKey.QUIZ))
                .as("quiz 应当排到 plan 之前")
                .isLessThan(plan.contentOrder().indexOf(ArtifactKey.PLAN));
        // 结构段不受影响
        assertThat(keys(plan.order()).subList(0, 2)).containsExactly("diagnose", "coordinate");
        assertThat(keys(plan.order()).subList(keys(plan.order()).size() - 3, keys(plan.order()).size()))
                .containsExactly("review", "reviewFinal", "assemble");
    }

    @Test
    @DisplayName("自环被丢弃并给出原因")
    void selfLoopIsRejected() {
        HandoffPlanner.Plan plan = HandoffPlanner.plan(List.of(
                new HandoffPlanner.Handoff(ArtifactKey.QUIZ, ArtifactKey.QUIZ, "", "")));

        assertThat(plan.accepted()).isEmpty();
        assertThat(plan.rejected()).hasSize(1);
        assertThat(plan.rejected().get(0).reason()).contains("自环");
        assertThat(plan.order()).isEqualTo(ArtifactKey.DEFAULT_ORDER);
    }

    @Test
    @DisplayName("结构步骤（诊断/评审）不参与重排，声明了也会被丢弃")
    void structuralStepsAreNotReorderable() {
        HandoffPlanner.Plan plan = HandoffPlanner.plan(List.of(
                new HandoffPlanner.Handoff(ArtifactKey.QUIZ, ArtifactKey.DIAGNOSE, "", "诊断放到最后"),
                new HandoffPlanner.Handoff(ArtifactKey.REVIEW, ArtifactKey.PLAN, "", "评审提到最前")));

        assertThat(plan.accepted()).isEmpty();
        assertThat(plan.rejected()).hasSize(2);
        assertThat(plan.rejected()).allSatisfy(r ->
                assertThat(r.reason()).contains("结构步骤"));
        assertThat(plan.order()).isEqualTo(ArtifactKey.DEFAULT_ORDER);
    }

    @Test
    @DisplayName("重复声明只生效一次")
    void duplicateHandoffsAreRejected() {
        HandoffPlanner.Handoff edge =
                new HandoffPlanner.Handoff(ArtifactKey.QUIZ, ArtifactKey.PLAN, "", "");
        HandoffPlanner.Plan plan = HandoffPlanner.plan(List.of(edge, edge));

        assertThat(plan.accepted()).hasSize(1);
        assertThat(plan.rejected()).hasSize(1);
        assertThat(plan.rejected().get(0).reason()).contains("重复");
    }

    @Test
    @DisplayName("成环时回退默认顺序，并在说明里讲清楚")
    void cycleFallsBackToDefaultOrder() {
        // plan -> quiz 与 quiz -> plan 互相要求对方先执行
        HandoffPlanner.Plan plan = HandoffPlanner.plan(List.of(
                new HandoffPlanner.Handoff(ArtifactKey.PLAN, ArtifactKey.QUIZ, "", ""),
                new HandoffPlanner.Handoff(ArtifactKey.QUIZ, ArtifactKey.PLAN, "", "")));

        assertThat(plan.order()).isEqualTo(ArtifactKey.DEFAULT_ORDER);
        assertThat(plan.note()).as("回退必须在界面上可见，不能悄悄发生").contains("回退");
        assertThat(plan.reordered()).isFalse();
    }

    @Test
    @DisplayName("顺序不可变：链式交接按拓扑序展开")
    void chainHandoffsAreRespected() {
        // codecase -> reading -> mindmap，把这三个提到前面
        HandoffPlanner.Plan plan = HandoffPlanner.plan(List.of(
                new HandoffPlanner.Handoff(ArtifactKey.CODECASE, ArtifactKey.READING, "", ""),
                new HandoffPlanner.Handoff(ArtifactKey.READING, ArtifactKey.MINDMAP, "", "")));

        List<ArtifactKey> content = plan.contentOrder();
        assertThat(content.indexOf(ArtifactKey.CODECASE))
                .isLessThan(content.indexOf(ArtifactKey.READING));
        assertThat(content.indexOf(ArtifactKey.READING))
                .isLessThan(content.indexOf(ArtifactKey.MINDMAP));
    }

    @Test
    @DisplayName("同样的输入永远给出同样的顺序（评测与演示都要求确定性）")
    void planningIsDeterministic() {
        List<HandoffPlanner.Handoff> input = List.of(
                new HandoffPlanner.Handoff(ArtifactKey.QUIZ, ArtifactKey.PLAN, "", ""),
                new HandoffPlanner.Handoff(ArtifactKey.READING, ArtifactKey.MINDMAP, "", ""));

        assertThat(HandoffPlanner.plan(input).order())
                .isEqualTo(HandoffPlanner.plan(input).order());
    }

    @Test
    @DisplayName("解析：兼容 Map 形式、字符串简写，忽略无法识别的项")
    void parsingIsLenient() {
        Object raw = List.of(
                Map.of("from", "quiz", "to", "plan", "artifact", "题目", "reason", "先出题"),
                "reading->mindmap",
                Map.of("from", "不存在的步骤", "to", "plan"),
                Map.of("to", "plan"),
                "格式不对的字符串");

        List<HandoffPlanner.Handoff> parsed = HandoffPlanner.parse(raw);

        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0).from()).isEqualTo(ArtifactKey.QUIZ);
        assertThat(parsed.get(1).from()).isEqualTo(ArtifactKey.READING);
    }

    @Test
    @DisplayName("不变式：任何输入下，输出都是默认全序的排列")
    void orderIsAlwaysAPermutationOfDefaults() {
        Random random = new Random(20260913L);
        ArtifactKey[] all = ArtifactKey.values();
        int checked = 0;

        for (int round = 0; round < 2000; round++) {
            int count = random.nextInt(8);
            List<HandoffPlanner.Handoff> input = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                // 故意包含自环、结构步骤、重复——都是模型可能产出的畸形输入
                input.add(new HandoffPlanner.Handoff(
                        all[random.nextInt(all.length)],
                        all[random.nextInt(all.length)],
                        "art", "reason"));
            }

            HandoffPlanner.Plan plan = HandoffPlanner.plan(input);
            List<ArtifactKey> order = plan.order();

            assertThat(order)
                    .as("第 %d 轮：输出必须是默认全序的排列（不跳过、不重复、不发明）。输入=%s",
                            round, input)
                    .containsExactlyInAnyOrderElementsOf(ArtifactKey.DEFAULT_ORDER);
            assertThat(order).hasSize(ArtifactKey.DEFAULT_ORDER.size());

            // 结构段的位置必须纹丝不动
            assertThat(order.subList(0, ArtifactKey.FIXED_PREFIX.size()))
                    .isEqualTo(ArtifactKey.FIXED_PREFIX);
            assertThat(order.subList(order.size() - ArtifactKey.FIXED_SUFFIX.size(), order.size()))
                    .isEqualTo(ArtifactKey.FIXED_SUFFIX);
            checked++;
        }
        assertThat(checked).isEqualTo(2000);
    }

    // ==================== 波次：并行执行计划 ====================
    //
    // 波次解决的是「顺序对，但太慢」：串行执行 7 个内容智能体实测 42.9s，
    // 而按依赖分波并行只需 10.3s（取最慢的一个）。代价是**深度超过 K 的交接
    // 不再等待上游**，上游产物也就注入不了下游提示词——这个代价必须显式且可见。

    private static List<List<String>> waveKeys(HandoffPlanner.Plan plan) {
        return plan.waves().stream()
                .map(wave -> wave.stream().map(ArtifactKey::key).toList())
                .toList();
    }

    private static int waveOf(HandoffPlanner.Plan plan, ArtifactKey key) {
        for (int i = 0; i < plan.waves().size(); i++) {
            if (plan.waves().get(i).contains(key)) return i;
        }
        return -1;
    }

    private static HandoffPlanner.Handoff edge(ArtifactKey from, ArtifactKey to) {
        return new HandoffPlanner.Handoff(from, to, "", "");
    }

    @Test
    @DisplayName("没有交接时只有一波：7 个内容智能体全部并行")
    void noHandoffsMeansSingleWave() {
        HandoffPlanner.Plan plan = HandoffPlanner.plan(List.of());

        assertThat(plan.waves()).hasSize(1);
        assertThat(plan.waves().get(0)).isEqualTo(ArtifactKey.REORDERABLE);
    }

    @Test
    @DisplayName("长链被压平到 K 波——这是拿交接深度换时延的刻意取舍")
    void longChainIsFlattenedToMaxWaves() {
        // plan -> resource -> reading -> explainer 的自然深度是 4 波，压平到 2 波。
        // mindmap/quiz/codecase 没被声明任何上游，深度为 0，因此和 plan 同在第一波。
        HandoffPlanner.Plan plan = HandoffPlanner.plan(List.of(
                edge(ArtifactKey.PLAN, ArtifactKey.RESOURCE),
                edge(ArtifactKey.RESOURCE, ArtifactKey.READING),
                edge(ArtifactKey.READING, ArtifactKey.EXPLAINER)), 2);

        assertThat(waveKeys(plan)).containsExactly(
                List.of("plan", "mindmap", "quiz", "codecase"),
                List.of("resource", "reading", "explainer"));
        // 链上的相邻两者被拉开到不同波：plan 跑完才轮到 resource
        assertThat(plan.injectable(edge(ArtifactKey.PLAN, ArtifactKey.RESOURCE))).isTrue();
        // 链的下半段被压进同一波，注入不了——这就是压平的代价
        assertThat(plan.injectable(edge(ArtifactKey.RESOURCE, ArtifactKey.READING))).isFalse();
    }

    @Test
    @DisplayName("K=1 时全部并行，不等待任何上游")
    void maxWavesOnePutsEverythingInOneWave() {
        HandoffPlanner.Plan plan = HandoffPlanner.plan(
                List.of(edge(ArtifactKey.PLAN, ArtifactKey.QUIZ)), 1);

        assertThat(plan.waves()).hasSize(1);
        assertThat(plan.waves().get(0)).isEqualTo(ArtifactKey.REORDERABLE);
    }

    @Test
    @DisplayName("波次是内容段的划分：不重不漏")
    void wavesPartitionTheContentAgents() {
        HandoffPlanner.Plan plan = HandoffPlanner.plan(List.of(
                edge(ArtifactKey.PLAN, ArtifactKey.QUIZ),
                edge(ArtifactKey.QUIZ, ArtifactKey.MINDMAP),
                edge(ArtifactKey.RESOURCE, ArtifactKey.READING)));

        List<ArtifactKey> flat = plan.waves().stream().flatMap(List::stream).toList();

        assertThat(flat).containsExactlyInAnyOrderElementsOf(ArtifactKey.REORDERABLE);
        assertThat(flat).hasSize(ArtifactKey.REORDERABLE.size());
    }

    @Test
    @DisplayName("跨波交接才谈得上注入；同波交接一律不可注入")
    void onlyCrossWaveHandoffsAreInjectable() {
        // plan(第1波) -> quiz(第2波)：跨波，上游已跑完，可以注入
        HandoffPlanner.Handoff crossWave = edge(ArtifactKey.PLAN, ArtifactKey.QUIZ);
        // quiz -> mindmap 的自然深度是 2，被 K=2 压到同一波，注入不了
        HandoffPlanner.Handoff flattened = edge(ArtifactKey.QUIZ, ArtifactKey.MINDMAP);
        HandoffPlanner.Plan plan = HandoffPlanner.plan(List.of(crossWave, flattened), 2);

        assertThat(waveOf(plan, ArtifactKey.PLAN)).isLessThan(waveOf(plan, ArtifactKey.QUIZ));
        assertThat(waveOf(plan, ArtifactKey.QUIZ)).isEqualTo(waveOf(plan, ArtifactKey.MINDMAP));
        assertThat(plan.injectable(crossWave)).isTrue();
        assertThat(plan.injectable(flattened)).isFalse();
    }

    @Test
    @DisplayName("压平了交接时说明里要讲清楚，不能只沉默地体现在 injected:false 上")
    void flatteningIsExplainedInTheNote() {
        HandoffPlanner.Plan plan = HandoffPlanner.plan(List.of(
                edge(ArtifactKey.PLAN, ArtifactKey.QUIZ),
                edge(ArtifactKey.QUIZ, ArtifactKey.MINDMAP)), 2);

        assertThat(plan.note()).contains("波");
    }

    @Test
    @DisplayName("没有压平的时候不要谎称压平")
    void noteDoesNotClaimFlatteningWhenNotNeeded() {
        HandoffPlanner.Plan plan = HandoffPlanner.plan(
                List.of(edge(ArtifactKey.PLAN, ArtifactKey.QUIZ)), 2);

        assertThat(plan.note()).doesNotContain("压平");
    }

    @Test
    @DisplayName("成环时回退串行：每个智能体单独一波，顺序即默认顺序")
    void cycleFallsBackToSerialWaves() {
        HandoffPlanner.Plan plan = HandoffPlanner.plan(List.of(
                edge(ArtifactKey.PLAN, ArtifactKey.QUIZ),
                edge(ArtifactKey.QUIZ, ArtifactKey.PLAN)));

        assertThat(plan.waves()).hasSize(ArtifactKey.REORDERABLE.size());
        assertThat(plan.waves()).allSatisfy(wave -> assertThat(wave).hasSize(1));
        assertThat(plan.waves().stream().flatMap(List::stream).toList())
                .isEqualTo(ArtifactKey.REORDERABLE);
    }

    @Test
    @DisplayName("同样的输入永远给出同样的波次")
    void wavesAreDeterministic() {
        List<HandoffPlanner.Handoff> input = List.of(
                edge(ArtifactKey.PLAN, ArtifactKey.QUIZ),
                edge(ArtifactKey.RESOURCE, ArtifactKey.READING));

        assertThat(waveKeys(HandoffPlanner.plan(input)))
                .isEqualTo(waveKeys(HandoffPlanner.plan(input)));
    }

    @Test
    @DisplayName("不变式：任意输入下，波次都是内容段的划分且不超预算")
    void wavesAreAlwaysAPartitionWithinBudget() {
        Random random = new Random(20260914L);
        ArtifactKey[] reorderable = ArtifactKey.REORDERABLE.toArray(new ArtifactKey[0]);
        int checked = 0;

        for (int round = 0; round < 800; round++) {
            int count = random.nextInt(6);
            List<HandoffPlanner.Handoff> input = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                input.add(edge(reorderable[random.nextInt(reorderable.length)],
                        reorderable[random.nextInt(reorderable.length)]));
            }

            for (int k : new int[]{1, 2, 3, 7}) {
                HandoffPlanner.Plan plan = HandoffPlanner.plan(input, k);
                List<ArtifactKey> flat = plan.waves().stream().flatMap(List::stream).toList();

                assertThat(flat)
                        .as("第 %d 轮 K=%d：波次必须是内容段的划分。输入=%s", round, k, input)
                        .containsExactlyInAnyOrderElementsOf(ArtifactKey.REORDERABLE);
                assertThat(plan.waves()).allSatisfy(wave -> assertThat(wave).isNotEmpty());
                checked++;

                // 成环时走的是串行回退：它刻意无视 K（正确性优先于速度），
                // 交接边也不被信任（见 acceptedEdgesAreSatisfiedByTheOrder）。
                // 下面两条只在无环的压平路径上成立。
                if (plan.note().contains("回退")) {
                    assertThat(waveKeys(plan)).as("回退时每个内容智能体单独一波")
                            .hasSize(ArtifactKey.REORDERABLE.size());
                    continue;
                }

                assertThat(plan.waves())
                        .as("第 %d 轮 K=%d：波数不得超过预算", round, k, input)
                        .hasSizeLessThanOrEqualTo(k);

                // 不可注入的交接只能有一个原因：两端被压到了同一波
                for (HandoffPlanner.Handoff e : plan.accepted()) {
                    if (plan.injectable(e)) continue;
                    assertThat(waveOf(plan, e.from()))
                            .as("不可注入的交接 %s 应落在同一波。输入=%s", e.key(), input)
                            .isEqualTo(waveOf(plan, e.to()));
                }
            }
        }
        assertThat(checked).isEqualTo(800 * 4);
    }

    @Test
    @DisplayName("生效的交接边在最终顺序里确实被满足")
    void acceptedEdgesAreSatisfiedByTheOrder() {
        Random random = new Random(7L);
        ArtifactKey[] reorderable = ArtifactKey.REORDERABLE.toArray(new ArtifactKey[0]);

        for (int round = 0; round < 500; round++) {
            List<HandoffPlanner.Handoff> input = new ArrayList<>();
            int count = random.nextInt(5);
            for (int i = 0; i < count; i++) {
                input.add(new HandoffPlanner.Handoff(
                        reorderable[random.nextInt(reorderable.length)],
                        reorderable[random.nextInt(reorderable.length)], "", ""));
            }

            HandoffPlanner.Plan plan = HandoffPlanner.plan(input);
            // 回退到默认顺序时，交接边**本来就不保证被满足**——这是刻意的取舍：
            // 宁可顺序退回默认，也不让流水线因为模型产出一个环就卡死。
            if (plan.note().contains("回退")) continue;

            for (HandoffPlanner.Handoff edge : plan.accepted()) {
                assertThat(plan.order().indexOf(edge.from()))
                        .as("声明了 %s，但顺序里 %s 并不在 %s 之前。输入=%s",
                                edge.key(), edge.from(), edge.to(), input)
                        .isLessThan(plan.order().indexOf(edge.to()));
            }
        }
    }
}
