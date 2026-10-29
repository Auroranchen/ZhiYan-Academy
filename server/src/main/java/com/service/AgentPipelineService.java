package com.service;

import com.agent.ArtifactKey;
import com.agent.HandoffPlanner;
import com.config.AgentPipelineProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repository.PipelineRunRepository;
import com.response.Result;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 多智能体服务端流水线编排。
 * 相比前端逐个调用智能体接口的方式：
 *  1. 编排在服务端一次完成，每步结果实时落库（pipeline_run.steps_json），刷新页面不丢进度
 *  2. 通过 SSE 向前端流式推送每个智能体的启动/完成事件与产物，满足「生成进度追踪 / 流式呈现」
 *  3. 支持取消与「最近一次运行」查询，前端可断线重连并回放已完成步骤
 */
@Service
public class AgentPipelineService {

    private static final Logger logger = LoggerFactory.getLogger(AgentPipelineService.class);
    private static final long SSE_TIMEOUT_MS = 15L * 60 * 1000;

    @Autowired
    private LearningAgentService agentService;

    @Autowired
    private PipelineRunRepository pipelineRunRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Integer, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    /**
     * 编排线程池与 worker 池**必须分开**：编排线程会阻塞等待本波次的 worker 任务，
     * 若两者共用同一个池，多用户并发时 worker 任务将排在这些阻塞的编排线程之后拿不到线程，
     * 表现为整条流水线彻底卡死。
     */
    @Autowired
    @Qualifier("agentPipelineOrchestrator")
    private Executor orchestratorExecutor;

    @Autowired
    @Qualifier("agentPipelineWorker")
    private Executor workerExecutor;

    @Autowired
    private AgentPipelineProperties properties;

    /** 跨 run 的大模型并发闸门，保护上游 API 与成本。见 {@link #throughAiCallGate}。 */
    private volatile Semaphore aiCallGate;

    @PostConstruct
    public void init() {
        aiCallGate = new Semaphore(Math.max(1, properties.getMaxConcurrentAiCalls()), true);
        pipelineRunRepository.markInterruptedRunsAsError();
    }

    /**
     * 看门狗：把长时间没有推进的运行判为卡死并收尾。
     *
     * <p>只改数据库状态是不够的——前端靠 SSE 流才知道运行已经结束，不关流页面会一直
     * 转圈，「停止生成」也因为运行早已不在而无法生效。所以这里要一并广播终止事件、
     * 关闭流、清掉取消标志。
     *
     * <p>实测出现过 934s 仍为 running 的运行，这个兜底就是为了它。
     */
    @Scheduled(fixedDelayString = "${app.agent-pipeline.watchdog-interval-ms:60000}")
    public void sweepStaleRuns() {
        List<Integer> swept = pipelineRunRepository.markStaleRunsAsError(properties.getStaleTimeoutMs());
        for (Integer runId : swept) {
            broadcast(runId, "fail", Map.of("message", "运行超时未推进，已自动终止"));
            completeEmitters(runId);
            cancelFlags.remove(runId);
        }
    }

    // ==================== 启动 ====================

    public synchronized Result<Map<String, Object>> start(String ownerType, Integer ownerId, Integer courseId,
                                                          int quizCount, List<String> requestedFocusPoints) {
        // 同一用户仅允许一条运行中的流水线，重复启动直接返回既有运行（前端可重连）
        Integer attachedRunId = pipelineRunRepository.findRunningId(ownerType, ownerId).orElse(null);
        if (attachedRunId != null) {
            Map<String, Object> out = new HashMap<>();
            out.put("runId", attachedRunId);
            out.put("attached", true);
            return Result.success(out);
        }

        final Integer runId = pipelineRunRepository.create(ownerType, ownerId, courseId, quizCount).orElse(null);
        if (runId == null) return Result.error("创建流水线失败，请稍后重试");

        cancelFlags.put(runId, new AtomicBoolean(false));
        List<String> focusPoints = requestedFocusPoints == null
                ? List.of() : List.copyOf(requestedFocusPoints);
        orchestratorExecutor.execute(
                () -> runPipeline(runId, ownerType, ownerId, courseId, quizCount, focusPoints));

        Map<String, Object> out = new HashMap<>();
        out.put("runId", runId);
        out.put("attached", false);
        return Result.success(out);
    }

    // ==================== SSE 订阅（含已完成步骤回放） ====================

    public SseEmitter stream(Integer runId, String ownerType, Integer ownerId) {
        Map<String, Object> run = findRun(runId, ownerType, ownerId);
        if (run == null) {
            SseEmitter emitter = new SseEmitter(0L);
            sendEvent(emitter, "fail", Map.of("message", "运行不存在或无权访问"));
            emitter.complete();
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable cleanup = () -> list.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // 注册 emitter 之后重新读取运行状态再回放，避免「读取与注册之间完成的步骤」丢失；
        // 步骤事件对前端幂等（重复应用只是覆盖同一状态），晚注册收到的少量重复事件无副作用
        run = findRun(runId, ownerType, ownerId);
        if (run == null) {
            emitter.complete();
            return emitter;
        }

        // 回放已完成步骤，支撑「刷新页面 / 断线重连不丢进度」
        Map<String, Object> steps = parseSteps(run.get("stepsJson"));
        for (Map.Entry<String, Object> entry : steps.entrySet()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("step", entry.getKey());
            payload.put("status", "done");
            payload.put("data", entry.getValue());
            payload.put("replay", true);
            sendEvent(emitter, "step", payload);
        }
        String status = String.valueOf(run.get("status"));
        if (!"running".equals(status)) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("status", status);
            payload.put("resourceId", run.get("resourceId"));
            payload.put("error", run.get("error"));
            sendEvent(emitter, "done".equals(statusEvent(status)) ? "done" : statusEvent(status), payload);
            emitter.complete();
        }
        return emitter;
    }

