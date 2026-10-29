package com.controller;

import com.config.RequireRole;
import com.model.ClassInfo;
import com.response.Result;
import com.service.ClassService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 班级控制器
 * 处理班级的增删改查请求
 */
@RestController
@RequestMapping("/class")
public class ClassController {

    @Autowired
    private ClassService classService;

    /**
     * 获取班级列表（分页）
     * 
     * @param page     页码，默认1
     * @param pageSize 每页数量，默认10
     * @return 班级列表
     */
    @GetMapping("/list")
    public Result<List<ClassInfo>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        // 查询列表
        List<ClassInfo> list = classService.getList(page, pageSize);
        // 查询总数
        Long total = classService.getCount();
        return Result.success(list, total);
    }

    /**
     * 获取所有班级
     * 
     * @return 班级列表
     */
    @GetMapping("/all")
    public Result<List<ClassInfo>> all() {
        List<ClassInfo> list = classService.getAll();
        return Result.success(list);
    }

    /**
     * 根据专业ID获取班级列表
     * 
     * @param majorid 专业ID
     * @return 班级列表
     */
    @GetMapping("/byMajor/{majorid}")
    public Result<List<ClassInfo>> getByMajorid(@PathVariable("majorid") Integer majorid) {
        List<ClassInfo> list = classService.getByMajorid(majorid);
        return Result.success(list);
    }

    /**
     * 获取班级详情
     * 
     * @param id 班级ID
     * @return 班级对象
     */
    @GetMapping("/{id}")
    public Result<ClassInfo> getById(@PathVariable("id") Integer id) {
        ClassInfo classInfo = classService.getById(id);
        if (classInfo == null) {
            return Result.error("班级不存在");
        }
        return Result.success(classInfo);
    }

    /**
     * 添加班级
     * 
     * @param classInfo 班级对象
     * @return 操作结果
     */
    @PostMapping("/add")
    @RequireRole("admin")
    public Result<Void> add(@RequestBody ClassInfo classInfo) {
        return classService.add(classInfo);
    }

    /**
     * 更新班级
     * 
     * @param classInfo 班级对象
     * @return 操作结果
     */
    @PutMapping("/update")
    @RequireRole("admin")
    public Result<Void> update(@RequestBody ClassInfo classInfo) {
        return classService.update(classInfo);
    }

    /**
     * 删除班级
     * 
     * @param id 班级ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{id}")
    @RequireRole("admin")
    public Result<Void> delete(@PathVariable("id") Integer id) {
        return classService.delete(id);
    }
}
