package com.controller;

import com.mapper.DashboardMapper;
import com.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公开接口（无需登录）：供系统介绍页 / 落地页展示平台真实运行数据。
 */
@RestController
@RequestMapping("/public")
public class PublicController {

    @Autowired
    private DashboardMapper dashboardMapper;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("students", safe(dashboardMapper::countStudents));
        data.put("teachers", safe(dashboardMapper::countTeachers));
        data.put("courses", safe(dashboardMapper::countCourses));
        data.put("homeworks", safe(dashboardMapper::countHomeworks));
        data.put("submissions", safe(dashboardMapper::countSubmissions));
        data.put("graded", safe(dashboardMapper::countGraded));
        return Result.success(data);
    }

    private long safe(java.util.function.LongSupplier supplier) {
        try {
            return supplier.getAsLong();
        } catch (Exception e) {
            return 0L;
        }
    }
}
