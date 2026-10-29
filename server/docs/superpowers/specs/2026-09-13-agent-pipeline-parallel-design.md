# 多智能体工作台流水线提速设计（分层并行 + 场景级模型路由）

- 日期：2026-09-13
- 涉及仓库：`zya-server`（编排与模型网关）、`zya-web`（工作台展示）
- 目标：把「学习多智能体工作台」一次完整流水线的执行时间从**分钟级压到 1 分钟以内**，采用异步（并行）执行

---

## 1. 背景与实测基线

### 1.1 现状

`AgentPipelineService.runPipeline`（`AgentPipelineService.java:172-326`）串行执行约 11–14 次大模型调用：

```
diagnose → coordinate → [7 个内容智能体，串行 for 循环] → review → revise(≤2) → reviewFinal → assemble
```

内容段的串行来自 `AgentPipelineService.java:216-225` 的 `for (ArtifactKey key : handoffPlan.order())` 循环。
`HandoffPlanner` 已经把协调智能体声明的交接关系算成了一张 DAG，但**只用于生成一条线性顺序**，没有用于并行调度。

### 1.2 实测数据（来源：`zhzyai.ai_call_log` / `zhzyai.pipeline_run`）

线程池大小：`AgentPipelineService.java:47` 为 `Executors.newFixedThreadPool(3)`。

**按模型分组的吞吐（`ai_call_log`）**

| 模型 | 调用数 | 平均耗时 | 吞吐 |
|---|---|---|---|
| `deepseek-v4-pro`（当前配置） | 63 | 63.7s | **73 tok/s** |
| `deepseek-flash` | 56 | 12.7s | **212 tok/s** |

**`deepseek-v4-pro` 下各阶段平均耗时（串行，单次全流水线 ≈ 450s）**

| 阶段 | 耗时 | 阶段 | 耗时 |
|---|---|---|---|
| diagnose | 26.6s | codecase | 40.4s |
| coordinate | 64.6s | explainer | 34.7s |
| plan | 59.0s | review | 14.0s |
| resource | 76.4s | reviewFinal | 9.2s |
| mindmap | 32.1s | assemble | 1.5s |
| quiz | 69.3s | 返工（≤2） | +25~55s |
| reading | 21.0s | | |

**一次成功的 `deepseek-flash` 运行（`pipeline_run` id=11，端到端 94s）逐步耗时**

| 阶段 | 耗时 | 阶段 | 耗时 |
|---|---|---|---|
| diagnose | 2 988 ms | codecase | 10 259 ms |
| coordinate | 11 896 ms | explainer | 8 262 ms |
| plan | 8 102 ms | review | 10 755 ms |
| resource | 5 091 ms | revise_quiz | 5 554 ms |
| mindmap | 1 800 ms | revise_codecase | 10 259 ms |
| quiz | 5 554 ms | reviewFinal | 5 769 ms |
| reading | 3 736 ms | assemble | 1 710 ms |
| | | **合计（串行）** | **91 735 ms** |

### 1.3 根因

1. **耗时 ≈ 输出 token ÷ 模型吞吐**。一次流水线生成约 40 000 输出 token；`deepseek-v4-pro` 只有 73 tok/s，光生成就 ~550s。这是天花板，且与串并行无关。
2. **7 个内容智能体串行执行**，占 ~333s（v4-pro）/ 42.9s（flash）。
3. **线程池只有 3 个线程**，直接改并行会排队跑不开，且与编排线程共用同一个池存在**饥饿死锁**风险（见 §6.3）。

---

## 2. 目标与非目标

### 目标

- **无返工路径**端到端 ≤ 60s（预期 ≈ 51.6s）。
- **有返工路径**端到端 ≈ 61.9s。**这一条已知未达成「1 分钟以内」**，原因是返工是常见路径（实测 4 次评审 3 次触发），而为保留交接语义与不截断输出，返工段无法从关键路径上移除。已在 §11 列为遗留项。
- 不减少智能体数量、不截断模型输出、不牺牲交接（handoff）语义的前两层。
- 并行化引入的并发缺陷一并修复。

### 非目标

- 不改动前端逐个调用智能体的既有接口（`/agent/plan`、`/agent/resource` …），它们继续可用。
- 不为单个智能体的输出设置 `max_tokens` 上限（已确认不做）。
- 不实现「两阶段异步返回」（先返回资源包、后台补齐）——已确认不做。
- 不修改 `review` / `coordinate` 的提示词以缩短其输出（已确认不做「改提示词」方案）。

