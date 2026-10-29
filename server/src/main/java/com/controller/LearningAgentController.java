package com.controller;

import com.config.RequireRole;
import com.response.Result;
import com.service.AgentPipelineService;
import com.service.LearningAgentService;
import com.service.ResourceAccessService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 学习多智能体控制器。
 * 前端按步调用各智能体接口，实现协作过程可视化与个性化资源生成。
 */
@RestController
@RequestMapping("/agent")
public class LearningAgentController {

    @Autowired
    private LearningAgentService agentService;

    @Autowired
    private AgentPipelineService pipelineService;

    @Autowired
    private ResourceAccessService resourceAccess;

    // ============== 服务端流水线编排（SSE 流式 + 断点恢复） ==============

    /** 启动一次完整的多智能体流水线；若已有运行中的流水线则直接返回其 runId 供前端重连 */
    @PostMapping("/pipeline/start")
    public Result<Map<String, Object>> pipelineStart(@RequestBody(required = false) Map<String, Object> body,
                                                     HttpSession session) {
        Owner owner = currentOwner(session);
        if (owner == null) return Result.error(401, "请先登录");
        Integer courseId = body != null ? toInt(body.get("courseid")) : null;
        if (owner.teacher && courseId == null) return Result.error("教师模式请先选择课程");
        Integer count = body != null ? toInt(body.get("quizCount")) : null;
        List<String> focusPoints = body != null ? toStrList(body.get("focusPoints")) : new ArrayList<>();
        return pipelineService.start(owner.type, owner.id, courseId, count == null ? 5 : count, focusPoints);
    }