    private String statusEvent(String status) {
        if ("done".equals(status)) return "done";
        if ("cancelled".equals(status)) return "cancelled";
        return "fail";
    }

    // ==================== 取消 / 查询 ====================

    public Result<Void> cancel(Integer runId, String ownerType, Integer ownerId) {
        Map<String, Object> run = findRun(runId, ownerType, ownerId);
        if (run == null) return Result.error("运行不存在或无权操作");
        AtomicBoolean flag = cancelFlags.get(runId);
        if (flag != null) flag.set(true);
        // 立即更新持久化状态，避免用户取消后再次启动仍被附着到旧流水线
        finish(runId, "cancelled", null, null);
        broadcast(runId, "cancelled", Map.of("status", "cancelled"));
        return Result.success();
    }

    /** 最近一次运行（用于页面加载时恢复状态） */
    public Result<Map<String, Object>> latest(String ownerType, Integer ownerId) {
        Map<String, Object> row = pipelineRunRepository.findLatest(ownerType, ownerId).orElse(null);
        if (row == null) return Result.success(new HashMap<>());

        Map<String, Object> out = new HashMap<>(row);
        out.put("runId", out.remove("id"));
        return Result.success(out);
    }

    // ==================== 流水线执行 ====================

    private void runPipeline(Integer runId, String ownerType, Integer ownerId, Integer courseId, int quizCount,
                             List<String> requestedFocusPoints) {
        boolean teacher = "teacher".equals(ownerType);
        Map<String, Object> steps = new LinkedHashMap<>();
        try {
            // 1. 诊断
            Map<String, Object> diagnose = runStep(runId, steps, "diagnose", () -> teacher
                    ? agentService.diagnoseClass(courseId)
                    : agentService.diagnoseStudent(ownerId, courseId));
            if (diagnose == null) return;

            String subject = str(diagnose.get("subject"));
            List<String> weakPoints = weakPointNames(diagnose.get("weakPoints"));
            // 学习画像评估页传入的重点与实时学情诊断合并，形成“评估 → 生成”闭环
            if (requestedFocusPoints != null) {
                for (String focus : requestedFocusPoints) {
                    if (focus != null && !focus.isBlank() && !weakPoints.contains(focus.trim())) {
                        weakPoints.add(focus.trim());
                    }
                }
            }
            if (weakPoints.isEmpty()) {
                finish(runId, "error", null, "暂无足够学情数据用于诊断");
                broadcast(runId, "fail", Map.of("message", "暂无足够学情数据用于诊断，请先完成作业/考试"));
                return;
            }
            Integer studentId = teacher ? null : ownerId;

            // 2. 协调智能体：拆分职责、分配重点并声明交接关系
            Map<String, Object> coordinate = runStep(runId, steps, "coordinate",
                    () -> agentService.coordinate(subject, weakPoints, teacher, courseId, studentId));
            if (coordinate == null) return;

            // 3-9. 内容智能体：**执行顺序由交接关系决定**，而不是写死的次序。
            //
            // 此前 coordinate 产出的 handoffs 只有前端在读来做展示，对执行毫无影响——
            // 「智能体协作」因此只是一句装饰。现在它成为真实的数据依赖：
            // 声明的交接会改变调用顺序，且上游产物会被注入下游的提示词。
            HandoffPlanner.Plan handoffPlan = HandoffPlanner.plan(
                    HandoffPlanner.parse(coordinate.get("handoffs")), properties.getMaxWaves());
            logger.info("流水线 {} 交接规划: {}（生效 {} 条 / 丢弃 {} 条 / 内容段 {} 波）", runId,
                    handoffPlan.note(), handoffPlan.accepted().size(), handoffPlan.rejected().size(),
                    handoffPlan.waveCount());

            Map<String, String> handoffDigests = new LinkedHashMap<>();
            if (!runContentWaves(runId, steps, handoffPlan, handoffDigests, coordinate, subject,
                    weakPoints, teacher, courseId, studentId, quizCount)) {
                return;
            }

            // 10. 独立评审智能体交叉检查全部产物
            Map<String, Object> firstReview = runStep(runId, steps, "review",
                    () -> agentService.reviewArtifacts(subject, weakPoints, reviewInput(steps), false));
            if (firstReview == null) return;

            // 11. 有限返工：最多一轮、最多两个产物，避免成本与时延失控。
            // 两个返工产物之间没有任何依赖，因此并行发起——实测 15.8s → 10.3s。
            List<Map<String, Object>> revisions = runRevisions(runId, steps, retryAgentKeys(firstReview),
                    subject, weakPoints, firstReview, courseId, studentId);
            if (revisions == null) return;

            Map<String, Object> finalReview;
            if (!revisions.isEmpty()) {
                finalReview = runStep(runId, steps, "reviewFinal",
                        () -> agentService.reviewArtifacts(subject, weakPoints, reviewInput(steps), true));
                if (finalReview == null) return;
            } else {
                finalReview = new HashMap<>(firstReview);
                finalReview.put("agent", "复审智能体");
                finalReview.put("round", 2);
                finalReview.put("notRequired", true);
                finalReview.put("summary", reviewApproved(firstReview)
                        ? "首轮评审已通过，无需返工复审"
                        : "首轮评审未指定可返工产物，保留评审结论并建议教师复核");
                recordStep(runId, steps, "reviewFinal", finalReview);
            }

            // 12. 编排 + 入库
            Map<String, Object> pack = buildPack(subject, weakPoints, steps);
            Map<String, Object> collaboration = new LinkedHashMap<>();
            collaboration.put("strategy", coordinate);
            // 交接规划：这是「智能体真的在协作」的可验证证据。
            // 每条边都带 digestChars 与 injected 标记——能证明上游产物确实进了下游提示词，
            // 而不是只声明了一条关系却什么也没做。
            Map<String, Object> handoffPayload = new LinkedHashMap<>();
            handoffPayload.put("note", handoffPlan.note());
            handoffPayload.put("reordered", handoffPlan.reordered());
            handoffPayload.put("order", handoffPlan.order().stream().map(ArtifactKey::key).toList());
            handoffPayload.put("contentOrder",
                    handoffPlan.contentOrder().stream().map(ArtifactKey::key).toList());
            handoffPayload.put("defaultContentOrder",
                    ArtifactKey.REORDERABLE.stream().map(ArtifactKey::key).toList());
            handoffPayload.put("edges", handoffRecords(handoffPlan, handoffDigests));
            handoffPayload.put("rejected", handoffPlan.rejected().stream().map(r -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("from", r.from());
                item.put("to", r.to());
                item.put("reason", r.reason());
                return item;
            }).toList());
            handoffPayload.put("graph", handoffLayout(handoffPlan, handoffDigests));
            collaboration.put("handoffs", handoffPayload);
            collaboration.put("firstReview", firstReview);
            collaboration.put("revisions", revisions);
            collaboration.put("finalReview", finalReview);
            collaboration.put("sharedState", "pipeline_run.steps_json");
            pack.put("collaboration", collaboration);
            pack.put("qualityReview", finalReview);
            String scopeType = teacher ? "class" : (courseId != null ? "course" : "student");
            Map<String, Object> assembled = runStep(runId, steps, "assemble",
                    () -> agentService.assemble(ownerType, ownerId, scopeType, courseId, pack));
            if (assembled == null) return;

            Integer resourceId = assembled.get("id") instanceof Number ? ((Number) assembled.get("id")).intValue() : null;
            finish(runId, "done", resourceId, null);
            Map<String, Object> donePayload = new HashMap<>();
            donePayload.put("status", "done");
            donePayload.put("resourceId", resourceId);
            donePayload.put("title", assembled.get("title"));
            broadcast(runId, "done", donePayload);
        } catch (Exception e) {
            logger.error("流水线 {} 执行异常: {}", runId, e.getMessage());
            finish(runId, "error", null, e.getMessage());
            broadcast(runId, "fail", Map.of("message", "协作过程中出现错误：" + str(e.getMessage())));
        } finally {
            completeEmitters(runId);
            cancelFlags.remove(runId);
        }
    }

    private interface StepCall {
        Result<Map<String, Object>> invoke();
    }

    /** 一个已提交的并行步骤，以及它完成后的结果。 */
    private record StepOutcome(String step, Result<Map<String, Object>> result) {
    }

    /**
     * 按波次并行执行内容智能体。
     *
     * <p>**这是整条流水线的主要提速点**：7 个内容智能体互不依赖时串行要 42.9s，
     * 并行只需最慢那一个的时间（实测 10.3s）。波与波之间仍然串行——交接声明的依赖
     * 要求上游产物先落地，才能注入下游提示词。
     *
     * <p>波次由 {@link HandoffPlanner.Plan#waves()} 给出，已按 {@code max-waves} 压平；
     * 被压平到同一波的交接拿不到上游产物，会如实记成 {@code injected:false}。
     *
     * <p>**广播只发生在编排线程上**（worker 只返回结果，不碰 SSE）：这样既保留了
     * 「谁先跑完谁先出现在日志里」的实时感，又从结构上避免了多线程并发写同一个
     * {@code SseEmitter} 的竞态。
     *
     * @return false 表示流水线应终止（失败或已取消）
     */
    private boolean runContentWaves(Integer runId, Map<String, Object> steps, HandoffPlanner.Plan plan,
                                    Map<String, String> handoffDigests, Map<String, Object> coordinate,
                                    String subject, List<String> weakPoints, boolean teacher,
                                    Integer courseId, Integer studentId, int quizCount) {
        ExecutorCompletionService<StepOutcome> completion =
                new ExecutorCompletionService<>(workerExecutor);

        for (List<ArtifactKey> wave : plan.waves()) {
            if (isCancelled(runId)) {
                cancelRun(runId);
                return false;
            }

            List<ArtifactKey> submitted = new ArrayList<>(wave);
            for (ArtifactKey key : wave) {
                // 上游摘要只可能来自更早的波次：同波的上游此刻还没跑完，
                // handoffDigest 自然拿到空串，于是 injected 如实为 false
                String digest = handoffDigest(key, plan, steps);
                handoffDigests.put(key.key(), digest);
                broadcast(runId, "step", Map.of("step", key.key(), "status", "running"));
                completion.submit(() -> new StepOutcome(key.key(),
                        throughAiCallGate(() -> dispatchContentAgent(key, coordinate, subject,
                                weakPoints, teacher, courseId, studentId, quizCount, digest))));
            }

            List<StepOutcome> outcomes = new ArrayList<>(submitted.size());
            for (int i = 0; i < submitted.size(); i++) {
                StepOutcome outcome = takeOutcome(completion);
                if (outcome == null) return false;
                outcomes.add(outcome);
                if (isCancelled(runId)) {
                    cancelRun(runId);
                    return false;
                }
                // 到达即落库、即广播：刷新页面不丢进度，用户也能逐个看到完成
                if (isSuccess(outcome.result())) {
                    applyStepSuccess(runId, steps, outcome.step(), outcome.result().getData());
                }
            }

            // 失败判定放在整波收齐之后，并按提交顺序取第一个失败者：
            // 否则「报哪个错」会取决于线程调度，同一份输入两次运行给出不同结果。
            // 也让同波其余智能体自然跑完，不至于留下悬空任务。
            String failed = firstFailure(submitted.stream().map(ArtifactKey::key).toList(), outcomes);
            if (failed != null) {
                reportStepFailure(runId, failed, resultOf(failed, outcomes));
                return false;
            }
        }
        return true;
    }

    /**
     * 并行返工。至多两个产物，彼此独立，因此可以同时发起。
     *
     * <p>返工结果要**覆盖**原产物（写回同一个 key），所以落库与广播统一放在编排线程做，
     * worker 只负责调用与返回——这样 {@code steps} 始终只被一个线程写。
     *
     * @return 返工记录；返回 null 表示流水线应终止
     */
    private List<Map<String, Object>> runRevisions(Integer runId, Map<String, Object> steps,
                                                   List<String> retryAgents, String subject,
                                                   List<String> weakPoints, Map<String, Object> firstReview,
                                                   Integer courseId, Integer studentId) {
        List<Map<String, Object>> revisions = new ArrayList<>();
        if (retryAgents.isEmpty()) return revisions;

        // 先把原产物取出来：worker 拿到的是快照，之后 steps 再被改写也不影响它
        List<String> pending = new ArrayList<>();
        Map<String, Map<String, Object>> previousArtifacts = new LinkedHashMap<>();
        for (String agentKey : retryAgents) {
            Object previous = steps.get(agentKey);
            if (!(previous instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> artifact = (Map<String, Object>) previous;
            pending.add(agentKey);
            previousArtifacts.put(agentKey, artifact);
        }
        if (pending.isEmpty()) return revisions;

        ExecutorCompletionService<StepOutcome> completion =
                new ExecutorCompletionService<>(workerExecutor);
        for (String agentKey : pending) {
            broadcast(runId, "step", Map.of("step", "revise_" + agentKey, "status", "running"));
            Map<String, Object> previous = previousArtifacts.get(agentKey);
            completion.submit(() -> new StepOutcome(agentKey, throughAiCallGate(
                    () -> agentService.reviseArtifact(agentKey, subject, weakPoints, previous,
                            firstReview, courseId, studentId))));
        }

        List<StepOutcome> outcomes = new ArrayList<>(pending.size());
        for (int i = 0; i < pending.size(); i++) {
            StepOutcome outcome = takeOutcome(completion);
            if (outcome == null) return null;
            outcomes.add(outcome);
            if (isCancelled(runId)) {
                cancelRun(runId);
                return null;
            }
            if (!isSuccess(outcome.result())) continue;

            Map<String, Object> revised = outcome.result().getData();
            steps.put(outcome.step(), revised);
            persistSteps(runId, steps);
            Map<String, Object> revisionMeta = new HashMap<>();
            revisionMeta.put("agent", outcome.step());
            revisionMeta.put("step", "revise_" + outcome.step());
            revisionMeta.put("reason", reviewIssue(firstReview, outcome.step()));
            revisions.add(revisionMeta);

            Map<String, Object> payload = new HashMap<>();
            payload.put("step", outcome.step());
            payload.put("status", "done");
            payload.put("data", revised);
            payload.put("revision", true);
            broadcast(runId, "step", payload);
        }

        String failed = firstFailure(pending, outcomes);
        if (failed != null) {
            reportStepFailure(runId, "revise_" + failed, resultOf(failed, outcomes));
            return null;
        }
        return revisions;
    }

    /**
     * 按**提交顺序**取第一个失败的步骤名；全都成功则返回 null。
     *
     * <p>结果按完成先后到达，直接取「第一个到达的失败」会让报错内容随线程调度变化。
     */
    private String firstFailure(List<String> submittedOrder, List<StepOutcome> outcomes) {
        for (String step : submittedOrder) {
            StepOutcome outcome = outcomeOf(step, outcomes);
            if (outcome != null && !isSuccess(outcome.result())) return step;
        }
        return null;
    }

    private StepOutcome outcomeOf(String step, List<StepOutcome> outcomes) {
        for (StepOutcome outcome : outcomes) {
            if (outcome.step().equals(step)) return outcome;
        }
        return null;
    }

    private Result<Map<String, Object>> resultOf(String step, List<StepOutcome> outcomes) {
        StepOutcome outcome = outcomeOf(step, outcomes);
        return outcome != null ? outcome.result() : null;
    }

    /** 取一个已完成的结果；线程被中断时返回 null（由调用方终止流水线）。 */
    private StepOutcome takeOutcome(ExecutorCompletionService<StepOutcome> completion) {
        try {
            return completion.take().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            // 任务内部已把异常收敛成 Result，走到这里说明是意料外的运行时错误
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return new StepOutcome("unknown", Result.error(str(cause.getMessage())));
        }
    }

    private boolean isSuccess(Result<Map<String, Object>> result) {
        return result != null && result.getCode() == 200 && result.getData() != null;
    }

    private void applyStepSuccess(Integer runId, Map<String, Object> steps, String step,
                                  Map<String, Object> data) {
        steps.put(step, data);
        persistSteps(runId, steps);
        Map<String, Object> payload = new HashMap<>();
        payload.put("step", step);
        payload.put("status", "done");
        payload.put("data", data);
        broadcast(runId, "step", payload);
    }

    private void reportStepFailure(Integer runId, String step, Result<Map<String, Object>> result) {
        String msg = result != null ? str(result.getMsg()) : "步骤执行失败";
        finish(runId, "error", null, step + "失败：" + msg);
        Map<String, Object> payload = new HashMap<>();
        payload.put("step", step);
        payload.put("status", "error");
        payload.put("message", msg);
        broadcast(runId, "step", payload);
        broadcast(runId, "fail", Map.of("message", msg, "step", step));
    }

    private void cancelRun(Integer runId) {
        finish(runId, "cancelled", null, null);
        broadcast(runId, "cancelled", Map.of("status", "cancelled"));
    }

    /**
     * 通过全局并发闸门执行一次大模型调用。
     *
     * <p>闸门是跨 run 的：多个用户同时跑流水线时，各路 fan-out 叠加会瞬间打出
     * 数倍于单条流水线的并发请求，既可能触发上游限流也会放大成本。
     * 未初始化闸门时（直接 new 出来跑单测）不做限制。
     */
    private Result<Map<String, Object>> throughAiCallGate(StepCall call) {
        Semaphore gate = aiCallGate;
        if (gate == null) return invokeQuietly(call);
        try {
            gate.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.error("等待并发闸门时被中断");
        }
        try {
            return invokeQuietly(call);
        } finally {
            gate.release();
        }
    }

    /** 统一的异常收敛：任何异常都变成失败的 Result，绝不让 worker 线程抛出。 */
    private Result<Map<String, Object>> invokeQuietly(StepCall call) {
        try {
            return call.invoke();
        } catch (Exception e) {
            return Result.error(str(e.getMessage()));
        }
    }

    /**
     * 执行一个智能体步骤：推送 running 事件 → 调用 → 落库 → 推送 done 事件。
     * 返回 null 表示流水线应终止（失败或被取消），已完成的步骤结果保留在库中。
     */
    private Map<String, Object> runStep(Integer runId, Map<String, Object> steps, String step, StepCall call) {
        if (isCancelled(runId)) {
            finish(runId, "cancelled", null, null);
            broadcast(runId, "cancelled", Map.of("status", "cancelled"));
            return null;
        }
        broadcast(runId, "step", Map.of("step", step, "status", "running"));
        Result<Map<String, Object>> result;
        try {
            result = call.invoke();
        } catch (Exception e) {
            result = Result.error(str(e.getMessage()));
        }
        // 当前 AI 请求无法强制中断，但返回后必须丢弃结果，不能继续入库或覆盖 cancelled 状态
        if (isCancelled(runId)) {
            finish(runId, "cancelled", null, null);
            broadcast(runId, "cancelled", Map.of("status", "cancelled"));
            return null;
        }
        if (result == null || result.getCode() != 200 || result.getData() == null) {
            String msg = result != null ? str(result.getMsg()) : "步骤执行失败";
            finish(runId, "error", null, step + "失败：" + msg);
            Map<String, Object> payload = new HashMap<>();
            payload.put("step", step);
            payload.put("status", "error");
            payload.put("message", msg);
            broadcast(runId, "step", payload);
            broadcast(runId, "fail", Map.of("message", msg, "step", step));
            return null;
        }
        Map<String, Object> data = result.getData();
        steps.put(step, data);
        persistSteps(runId, steps);
        Map<String, Object> payload = new HashMap<>();
        payload.put("step", step);
        payload.put("status", "done");
        payload.put("data", data);
        broadcast(runId, "step", payload);
        return data;
    }

    private void recordStep(Integer runId, Map<String, Object> steps, String step, Map<String, Object> data) {
        steps.put(step, data);
        persistSteps(runId, steps);
        Map<String, Object> payload = new HashMap<>();
        payload.put("step", step);
        payload.put("status", "done");
        payload.put("data", data);
        broadcast(runId, "step", payload);
    }

    @SuppressWarnings("unchecked")
    private List<String> assignmentFocus(Map<String, Object> coordinate, String agentKey,
                                         List<String> fallback) {
        Object raw = coordinate.get("assignments");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (!(item instanceof Map)) continue;
                Map<String, Object> assignment = (Map<String, Object>) item;
                if (!agentKey.equals(str(assignment.get("agent")))) continue;
                Object points = assignment.get("focusPoints");
                List<String> selected = new ArrayList<>();
                if (points instanceof List) {
                    for (Object point : (List<?>) points) {
                        String value = str(point).trim();
                        if (!value.isEmpty() && !selected.contains(value)) selected.add(value);
                    }
                }
                if (!selected.isEmpty()) return selected;
            }
        }
        return new ArrayList<>(fallback);
    }

    @SuppressWarnings("unchecked")
    private String assignmentInstruction(Map<String, Object> coordinate, String agentKey) {
        Object raw = coordinate.get("assignments");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (!(item instanceof Map)) continue;
                Map<String, Object> assignment = (Map<String, Object>) item;
                if (agentKey.equals(str(assignment.get("agent")))) {
                    return str(assignment.get("instruction"));
                }
            }
        }
        return "";
    }

    /** 每个下游最多注入几条上游产物。多了会挤占提示词预算，收益也不明显。 */
    private static final int MAX_HANDOFF_UPSTREAM = 3;

    /** 单条上游产物的注入上限（字符）。 */
    private static final int MAX_HANDOFF_DIGEST_CHARS = 1200;

    /**
     * 按 {@link ArtifactKey} 分派内容智能体。
     *
     * <p>用 switch 而不是反射或注册表：这七个调用的参数各不相同（出题要多传数量、
     * 讲解要多传学号），显式写出来最不容易出错，也便于编译器帮忙查漏。
     */
    private Result<Map<String, Object>> dispatchContentAgent(
            ArtifactKey key, Map<String, Object> coordinate, String subject, List<String> weakPoints,
            boolean teacher, Integer courseId, Integer studentId, int quizCount, String digest) {
        String agentKey = key.key();
        List<String> focus = assignmentFocus(coordinate, agentKey, weakPoints);
        // 交接摘要拼在协调指令之后：两者都是「给这个智能体的额外要求」，不该互相覆盖
        String instruction = assignmentInstruction(coordinate, agentKey) + digest;
        return switch (key) {
            case PLAN -> agentService.plan(subject, focus, teacher, courseId, studentId, instruction);
            case RESOURCE -> agentService.resource(subject, focus, courseId, studentId, instruction);
            case MINDMAP -> agentService.mindmap(subject, focus, courseId, instruction);
            case QUIZ -> agentService.quiz(subject, focus, quizCount, courseId, instruction);
            case READING -> agentService.reading(subject, focus, courseId, instruction);
            case CODECASE -> agentService.codecase(subject, focus, courseId, instruction);
            case EXPLAINER -> agentService.explainer(subject, explainerTopic(focus), courseId, studentId, instruction);
            default -> throw new IllegalStateException("非内容智能体不应走到这里: " + key);
        };
    }

    private static String explainerTopic(List<String> focus) {
        return String.join("、", focus.subList(0, Math.min(2, focus.size())));
    }

    /**
     * 构造注入下游的上游产物摘要。
     *
     * <p>**这一步才是「交接」有实际含义的地方**：只有当真把上游产出放进下游的提示词里，
     * 下游才谈得上「基于上游的结果继续做」。否则 handoffs 只是一个被读出来展示的字段。
     */
    private String handoffDigest(ArtifactKey target, HandoffPlanner.Plan plan, Map<String, Object> steps) {
        List<ArtifactKey> upstream = plan.accepted().stream()
                .filter(edge -> edge.to() == target)
                .map(HandoffPlanner.Handoff::from)
                .distinct()
                .limit(MAX_HANDOFF_UPSTREAM)
                .toList();
        if (upstream.isEmpty()) return "";

        StringBuilder digest = new StringBuilder("\n\n【上游智能体交接的产物】\n");
        boolean any = false;
        for (ArtifactKey key : upstream) {
            Object artifact = steps.get(key.key());
            if (artifact == null) continue;
            digest.append("- ").append(key.label()).append("：")
                    .append(truncate(summarizeArtifact(artifact), MAX_HANDOFF_DIGEST_CHARS))
                    .append('\n');
            any = true;
        }
        return any ? digest.toString() : "";
    }

    private String summarizeArtifact(Object artifact) {
        try {
            return objectMapper.writeValueAsString(artifact);
        } catch (Exception e) {
            return String.valueOf(artifact);
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    /**
     * 交接记录：写进 {@code pipeline_run} 供前端画 DAG，也让「确实注入了」可被验证，
     * 而不是只能听信一句话。
     */
    private List<Map<String, Object>> handoffRecords(HandoffPlanner.Plan plan,
                                                     Map<String, String> digests) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (HandoffPlanner.Handoff edge : plan.accepted()) {
            String digest = digests.getOrDefault(edge.to().key(), "");
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("from", edge.from().key());
            record.put("fromLabel", edge.from().label());
            record.put("to", edge.to().key());
            record.put("toLabel", edge.to().label());
            record.put("artifact", edge.artifact());
            record.put("reason", edge.reason());
            record.put("digestChars", digest.length());
            record.put("injected", !digest.isEmpty());
            records.add(record);
        }
        return records;
    }

    /**
     * 交接图的**服务端**分层布局。
     *
     * <p>刻意在服务端算：前端只负责画，图才是确定的、可复现的，Vue 侧的改动也最小。
     * 层号 = 该节点在交接 DAG 中的最长路径深度，结构段各自单独分层。
     *
     * @return {@code {nodes:[{key,label,layer}], edges:[{from,to,reason,injected}]}}
     */
    private Map<String, Object> handoffLayout(HandoffPlanner.Plan plan, Map<String, String> digests) {
        Map<String, Integer> layers = new LinkedHashMap<>();
        for (int i = 0; i < ArtifactKey.FIXED_PREFIX.size(); i++) {
            layers.put(ArtifactKey.FIXED_PREFIX.get(i).key(), i);
        }

        int base = ArtifactKey.FIXED_PREFIX.size();
        Map<String, Integer> contentDepth = new LinkedHashMap<>();
        for (ArtifactKey key : plan.contentOrder()) {
            int depth = 0;
            for (HandoffPlanner.Handoff edge : plan.accepted()) {
                if (edge.to() != key) continue;
                Integer upstream = contentDepth.get(edge.from().key());
                if (upstream != null) depth = Math.max(depth, upstream + 1);
            }
            contentDepth.put(key.key(), depth);
            layers.put(key.key(), base + depth);
        }

        int suffixBase = base + plan.contentOrder().size();
        for (int i = 0; i < ArtifactKey.FIXED_SUFFIX.size(); i++) {
            layers.put(ArtifactKey.FIXED_SUFFIX.get(i).key(), suffixBase + i);
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (ArtifactKey key : plan.order()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("key", key.key());
            node.put("label", key.label());
            node.put("layer", layers.getOrDefault(key.key(), 0));
            node.put("reorderable", key.reorderable());
            nodes.add(node);
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        for (HandoffPlanner.Handoff edge : plan.accepted()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("from", edge.from().key());
            item.put("to", edge.to().key());
            item.put("reason", edge.reason());
            item.put("injected", !digests.getOrDefault(edge.to().key(), "").isEmpty());
            edges.add(item);
        }

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        return graph;
    }

    private Map<String, Object> reviewInput(Map<String, Object> steps) {
        Map<String, Object> input = new LinkedHashMap<>();
        for (String key : List.of("diagnose", "coordinate", "plan", "resource", "mindmap",
                "quiz", "reading", "codecase", "explainer")) {
            if (steps.containsKey(key)) input.put(key, steps.get(key));
        }
        return input;
    }

    private List<String> retryAgentKeys(Map<String, Object> review) {
        if (reviewApproved(review)) return new ArrayList<>();
        List<String> allowed = List.of("plan", "resource", "mindmap", "quiz", "reading", "codecase", "explainer");
        List<String> retries = new ArrayList<>();
        Object raw = review.get("retryAgents");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                String key = str(item);
                if (allowed.contains(key) && !retries.contains(key)) retries.add(key);
                if (retries.size() >= 2) break;
            }
        }
        return retries;
    }

    private boolean reviewApproved(Map<String, Object> review) {
        Object approved = review.get("approved");
        return Boolean.TRUE.equals(approved) || "true".equalsIgnoreCase(str(approved));
    }

    @SuppressWarnings("unchecked")
    private String reviewIssue(Map<String, Object> review, String agentKey) {
        Object raw = review.get("issues");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (!(item instanceof Map)) continue;
                Map<String, Object> issue = (Map<String, Object>) item;
                if (agentKey.equals(str(issue.get("agent")))) {
                    return str(issue.get("problem")) + "；" + str(issue.get("suggestion"));
                }
            }
        }
        return "质量评审要求定向优化";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildPack(String subject, List<String> weakPoints, Map<String, Object> steps) {
        Map<String, Object> diagnose = (Map<String, Object>) steps.get("diagnose");
        Map<String, Object> pack = new HashMap<>();
        pack.put("subject", subject);
        pack.put("weakPoints", weakPoints);
        pack.put("diagnose", filterKeys(diagnose, "summary", "level", "weakPoints", "strengths"));
        pack.put("plan", filterKeys((Map<String, Object>) steps.get("plan"), "stages"));
        pack.put("resources", filterKeys((Map<String, Object>) steps.get("resource"), "sections"));
        Map<String, Object> mindmap = (Map<String, Object>) steps.get("mindmap");
        Map<String, Object> mm = new HashMap<>();
        mm.put("data", mindmap != null ? mindmap.get("mindmap") : null);
        pack.put("mindmap", mm);
        pack.put("quiz", filterKeys((Map<String, Object>) steps.get("quiz"), "questions"));
        pack.put("reading", filterKeys((Map<String, Object>) steps.get("reading"), "readings"));
        pack.put("codecase", filterKeys((Map<String, Object>) steps.get("codecase"), "cases"));
        pack.put("explainer", filterKeys((Map<String, Object>) steps.get("explainer"), "title", "summary", "scenes"));
        pack.put("groundingSources", collectGrounding(steps));
        pack.put("citations", collectCitations(steps));
        pack.put("citationValidation", collectCitationValidation(steps));
        return pack;
    }

    private List<String> collectGrounding(Map<String, Object> steps) {
        List<String> sources = new ArrayList<>();
        for (Object stepData : steps.values()) {
            if (!(stepData instanceof Map)) continue;
            Object gs = ((Map<?, ?>) stepData).get("groundingSources");
            if (gs instanceof List) {
                for (Object s : (List<?>) gs) {
                    String v = String.valueOf(s);
                    if (!sources.contains(v)) sources.add(v);
                }
            }
        }
        return sources;
    }

    private List<Object> collectCitations(Map<String, Object> steps) {
        Map<String, Object> unique = new LinkedHashMap<>();
        for (Object stepData : steps.values()) {
            if (!(stepData instanceof Map)) continue;
            Object raw = ((Map<?, ?>) stepData).get("citations");
            if (!(raw instanceof List)) continue;
            for (Object citation : (List<?>) raw) {
                String key = citation instanceof Map
                        ? str(((Map<?, ?>) citation).get("label"))
                        : str(citation);
                if (!key.isBlank()) unique.putIfAbsent(key, citation);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private Map<String, Object> collectCitationValidation(Map<String, Object> steps) {
        Map<String, Object> reports = new LinkedHashMap<>();
        int valid = 0;
        int total = 0;
        for (Map.Entry<String, Object> entry : steps.entrySet()) {
            if (!(entry.getValue() instanceof Map)) continue;
            Object report = ((Map<?, ?>) entry.getValue()).get("citationValidation");
            if (!(report instanceof Map)) continue;
            reports.put(entry.getKey(), report);
            total++;
            Object ok = ((Map<?, ?>) report).get("valid");
            if (Boolean.TRUE.equals(ok) || "true".equalsIgnoreCase(str(ok))) valid++;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("validSteps", valid);
        summary.put("totalSteps", total);
        summary.put("allValid", total == 0 || valid == total);
        summary.put("reports", reports);
        return summary;
    }

    private Map<String, Object> filterKeys(Map<String, Object> src, String... keys) {
        Map<String, Object> out = new HashMap<>();
        if (src == null) return out;
        for (String key : keys) {
            if (src.containsKey(key)) out.put(key, src.get(key));
        }
        return out;
    }

    private List<String> weakPointNames(Object weakPoints) {
        List<String> names = new ArrayList<>();
        if (weakPoints instanceof List) {
            for (Object item : (List<?>) weakPoints) {
                if (item instanceof Map) {
                    Object name = ((Map<?, ?>) item).get("name");
                    if (name != null) names.add(String.valueOf(name));
                } else if (item != null) {
                    names.add(String.valueOf(item));
                }
            }
        }
        return names;
    }

    // ==================== 状态与推送 ====================

    private boolean isCancelled(Integer runId) {
        AtomicBoolean flag = cancelFlags.get(runId);
        return flag != null && flag.get();
    }

    private void persistSteps(Integer runId, Map<String, Object> steps) {
        try {
            pipelineRunRepository.saveSteps(runId, objectMapper.writeValueAsString(steps));
        } catch (Exception e) {
            logger.warn("序列化流水线 {} 步骤失败: {}", runId, e.getMessage());
        }
    }

    private void finish(Integer runId, String status, Integer resourceId, String error) {
        pipelineRunRepository.finish(runId, status, resourceId, error);
    }

    private void broadcast(Integer runId, String event, Map<String, Object> payload) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(runId);
        if (list == null || list.isEmpty()) return;
        for (SseEmitter emitter : list) {
            sendEvent(emitter, event, payload);
        }
    }

    private void sendEvent(SseEmitter emitter, String event, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            try { emitter.complete(); } catch (Exception ignored) {}
        }
    }

    private void completeEmitters(Integer runId) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.remove(runId);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try { emitter.complete(); } catch (Exception ignored) {}
        }
    }

    private Map<String, Object> findRun(Integer runId, String ownerType, Integer ownerId) {
        return pipelineRunRepository.findOwned(runId, ownerType, ownerId).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSteps(Object stepsJson) {
        if (stepsJson == null) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(stepsJson.toString(), LinkedHashMap.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