---

## 3. 设计决策

| # | 决策 | 依据 |
|---|---|---|
| D1 | **仅流水线场景切换到 `deepseek-flash`**，其余场景（作业批改、学情报告、AI 助手）保持 `deepseek-v4-pro` | flash 吞吐 212 tok/s ≈ v4-pro 的 3 倍；场景级隔离把质量影响面收敛到流水线 |
| D2 | **内容段按 DAG 波次并行**，且**波次上限 K=2**（可配） | 真实交接链会形成 4 波，内容段 31.8s；压到 2 波后 18.4s |
| D3 | **被压平的交接如实标记为 `injected: false`**，不假装注入过 | 沿用仓库既有做法：`handoffRecords` 已用 `digestChars`/`injected` 证明「交接确实生效」 |
| D4 | **并行 worker 不直接推 SSE**，由编排线程用 `ExecutorCompletionService.take()` 统一按到达顺序广播 | 让所有 `broadcast` 发生在单一线程，从结构上消除 `SseEmitter` 并发写问题 |
| D5 | **编排与 worker 分离为两个线程池** | 避免「编排线程等 worker、worker 无线程可跑」的饥饿死锁 |
| D6 | 产出的资源包内容与字段结构**保持不变** | 前端 `AgentWorkbench.vue` 已按事件驱动（`applyStepData`），无需为并行改造数据结构 |

### 为什么选 K=2（而不是严格 DAG 或全并行）

用真实交接数据计算的对比（内容段 = 7 个内容智能体，耗时取 flash 实测值）：

| 运行 | 交接边数 | 波次 | 内容段耗时 | 全流水线 |
|---|---|---|---|---|
| run 11 | 0 条 | 1 波 | 10.3s | 53.8s |
| run 17 | 8 条 | 4 波 | 31.8s | 75.3s |
| run 15 | 6 条 | 4 波 | 32.3s | 75.8s |

- **严格尊重 DAG**：语义最完整，但 75s 不达标。
- **完全并行（忽略交接顺序）**：10.3s 最快，但会废掉仓库刻意构建并测试过的「交接驱动执行」能力（`AgentHandoffWiringTest`、`AgentPipelineCollaborationTest` 守着它）。
- **有界波次 K=2**：`plan` 先跑，其余 6 个与 `plan` 并行；深度超过 2 的交接不再等待上游。内容段 18.4s，是保语义与达标之间的折中点。

**预期时间线（flash + K=2）**

```
diagnose 3.0 → coordinate 11.9 → [wave1: plan 8.1] → [wave2: 其余6个，取最慢 10.3]
→ review 10.8 → [返工并行 10.3] → reviewFinal 5.8 → assemble 1.7
```

- 无返工：**≈ 51.6s**
- 有返工：**≈ 61.9s**

> 说明：返工是否触发取决于评审结论。实测 4 次 `agent.review` 中有 3 次触发了返工，因此 **61.9s 才是常见值**，而不是 51.6s。这一点必须在验收时如实呈现，不能只报无返工的乐观值。若要进一步压缩返工路径，可选手段见 §11。

---

## 4. 架构：波次调度

### 4.1 `HandoffPlanner` 新增波次计算

在 `HandoffPlanner.Plan` 上新增：

```java
/** 内容段的分波执行计划；每一波内部的智能体互不依赖，可并行执行。 */
public List<List<ArtifactKey>> waves();
```

**算法**：

1. 只取 `REORDERABLE`（7 个内容智能体）与 `accepted` 边。
2. 用最长路径深度算层级：`depth(k) = max(depth(上游) + 1)`，无上游为 0。
3. **压平到 K 波**：`wave(k) = min(depth(k), K - 1)`。K 由 `app.agent-pipeline.max-waves` 提供，取值下限 1（K=1 等价于全部并行、不等待任何上游），非法值（< 1）按 1 处理。
4. **成环时回退**：若拓扑排序不完整（`HandoffPlanner.java:121-123` 已判定），返回「每个智能体单独一波」的串行计划，行为与今天一致。

**不变式**（需测试覆盖）：

- `waves()` 中的所有元素去重后恰好等于 `ArtifactKey.REORDERABLE`，不重不漏。
- `waves().size() <= K`。
- 相同输入永远得到相同结果（确定性）。
- 无交接时 `waves()` 为单波，包含全部 7 个。