    /** SSE 订阅流水线进度（回放已完成步骤 + 实时推送后续步骤） */
    @GetMapping("/pipeline/stream/{runId}")
    public SseEmitter pipelineStream(@PathVariable Integer runId, HttpSession session) {
        Owner owner = currentOwner(session);
        if (owner == null) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.complete();
            return emitter;
        }
        return pipelineService.stream(runId, owner.type, owner.id);
    }

    @PostMapping("/pipeline/cancel/{runId}")
    public Result<Void> pipelineCancel(@PathVariable Integer runId, HttpSession session) {
        Owner owner = currentOwner(session);
        if (owner == null) return Result.error(401, "请先登录");
        return pipelineService.cancel(runId, owner.type, owner.id);
    }

    @GetMapping("/pipeline/latest")
    public Result<Map<String, Object>> pipelineLatest(HttpSession session) {
        Owner owner = currentOwner(session);
        if (owner == null) return Result.error(401, "请先登录");
        return pipelineService.latest(owner.type, owner.id);
    }

    // ============== 诊断智能体 ==============

    /** 学生：个人学情诊断（可按课程） */
    @PostMapping("/diagnose")
    public Result<Map<String, Object>> diagnose(@RequestBody(required = false) Map<String, Object> body, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid == null) return Result.error(401, "请先登录学生账号");
        Integer courseid = body != null ? toInt(body.get("courseid")) : null;
        return agentService.diagnoseStudent(studentid, courseid);
    }

    /**
     * 教师：班级/课程学情诊断。
     * 返回的是全班成绩分布与薄弱学生名单，必须限定在本人任教的课程内。
     */
    @PostMapping("/diagnose-class")
    @RequireRole({"teacher", "admin"})
    public Result<Map<String, Object>> diagnoseClass(@RequestBody Map<String, Object> body, HttpSession session) {
        Integer courseid = toInt(body.get("courseid"));
        if (!resourceAccess.canManageCourse(courseid, session)) {
            return Result.error(403, "无权查看该课程的学情");
        }
        return agentService.diagnoseClass(courseid);
    }

    // ============== 规划 / 资源 / 出题 智能体（学生、教师共用） ==============

    @PostMapping("/plan")
    public Result<Map<String, Object>> plan(@RequestBody Map<String, Object> body, HttpSession session) {
        if (notLogged(session)) return Result.error(401, "请先登录");
        return agentService.plan((String) body.get("subject"), toStrList(body.get("weakPoints")),
                isTeacher(session), toInt(body.get("courseid")), (Integer) session.getAttribute("studentid"));
    }

    @PostMapping("/resource")
    public Result<Map<String, Object>> resource(@RequestBody Map<String, Object> body, HttpSession session) {
        if (notLogged(session)) return Result.error(401, "请先登录");
        return agentService.resource((String) body.get("subject"), toStrList(body.get("weakPoints")),
                toInt(body.get("courseid")), (Integer) session.getAttribute("studentid"));
    }

    @PostMapping("/quiz")
    public Result<Map<String, Object>> quiz(@RequestBody Map<String, Object> body, HttpSession session) {
        if (notLogged(session)) return Result.error(401, "请先登录");
        Integer count = toInt(body.get("count"));
        return agentService.quiz((String) body.get("subject"), toStrList(body.get("weakPoints")),
                count == null ? 5 : count, toInt(body.get("courseid")));
    }

    @PostMapping("/mindmap")
    public Result<Map<String, Object>> mindmap(@RequestBody Map<String, Object> body, HttpSession session) {
        if (notLogged(session)) return Result.error(401, "请先登录");
        return agentService.mindmap((String) body.get("subject"), toStrList(body.get("weakPoints")),
                toInt(body.get("courseid")));
    }

    @PostMapping("/reading")
    public Result<Map<String, Object>> reading(@RequestBody Map<String, Object> body, HttpSession session) {
        if (notLogged(session)) return Result.error(401, "请先登录");
        return agentService.reading((String) body.get("subject"), toStrList(body.get("weakPoints")),
                toInt(body.get("courseid")));
    }

    @PostMapping("/codecase")
    public Result<Map<String, Object>> codecase(@RequestBody Map<String, Object> body, HttpSession session) {
        if (notLogged(session)) return Result.error(401, "请先登录");
        return agentService.codecase((String) body.get("subject"), toStrList(body.get("weakPoints")),
                toInt(body.get("courseid")));
    }

    /** 多模态讲解动画（分镜脚本） */
    @PostMapping("/explainer")
    public Result<Map<String, Object>> explainer(@RequestBody Map<String, Object> body, HttpSession session) {
        if (notLogged(session)) return Result.error(401, "请先登录");
        return agentService.explainer((String) body.get("subject"), (String) body.get("topic"),
                toInt(body.get("courseid")), (Integer) session.getAttribute("studentid"));
    }

    // ============== 编排智能体 + 入库 ==============

    @PostMapping("/assemble")
    public Result<Map<String, Object>> assemble(@RequestBody Map<String, Object> body, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        Integer teacherid = (Integer) session.getAttribute("teacherid");
        if (studentid == null && teacherid == null) return Result.error(401, "请先登录");
        Integer courseId = toInt(body.get("courseid"));
        if (teacherid != null) {
            return agentService.assemble("teacher", teacherid, "class", courseId, body);
        }
        return agentService.assemble("student", studentid, courseId != null ? "course" : "student", courseId, body);
    }

    // ============== 资源库 ==============

    @GetMapping("/library")
    public Result<List<Map<String, Object>>> library(HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        Integer teacherid = (Integer) session.getAttribute("teacherid");
        if (teacherid != null) return Result.success(agentService.library("teacher", teacherid));
        if (studentid != null) return Result.success(agentService.library("student", studentid));
        return Result.error(401, "请先登录");
    }

    @GetMapping("/library/{id}")
    public Result<Map<String, Object>> libraryDetail(@PathVariable Integer id, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        Integer teacherid = (Integer) session.getAttribute("teacherid");
        if (teacherid != null) return agentService.libraryDetail("teacher", teacherid, id);
        if (studentid != null) return agentService.libraryDetail("student", studentid, id);
        return Result.error(401, "请先登录");
    }

    /** 教师：把班级资源包一键推送给课程内学生 */
    @PostMapping("/push/{id}")
    @RequireRole({"teacher"})
    public Result<Map<String, Object>> push(@PathVariable Integer id, HttpSession session) {
        Integer teacherid = (Integer) session.getAttribute("teacherid");
        if (teacherid == null) return Result.error(401, "请先登录教师账号");
        return agentService.pushToStudents(teacherid, id);
    }

    @DeleteMapping("/library/{id}")
    public Result<Void> deleteResource(@PathVariable Integer id, HttpSession session) {
        Integer studentid = (Integer) session.getAttribute("studentid");
        Integer teacherid = (Integer) session.getAttribute("teacherid");
        if (teacherid != null) return agentService.deleteResource("teacher", teacherid, id);
        if (studentid != null) return agentService.deleteResource("student", studentid, id);
        return Result.error(401, "请先登录");
    }

    // ============== 工具 ==============

    private record Owner(String type, Integer id, boolean teacher) {}

    private Owner currentOwner(HttpSession session) {
        Integer teacherid = (Integer) session.getAttribute("teacherid");
        if (teacherid != null) return new Owner("teacher", teacherid, true);
        Integer studentid = (Integer) session.getAttribute("studentid");
        if (studentid != null) return new Owner("student", studentid, false);
        return null;
    }

    private boolean notLogged(HttpSession session) {
        return session.getAttribute("studentid") == null && session.getAttribute("teacherid") == null;
    }

    private boolean isTeacher(HttpSession session) {
        return session.getAttribute("teacherid") != null;
    }

    @SuppressWarnings("unchecked")
    private List<String> toStrList(Object o) {
        List<String> list = new ArrayList<>();
        if (o instanceof List) {
            for (Object item : (List<Object>) o) {
                if (item == null) continue;
                if (item instanceof Map) {
                    Object name = ((Map<String, Object>) item).get("name");
                    if (name != null) list.add(name.toString());
                } else {
                    list.add(item.toString());
                }
            }
        }
        return list;
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); }
        catch (NumberFormatException e) { return null; }
    }
}
