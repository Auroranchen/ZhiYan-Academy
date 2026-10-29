package com.controller;

import com.config.RequireRole;
import com.model.Major;
import com.response.Result;
import com.service.MajorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 专业控制器
 * 处理专业的增删改查请求
 */
@RestController
@RequestMapping("/major")
public class MajorController {

    @Autowired
    private MajorService majorService;

    /**
     * 获取专业列表（分页）
     * 
     * @param page     页码，默认1
     * @param pageSize 每页数量，默认10
     * @return 专业列表
     */
    @GetMapping("/list")
    public Result<List<Major>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        // 查询列表
        List<Major> list = majorService.getList(page, pageSize);
        // 查询总数
        Long total = majorService.getCount();
        return Result.success(list, total);
    }

    /**
     * 获取所有专业（下拉选择用）
     * 
     * @return 专业列表
     */
    @GetMapping("/all")
    public Result<List<Major>> all() {
        List<Major> list = majorService.getAll();
        return Result.success(list);
    }

    /**
     * 获取专业详情
     * 
     * @param id 专业ID
     * @return 专业对象
     */
    @GetMapping("/{id}")
    public Result<Major> getById(@PathVariable("id") Integer id) {
        Major major = majorService.getById(id);
        if (major == null) {
            return Result.error("专业不存在");
        }
        return Result.success(major);
    }

    /**
     * 添加专业
     * 
     * @param major 专业对象
     * @return 操作结果
     */
    @PostMapping("/add")
    @RequireRole("admin")
    public Result<Void> add(@RequestBody Major major) {
        return majorService.add(major);
    }

    /**
     * 更新专业
     * 
     * @param major 专业对象
     * @return 操作结果
     */
    @PutMapping("/update")
    @RequireRole("admin")
    public Result<Void> update(@RequestBody Major major) {
        return majorService.update(major);
    }

    /**
     * 删除专业
     * 
     * @param id 专业ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{id}")
    @RequireRole("admin")
    public Result<Void> delete(@PathVariable("id") Integer id) {
        return majorService.delete(id);
    }
}