### 4.2 执行流程

`AgentPipelineService.runPipeline` 的内容段从 for 循环改为波次循环：

```
for (List<ArtifactKey> wave : plan.waves()) {
    1. 取消检查（沿用现有 isCancelled 语义）
    2. 取该波各智能体的上游摘要 digest（仅来自已完成的波 → 被压平的边天然拿不到 digest）
    3. 提交该波全部任务到 worker 池（ExecutorCompletionService）
    4. 循环 take() 波内任务数：
       - 每个到达即合并进 steps、persistSteps、广播 done 事件
       - 取消检查：已取消则丢弃结果
    5. 任一任务失败 → 按 REORDERABLE 顺序取首个失败，标记 error 并终止
}
```

**关键点**：

- `steps`（`LinkedHashMap`）与 `persistSteps` **只由编排线程操作**，无需加锁。
- 每个智能体完成即落库、即广播，保留「刷新页面不丢进度」与「逐个完成可见」的既有体验（用 `ExecutorCompletionService.take()` 而非 `allOf().join()`，后者会等到整波结束才一次性可见）。
- 返工段（`revise_<agentKey>`）同样改为并行：当前最多 2 个返工是串行跑的（`AgentPipelineService.java:235-257`），实测 15.8s，并行后 10.3s。

### 4.3 交接摘要的诚实记录

`handoffRecords` / `handoffLayout` 已经输出 `injected` 与 `digestChars`。被 K 压平的交接会自然得到 `injected: false`、`digestChars: 0`，**不需要额外代码**——这正是既有字段设计的价值。

额外新增一段可读说明，写入 `collaboration.handoffs.note`：

> 为控制时延，交接压缩为 2 波；深度超过 2 的交接不再等待上游（injected:false）。

---

## 5. 模型路由（场景级）

### 5.1 新增 `com.ai.AiModelRouter`

```java
/** 按场景前缀选择模型；未命中则用 ai.zhipu.model 的默认值。 */
public String modelFor(String scene, String defaultModel);
```

与 `com.ai.AiPrompts` 同包，都属于「AI 层策略」，由 `AiSafeCallService` 持有。

### 5.2 配置

```yaml
app:
  ai-routing:
    # 场景前缀 → 模型。前缀按最长匹配优先，未命中回落到 ai.zhipu.model。
    rules:
      - prefix: "agent."
        model: ${AI_PIPELINE_MODEL:deepseek-flash}
```

用 `@ConfigurationProperties(prefix = "app.ai-routing")` 绑定 `List<Rule>`。
不硬编码 `agent.` 前缀——将来要让 `insight.*` 也走快模型时只改配置。

### 5.3 贯通调用链

`ZhipuAIService` 新增重载，默认值语义不变：

```
callPublic(system, user)                    // 保持，等价于 callPublic(system, user, null)
callPublic(system, user, modelOverride)     // 新增
  → callZhipuAIWithRetry(system, user, modelOverride)
    → callZhipuAI(system, user, modelOverride)
```

- 生效模型 = `modelOverride` 非空且非空白 ? `modelOverride` : `modelName`。
- `requestBody.put("model", effectiveModel)`，日志打印生效模型（便于排查）。
- `callStream` 同样加 `modelOverride` 重载，保持 `callStreamText` 一致（`chat` 场景不命中 `agent.` 前缀，行为不变）。
- `CallMetrics.model` 取的是**响应**里的 model 字段，因此 `ai_call_log.model` 会自动记录路由后的模型——**这给了我们验证路由生效的可查证据**。

`AiSafeCallService` 注入 `AiModelRouter`，在 `callText` / `callJson` / `callJsonRaw` / `callStreamText` 中传入 `router.modelFor(scene, aiService.getConfiguredModel())`。

---

## 6. 并发正确性修复

并行化会把以下问题从「潜在」变成「必现」，必须一并修。

### 6.1 `CourseKnowledgeContextService` 的 `embeddingService` 竞态

`CourseKnowledgeContextService.java:192` 在**无同步**的情况下写 `@Autowired` 字段：

```java
if (embeddingService == null) embeddingService = new LocalEmbeddingService();
```

7 路并发下多个线程会各自 `new LocalEmbeddingService()`，且存在可见性问题。

