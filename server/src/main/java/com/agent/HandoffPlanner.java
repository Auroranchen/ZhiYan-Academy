package com.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * 把协调智能体声明的「交接关系」变成实际执行顺序。
 *
 * <p><b>这是「让 handoffs 真正被消费」的核心：</b>此前 {@code coordinate} 会产出
 * {@code handoffs} 字段，但只有前端在读它做展示——也就是说那些交接对执行毫无影响，
 * 是纯粹的装饰。本类把它变成数据依赖：声明的交接会真的改变下游调用顺序，
 * 并且上游产物会被注入下游的提示词。
 *
 * <p><b>关键设计取舍：</b>默认顺序<b>不作为图的边</b>，只作两件事——
 * 平局时的裁决依据，以及成环时的回退去处。若把默认顺序也当成边加入，
 * 由于它本身是一条全序链，任何与它不一致的交接都会立刻成环，
 * 交接就永远无法生效了。
 *
 * <p><b>不变式：</b>输出永远是 {@link ArtifactKey#DEFAULT_ORDER} 的一个排列——
 * 不跳过任何步骤，也不发明新步骤。模型给出再离谱的交接，最坏也只是回退到默认顺序。
 */
public final class HandoffPlanner {

    private HandoffPlanner() {
    }

    /** 一条交接：{@code from} 的产物交给 {@code to} 使用。 */
    public record Handoff(ArtifactKey from, ArtifactKey to, String artifact, String reason) {

        public String key() {
            return from.key() + "->" + to.key();
        }
    }

    /** 被丢弃的交接及原因。要展示给用户看——静默丢弃会让人以为模型没产出交接。 */
    public record Rejected(String from, String to, String reason) {
    }

    /**
     * @param order    最终执行顺序（DEFAULT_ORDER 的排列）
     * @param accepted 生效的交接边
     * @param rejected 被丢弃的交接及原因
     * @param note     需要展示给用户的说明（如「检测到环，已回退默认顺序」）
     * @param waves    内容段的并行执行波次；同一波内的智能体互不等待
     */
    public record Plan(List<ArtifactKey> order, List<Handoff> accepted,
                       List<Rejected> rejected, String note, List<List<ArtifactKey>> waves) {

        /** 内容段的实际顺序，便于对照默认顺序看是否真的被重排了。 */
        public List<ArtifactKey> contentOrder() {
            return order.stream().filter(ArtifactKey::reorderable).toList();
        }

        public boolean reordered() {
            return !contentOrder().equals(ArtifactKey.REORDERABLE);
        }

        /**
         * 该交接的上游是否真的能先跑完，从而把产物注入下游提示词。
         *
         * <p>只有当上游位于**更早的波次**时才成立。被 K 压平到同一波的交接拿不到上游产物，
         * 调用方必须如实把它记成 {@code injected:false}，而不是假装注入过。
         */
        public boolean injectable(Handoff edge) {
            int from = waveIndexOf(waves, edge.from());
            int to = waveIndexOf(waves, edge.to());
            return from >= 0 && to >= 0 && from < to;
        }

        /** 内容段被压平成了几波。等于 {@code waves().size()}，单独开个名字让调用处读起来是自解释的。 */
        public int waveCount() {
            return waves.size();
        }
    }

    /**
     * 从模型产出的原始结构里解析交接声明。
     *
     * <p>宽容解析：模型可能给出字符串、Map、或缺失字段。解析不了的直接忽略，
     * 因为它们最终都要经过 {@link #plan} 的校验，这里不必重复报错。
     */
    @SuppressWarnings("unchecked")
    public static List<Handoff> parse(Object rawHandoffs) {
        List<Handoff> parsed = new ArrayList<>();
        if (!(rawHandoffs instanceof List<?> list)) return parsed;
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> entry = (Map<String, Object>) map;
                Optional<ArtifactKey> from = ArtifactKey.of(str(entry.get("from")));
                Optional<ArtifactKey> to = ArtifactKey.of(str(entry.get("to")));
                if (from.isEmpty() || to.isEmpty()) continue;
                parsed.add(new Handoff(from.get(), to.get(),
                        str(entry.get("artifact")), str(entry.get("reason"))));
            } else if (item instanceof String text) {
                // 兼容 "plan->quiz" 这种简写
                int arrow = text.indexOf("->");
                if (arrow > 0) {
                    Optional<ArtifactKey> from = ArtifactKey.of(text.substring(0, arrow));
                    Optional<ArtifactKey> to = ArtifactKey.of(text.substring(arrow + 2));
                    if (from.isPresent() && to.isPresent()) {
                        parsed.add(new Handoff(from.get(), to.get(), "", ""));
                    }
                }
            }
        }
        return parsed;
    }

    /** 默认波次上限：内容段最多分几波执行。压得越平越快，代价是深度交接不再等待上游。 */
    public static final int DEFAULT_MAX_WAVES = 2;

    public static Plan plan(List<Handoff> requested) {
        return plan(requested, DEFAULT_MAX_WAVES);
    }

    public static Plan plan(List<Handoff> requested, int maxWaves) {
        List<Handoff> accepted = new ArrayList<>();
        List<Rejected> rejected = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Handoff handoff : requested == null ? List.<Handoff>of() : requested) {
            if (handoff.from() == handoff.to()) {
                rejected.add(new Rejected(handoff.from().key(), handoff.to().key(), "自环"));
                continue;
            }
            if (!handoff.from().reorderable() || !handoff.to().reorderable()) {
                // 结构步骤的顺序由代码保证：诊断必须最先、评审必须最后
                rejected.add(new Rejected(handoff.from().key(), handoff.to().key(),
                        "结构步骤不可参与交接重排"));
                continue;
            }
            if (!seen.add(handoff.key())) {
                rejected.add(new Rejected(handoff.from().key(), handoff.to().key(), "重复声明"));
                continue;
            }
            accepted.add(handoff);
        }

        List<ArtifactKey> contentOrder = topoSort(accepted);
        boolean cyclic = contentOrder.size() != ArtifactKey.REORDERABLE.size();
        List<ArtifactKey> finalContent = cyclic ? ArtifactKey.REORDERABLE : contentOrder;

        List<ArtifactKey> order = new ArrayList<>(ArtifactKey.DEFAULT_ORDER.size());
        order.addAll(ArtifactKey.FIXED_PREFIX);
        order.addAll(finalContent);
        order.addAll(ArtifactKey.FIXED_SUFFIX);

        List<List<ArtifactKey>> waves = cyclic
                ? serialWaves()
                : layOutWaves(accepted, maxWaves);

        StringBuilder note = new StringBuilder(cyclic
                ? "交接关系构成环，内容段已回退为默认顺序"
                : (finalContent.equals(ArtifactKey.REORDERABLE)
                ? "交接关系与默认顺序一致，未发生重排"
                : "已按交接关系重排内容段"));
        // 压平是「为了时延而牺牲交接深度」，必须说出来。成环回退时不提：
        // 那时交接边本来就不被信任，压平与否不是它们失效的原因。
        if (!cyclic) {
            long dropped = accepted.stream()
                    .filter(edge -> waveIndexOf(waves, edge.from()) >= waveIndexOf(waves, edge.to()))
                    .count();
            if (dropped > 0) {
                note.append("；为控制时延，交接已压平为 ").append(waves.size())
                        .append(" 波，其中 ").append(dropped)
                        .append(" 条深度交接不再等待上游（injected:false）");
            }
        }

        return new Plan(List.copyOf(order), List.copyOf(accepted), List.copyOf(rejected),
                note.toString(), waves);
    }

    private static int waveIndexOf(List<List<ArtifactKey>> waves, ArtifactKey key) {
        for (int i = 0; i < waves.size(); i++) {
            if (waves.get(i).contains(key)) return i;
        }
        return -1;
    }

    /**
     * 按依赖深度把内容段分层，并压平到 {@code maxWaves} 波。
     *
     * <p>深度取的是 DAG 上的**最长路径**：只有所有上游都跑完，某一波才谈得上开始。
     * 超过预算的深度一律截断到最后一波，于是「深链」被摊平成一批可并行的节点——
     * 这正是拿交接深度换时延的地方。
     *
     * <p>只应在**无环**时调用（调用方已在成环时改走 {@link #serialWaves()}）；
     * 迭代仍以节点数封顶，避免万一传入带环图时死循环。
     */
    private static List<List<ArtifactKey>> layOutWaves(List<Handoff> edges, int maxWaves) {
        int budget = Math.max(1, maxWaves);
        Map<ArtifactKey, Integer> depth = new LinkedHashMap<>();
        for (ArtifactKey key : ArtifactKey.REORDERABLE) {
            depth.put(key, 0);
        }
        for (int round = 0; round < ArtifactKey.REORDERABLE.size(); round++) {
            boolean changed = false;
            for (Handoff edge : edges) {
                int candidate = depth.get(edge.from()) + 1;
                if (depth.get(edge.to()) < candidate) {
                    depth.put(edge.to(), candidate);
                    changed = true;
                }
            }
            if (!changed) break;
        }

        // TreeMap 保证按波号升序输出，且不会产生空波
        Map<Integer, List<ArtifactKey>> grouped = new TreeMap<>();
        for (ArtifactKey key : ArtifactKey.REORDERABLE) {
            grouped.computeIfAbsent(Math.min(depth.get(key), budget - 1), ignored -> new ArrayList<>())
                    .add(key);
        }
        return grouped.values().stream().map(List::copyOf).toList();
    }

    /** 成环时的回退：每个内容智能体单独一波，执行顺序与重构前的串行行为完全一致。 */
    private static List<List<ArtifactKey>> serialWaves() {
        return ArtifactKey.REORDERABLE.stream().map(List::of).toList();
    }

    /**
     * 在内容段上做 Kahn 拓扑排序。
     *
     * <p>平局时按默认顺序取——这保证「没有交接」时结果就是默认顺序，
     * 也保证同样的输入永远得到同样的顺序（评测与演示都要求确定性）。
     *
     * <p>成环时返回的元素个数会少于节点总数，由调用方回退。这里不抛异常：
     * 模型产出环是常见情况，不该让整条流水线失败。
     */
    private static List<ArtifactKey> topoSort(List<Handoff> edges) {
        Map<ArtifactKey, Integer> inDegree = new LinkedHashMap<>();
        Map<ArtifactKey, List<ArtifactKey>> adjacency = new HashMap<>();
        for (ArtifactKey key : ArtifactKey.REORDERABLE) {
            inDegree.put(key, 0);
            adjacency.put(key, new ArrayList<>());
        }
        for (Handoff edge : edges) {
            // 重复边已在上面去重，这里不会出现同一条边计两次
            adjacency.get(edge.from()).add(edge.to());
            inDegree.merge(edge.to(), 1, Integer::sum);
        }

        Comparator<ArtifactKey> byDefaultOrder =
                Comparator.comparingInt(ArtifactKey.REORDERABLE::indexOf);

        List<ArtifactKey> sorted = new ArrayList<>(ArtifactKey.REORDERABLE.size());
        Set<ArtifactKey> remaining = new LinkedHashSet<>(ArtifactKey.REORDERABLE);
        while (!remaining.isEmpty()) {
            ArtifactKey next = remaining.stream()
                    .filter(key -> inDegree.get(key) == 0)
                    .min(byDefaultOrder)
                    .orElse(null);
            if (next == null) break; // 成环，交给调用方回退
            sorted.add(next);
            remaining.remove(next);
            for (ArtifactKey downstream : adjacency.get(next)) {
                inDegree.merge(downstream, -1, Integer::sum);
            }
        }
        return sorted;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
