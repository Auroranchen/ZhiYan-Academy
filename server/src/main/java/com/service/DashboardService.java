package com.service;

import com.mapper.DashboardMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据可视化大屏业务层：汇总多维统计指标。
 */
@Service
public class DashboardService {

    @Autowired
    private DashboardMapper dashboardMapper;

    /**
     * 一次性返回大屏所需的全部聚合数据。
     */
    public Map<String, Object> screenData() {
        Map<String, Object> data = new HashMap<>();

        // 核心 KPI
        long submissions = dashboardMapper.countSubmissions();
        long graded = dashboardMapper.countGraded();
        Map<String, Object> kpi = new HashMap<>();
        kpi.put("students", dashboardMapper.countStudents());
        kpi.put("teachers", dashboardMapper.countTeachers());
        kpi.put("courses", dashboardMapper.countCourses());
        kpi.put("homeworks", dashboardMapper.countHomeworks());
        kpi.put("submissions", submissions);
        kpi.put("graded", graded);
        kpi.put("gradedRate", submissions > 0 ? Math.round(graded * 1000.0 / submissions) / 10.0 : 0);
        Double avg = dashboardMapper.avgScore();
        kpi.put("avgScore", avg != null ? avg : 0);
        data.put("kpi", kpi);

        // 多维图表
        data.put("homeworkByCourse", dashboardMapper.homeworkByCourse(10));
        data.put("scoreDistribution", dashboardMapper.scoreDistribution());
        data.put("submitTrend", dashboardMapper.submitTrend(14));
        data.put("avgScoreByCourse", dashboardMapper.avgScoreByCourse(10));
        data.put("studentsByMajor", dashboardMapper.studentsByMajor());
        data.put("teacherWorkload", dashboardMapper.teacherWorkload(10));

        Map<String, Object> ai = dashboardMapper.aiUsage();
        data.put("aiUsage", ai != null ? ai : new HashMap<>());

        return data;
    }

    public List<Map<String, Object>> submitTrend(int days) {
        return dashboardMapper.submitTrend(days);
    }
}