**修法**（保持测试可用——测试用 `new CourseKnowledgeContextService()` + `ReflectionTestUtils.setField`，所以字段不能改成 `final`）：

- 字段加 `volatile`。
- 新增 `private synchronized EmbeddingService embedding()`，内部做一次判空初始化并返回；`build()` 里的 `embeddingService.embed(...)` 改用它。
- `retrievalStrategy()` 保留现有的 `defaultStrategy` 双重检查锁，但把块内的 `if (embeddingService == null) embeddingService = new LocalEmbeddingService();` 换成 `embedding()` 调用，并让 `HybridRetrievalStrategy` 绑定 `embedding()` 返回的实例。
- 可选：加 `@PostConstruct` 预热，让 Spring 环境下首次请求不承担初始化成本。注意测试用 `new CourseKnowledgeContextService()` 直接构造，此方法不会执行，所以逻辑正确性不能依赖它。

### 6.2 `SseEmitter` 并发写

**不做加锁**，而是从结构上消除：按 D4，所有 `broadcast` 只发生在编排线程（worker 只返回结果）。`take()` 循环保证按到达顺序逐个广播。

### 6.3 线程池：编排与 worker 分离

现状单池 `fixed(3)` 且编排任务与 worker 任务同池 → 若多个 run 同时进行，编排线程会占满线程并阻塞等待 worker，而 worker 无线程可跑。**这是死锁，不只是慢**。

**新结构**：

| 池 | 默认大小 | 用途 |
|---|---|---|
| `pipelineOrchestrator` | 4 | 每个 run 一个编排线程（阻塞在 `take()` 上） |
| `agentWorker` | `app.agent-pipeline.worker-threads`（默认 8） | 执行单个智能体调用 |

另加**全局并发闸门** `Semaphore`（`app.agent-pipeline.max-concurrent-ai-calls`，默认 8），限制跨 run 的同时大模型调用数，保护上游 API 与成本。

### 6.4 看门狗（可选，建议纳入）

实测存在卡死运行：`pipeline_run` id=20 自 19:25:10 起 934s 仍为 `running`。建议新增一个定时清理：超过 `app.agent-pipeline.stale-timeout-ms`（默认 600 000）仍是 `running` 的运行标记为 `error`，并广播终止事件，避免「停止生成」按钮失效、UI 永久转圈。

---

## 7. 失败与取消语义

| 场景 | 行为 |
|---|---|
| 波内任一智能体失败 | 等待该波剩余任务到达后，按 `REORDERABLE` 顺序取首个失败，置 `error` 并终止；已完成的波结果保留在库中 |
| 取消 | 波间检查取消标志；在途请求无法强制中断（既有约束），返回后丢弃结果、不入库、不覆盖 `cancelled` 状态 |
| 编排异常 | 沿用现有 `catch` → `finish(error)` → `broadcast(fail)` |

**行为变化提示**：串行时某步失败会阻止后续步骤启动；并行后同一波内已提交的智能体会跑完（消耗 token）但结果丢弃。这是并行化的固有代价，需在评审时确认可接受。

---

## 8. 前端改动（`zya-web`）

### 8.1 进度条不再是线性链

`AgentWorkbench.vue:60-77` 把 12 个智能体渲染成一条带连接线的线性链。并行后会有多个节点同时 `running`，线性链的语义失真。

**改动**：

- 保留节点行与各节点状态（`idle/running/done/error` 已按节点独立维护，天然支持并发）。
- 把连接线改为**波次分组**：同一波的节点归为一组并标注「第 N 波 · 并行」。
- 节点状态文案已有「运行中」；保留。

### 8.2 总耗时计时器

在控制栏（`AgentWorkbench.vue:30-38` 的按钮区）显示 `已用 42s`，从启动到 `done` 事件；配合 `pipeline_run` 的墙钟时间可交叉验证。

### 8.3 注释与测试

- `src/api/agent.js:1-7` 的注释「各智能体依次调用大模型，一轮跑完以分钟计」需更新。
- `src/utils/__tests__/agentCollaboration.test.js` 需同步（若涉及波次数据）。

---

## 9. 配置项汇总

