package com.controller;

import com.config.RequireRole;
import com.response.Result;
import com.service.StatisticsService;
import com.service.PPTService;
import com.service.FileService;
import com.service.ResourceAccessService;
import com.mapper.HomeworkMapper;
import com.model.Homework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.Map;

/**
 * 统计报表控制器
 * 提供各类统计数据接口
 */
@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    private static final Logger log = LoggerFactory.getLogger(StatisticsController.class);

    @Autowired
    private StatisticsService statisticsService;
    @Autowired
    private PPTService pptService;
    @Autowired
    private FileService fileService;
    @Autowired
    private HomeworkMapper homeworkMapper;
    @Autowired
    private ResourceAccessService resourceAccess;

    /**
     * 获取课程作业统计数据（柱状图）
     * 统计每个课程的作业数量
     * 
     * @return 统计数据列表
     */
    @GetMapping("/homework")
    @RequireRole({"admin"})
    public Result<List<Map<String, Object>>> getCourseHomeworkStats() {
        List<Map<String, Object>> data = statisticsService.getCourseHomeworkStats();
        return Result.success(data);
    }

    /**
     * 获取学生成绩统计数据（水平条形图）
     * 统计每个学生的平均成绩
     * 
     * @param teacherid 教师ID（可选，用于筛选教师任教课程的学生）
     * @param courseid 课程ID（可选，用于筛选指定课程的学生成绩）
     * @param semester 学期（可选，用于筛选指定学期的成绩）
     * @return 统计数据列表
     */
    @GetMapping("/score")
    @RequireRole({"teacher", "admin"})
    public Result<List<Map<String, Object>>> getStudentScoreStats(
            @RequestParam(required = false) Integer teacherid,
            @RequestParam(required = false) Integer courseid,
            @RequestParam(required = false) String semester,
            HttpSession session) {
        Integer sessionTeacherId = (Integer) session.getAttribute("teacherid");
        if (sessionTeacherId != null) teacherid = sessionTeacherId;
        List<Map<String, Object>> data = statisticsService.getStudentScoreStats(teacherid, courseid, semester);
        return Result.success(data);
    }

    /**
     * 获取成绩区间统计数据（饼图）
     * 统计各成绩区间的人数
     * 
     * @param teacherid 教师ID（可选，用于筛选教师任教课程的学生）
     * @param courseid 课程ID（可选，用于筛选指定课程的成绩分布）
     * @param semester 学期（可选，用于筛选指定学期的成绩）
     * @return 统计数据列表
     */
    @GetMapping("/scoreRange")
    @RequireRole({"teacher", "admin"})
    public Result<List<Map<String, Object>>> getScoreRangeStats(
            @RequestParam(required = false) Integer teacherid,
            @RequestParam(required = false) Integer courseid,
            @RequestParam(required = false) String semester,
            HttpSession session) {
        Integer sessionTeacherId = (Integer) session.getAttribute("teacherid");
        if (sessionTeacherId != null) teacherid = sessionTeacherId;
        List<Map<String, Object>> data = statisticsService.getScoreRangeStats(teacherid, courseid, semester);
        return Result.success(data);
    }

    /**
     * 获取教师课程统计数据
     * 统计教师任教课程的作业和提交情况
     * 
     * @param id 教师ID
     * @return 统计数据列表
     */
    @GetMapping("/byTeacher/{id}")
    @RequireRole({"teacher", "admin"})
    public Result<List<Map<String, Object>>> getTeacherCourseStats(@PathVariable("id") Integer id, HttpSession session) {
        Integer teacherId = (Integer) session.getAttribute("teacherid");
        if (teacherId != null && !teacherId.equals(id)) return Result.error(403, "无权查看其他教师统计");
        List<Map<String, Object>> data = statisticsService.getTeacherCourseStats(id);
        return Result.success(data);
    }

    /**
     * 获取全校统计概览（学生数、课程数、作业数等），仅管理端使用
     *
     * @return 统计数据
     */
    @GetMapping("/overview")
    @RequireRole({"admin"})
    public Result<Map<String, Object>> getOverviewStats() {
        Map<String, Object> data = statisticsService.getOverviewStats();
        return Result.success(data);
    }

    /**
     * 作业逐题错误率。
     * 暴露的是全班答题分布，学生在交卷前拿到就等于提前知道答案倾向，
     * 因此限定为该作业所属课程的任课教师与管理员。
     */
    @GetMapping("/errorRate")
    @RequireRole({"teacher", "admin"})
    public Result<Map<String, Object>> getHomeworkErrorRate(@RequestParam Integer homeworkid,
                                                            HttpSession session) {
        if (!resourceAccess.canManageHomework(homeworkid, session)) {
            return Result.error(403, "无权查看该作业的统计");
        }
        Map<String, Object> data = statisticsService.getHomeworkErrorRate(homeworkid);
        return Result.success(data);
    }

    /**
     * 教师统计仪表盘：核心指标（学生数/作业数/提交率/平均分/待批改）+ 各作业成绩趋势
     *
     * @param teacherid 教师ID
     * @param courseid 课程ID（可选）
     * @param semester 学期（可选）
     * @return 仪表盘数据
     */
    @GetMapping("/teacherDashboard")
    @RequireRole({"teacher", "admin"})
    public Result<Map<String, Object>> getTeacherDashboard(
            @RequestParam Integer teacherid,
            @RequestParam(required = false) Integer courseid,
            @RequestParam(required = false) String semester,
            HttpSession session) {
        Integer sessionTeacherId = (Integer) session.getAttribute("teacherid");
        if (sessionTeacherId != null) teacherid = sessionTeacherId;
        Map<String, Object> data = statisticsService.getTeacherDashboard(teacherid, courseid, semester);
        return Result.success(data);
    }

    /**
     * 获取学生个人统计数据
     * 
     * @param studentid 学生ID
     * @param semester 学期（可选）
     * @return 统计数据
     */
    @GetMapping("/student/personal")
    public Result<Map<String, Object>> getStudentPersonalStats(
            @RequestParam Integer studentid,
            @RequestParam(required = false) String semester,
            HttpSession session) {
        Integer sessionStudentId = (Integer) session.getAttribute("studentid");
        boolean admin = session.getAttribute("adminid") != null;
        if (!admin && sessionStudentId == null) return Result.error(403, "无权查看学生统计");
        if (sessionStudentId != null) studentid = sessionStudentId;
        Map<String, Object> data = statisticsService.getStudentPersonalStats(studentid, semester);
        return Result.success(data);
    }
    
    @PostMapping("/exportHomeworkPPT")
    @RequireRole({"teacher", "admin"})
    public Result<Map<String, String>> exportHomeworkPPT(
            @RequestParam Integer homeworkid,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false, defaultValue = "ai") String style,
            @RequestParam(required = false) String logoPath,
            @RequestPart(required = false) MultipartFile template,
            @RequestPart(required = false) MultipartFile logo
    ) {
        Homework hw = homeworkMapper.findById(homeworkid);
        if (hw == null) {
            return Result.error("作业不存在");
        }
        Map<String, Object> stats = statisticsService.getHomeworkErrorRate(homeworkid);
        String templateAbs = null;
        if (template != null && !template.isEmpty()) {
            try {
                String saved = fileService.saveFile(template);
                templateAbs = fileService.getAbsolutePath(saved);
            } catch (Exception e) {
                return Result.error("模板保存失败");
            }
        }
        try {
            String rel = pptService.exportHomeworkPPT(hw, stats, null, templateAbs, style);
            String url = fileService.getDownloadUrl(rel);
            Map<String, String> resp = new java.util.HashMap<>();
            resp.put("url", url);
            resp.put("path", rel);
            return Result.success(resp);
        } catch (Exception e) {
            log.error("PPT 导出失败", e);
            return Result.error("PPT生成失败: " + e.getMessage());
        }
    }
}
