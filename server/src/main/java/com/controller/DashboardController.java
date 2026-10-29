package com.controller;

import com.config.RequireRole;
import com.response.Result;
import com.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据可视化大屏控制器。
 */
@RestController
@RequestMapping("/dashboard")
@RequireRole("admin")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    /**
     * 返回大屏全部聚合数据。
     */
    @GetMapping("/screen")
    public Result<Map<String, Object>> screen() {
        return Result.success(dashboardService.screenData());
    }
}