| 配置 | 默认 | 说明 |
|---|---|---|
| `app.ai-routing.rules[0].model` | `deepseek-flash`（env `AI_PIPELINE_MODEL`） | 流水线场景模型 |
| `app.agent-pipeline.max-waves` | 2 | 波次上限 K |
| `app.agent-pipeline.worker-threads` | 8 | 智能体 worker 池大小 |
| `app.agent-pipeline.orchestrator-threads` | 4 | 并发 run 上限 |
| `app.agent-pipeline.max-concurrent-ai-calls` | 8 | 全局 AI 调用并发闸门 |
| `app.agent-pipeline.stale-timeout-ms` | 600000 | 看门狗判定卡死的阈值 |

---

## 10. 测试与验收

### 10.1 单元测试

| 对象 | 用例 |
|---|---|
| `HandoffPlanner.waves()` | 无交接受→单波 7 个；K=2 压平；成环回退串行；不重不漏；确定性；`waves().size() <= K` |
| `AiModelRouter` | `agent.*` 命中；`chat`/`insight.*` 不命中回落默认；空前缀/空模型容错 |
| `ZhipuAIService` 重载 | 传 `null`/空白时用默认模型；传值时用覆盖值 |
| `CourseKnowledgeContextService` | 并发调用 `build()` 只创建一个 `LocalEmbeddingService`（用反射计数或传入计数型假实现） |
| `AgentPipelineService` | 波次执行顺序；被压平的边 `injected=false`；返工并行 |

### 10.2 必须保持通过的既有测试

- `com/agent/HandoffPlannerTest`（含随机输入的属性测试，断言 `order()` 是 `DEFAULT_ORDER` 的排列）
- `com/service/AgentHandoffWiringTest`（digest 注入、`injected`/`digestChars` 记录、布局分层）
- `com/service/AgentPipelineCollaborationTest`（协调分配、返工上限、回放）
- 安全回归测试群（`OwnershipBindingTest`、`AuthorizationRegressionTest` 等）

### 10.3 验收：以实测墙钟为准

速度目标必须用真实测量验收，不能只靠单测。步骤：

1. 用 flash + 并行配置启动后端，跑一次完整流水线。
2. 读 `pipeline_run` 的 `create_time → update_time` 得到端到端墙钟时间。
3. 读 `steps_json` 中各步骤 `elapsedMs`，对比设计预期（§3 时间线）。
4. 读 `ai_call_log.model` 确认路由生效（应为 `deepseek-flash`）。

**验收标准**：

| 路径 | 标准 | 预期 |
|---|---|---|
| 无返工 | 端到端 ≤ 60s | ≈ 51.6s |
| 有返工 | 端到端 ≤ 65s（**已知 > 60s，属遗留项**） | ≈ 61.9s |
| 模型路由 | `ai_call_log` 中 `agent.*` 场景的 `model` = `deepseek-flash`，其余场景仍为 `deepseek-v4-pro` | — |

必须**分别报告两条路径的实测值**，只报无返工值等于掩盖了常见情况。

### 10.4 构建与静态检查

- 前端：`npm run verify`（含 `lint:ci`、`check:tokens`、`test`、`build`）。
- 后端：`mvn verify`（JaCoCo 报告；集成测试依赖 Docker，无 Docker 会静默跳过——本次改动不涉及迁移，跳过可接受）。

---

## 11. 风险与遗留

| 风险 | 影响 | 处置 |
|---|---|---|
| flash 生成质量低于 v4-pro | 流水线产物质量下降 | 已确认场景级隔离；若质量不可接受，可回退 `AI_PIPELINE_MODEL=deepseek-v4-pro`（一行配置，无需改代码） |
| **有返工路径 ~61.9s 超 1 分钟** | 常见路径（实测 3/4）未达标 | 本轮已确认接受。后续可选：① 把「返工 + 复审」移出关键路径（先入库展示资源包，返工完成后回填更新——需 `learning_resource` 支持 update）；② 收紧 `coordinate` 提示词让交接链更浅、把内容段从 2 波压到 1 波（省 8.1s）；③ 评审要求返工时只返工 1 个产物（省 0s，因返工已并行）。推荐 ①，收益最大且不牺牲产出 |
| 波内失败仍消耗 token | 成本轻微上升 | 已确认接受（§7） |
| 上游 API 对 7 路并发的限流 | 部分调用 429 | 靠 `max-concurrent-ai-calls` 闸门 + 既有 `ZhipuAIService.callZhipuAIWithRetry`（2 次退避、尊重 `Retry-After`） |
| 多实例部署下限流失效 | 闸门与取消标志为进程内状态 | 同 `AiRateLimitInterceptor` 的既有约束，不新增 |
