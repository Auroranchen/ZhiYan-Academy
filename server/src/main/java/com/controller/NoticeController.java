package com.controller;

import com.config.RequireRole;

import com.model.Notice;
import com.response.Result;
import com.service.NoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知公告控制器
 * 处理通知公告的增删改查请求
 */
@RestController
@RequestMapping("/notice")
public class NoticeController {

    @Autowired
    private NoticeService noticeService;

    /**
     * 获取公告列表（分页）
     * 
     * @param page     页码，默认1
     * @param pageSize 每页数量，默认10
     * @return 公告列表
     */
    @GetMapping("/list")
    public Result<List<Notice>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        // 查询列表
        List<Notice> list = noticeService.getList(page, pageSize);
        // 查询总数
        Long total = noticeService.getCount();
        return Result.success(list, total);
    }

    /**
     * 获取公告详情
     * 
     * @param id 公告ID
     * @return 公告对象
     */
    @GetMapping("/{id}")
    public Result<Notice> getById(@PathVariable("id") Integer id) {
        Notice notice = noticeService.getById(id);
        if (notice == null) {
            return Result.error("公告不存在");
        }
        return Result.success(notice);
    }

    /**
     * 添加公告
     * 
     * @param notice 公告对象
     * @return 操作结果
     */
    @PostMapping("/add")
    @RequireRole("admin")
    public Result<Void> add(@RequestBody Notice notice) {
        return noticeService.add(notice);
    }

    /**
     * 更新公告
     * 
     * @param notice 公告对象
     * @return 操作结果
     */
    @PutMapping("/update")
    @RequireRole("admin")
    public Result<Void> update(@RequestBody Notice notice) {
        return noticeService.update(notice);
    }

    /**
     * 删除公告
     * 
     * @param id 公告ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{id}")
    @RequireRole("admin")
    public Result<Void> delete(@PathVariable("id") Integer id) {
        return noticeService.delete(id);
    }
}
