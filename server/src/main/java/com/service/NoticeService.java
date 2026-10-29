package com.service;

import com.mapper.NoticeMapper;
import com.model.Notice;
import com.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 通知公告业务逻辑层
 */
@Service
public class NoticeService {

    @Autowired
    private NoticeMapper noticeMapper;

    /**
     * 分页查询公告列表
     * 
     * @param page     页码
     * @param pageSize 每页数量
     * @return 公告列表
     */
    public List<Notice> getList(Integer page, Integer pageSize) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;
        return noticeMapper.findList(offset, pageSize);
    }

    /**
     * 获取公告总数
     * 
     * @return 总数
     */
    public Long getCount() {
        return noticeMapper.count();
    }

    /**
     * 根据ID获取公告
     * 
     * @param noticeid 公告ID
     * @return 公告对象
     */
    public Notice getById(Integer noticeid) {
        if (noticeid == null) {
            return null;
        }
        return noticeMapper.findById(noticeid);
    }

    /**
     * 添加公告
     * 
     * @param notice 公告对象
     * @return 操作结果
     */
    public Result<Void> add(Notice notice) {
        // 参数校验：标题不能为空
        if (notice.getTitle() == null || notice.getTitle().trim().isEmpty()) {
            return Result.error("公告标题不能为空");
        }
        
        // 参数校验：内容不能为空
        if (notice.getContent() == null || notice.getContent().trim().isEmpty()) {
            return Result.error("公告内容不能为空");
        }
        
        // 去除标题首尾空格
        notice.setTitle(notice.getTitle().trim());
        
        // 设置创建时间
        notice.setCreatetime(new Date());
        
        // 插入数据
        int rows = noticeMapper.insert(notice);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("添加失败");
    }

    /**
     * 更新公告
     * 
     * @param notice 公告对象
     * @return 操作结果
     */
    public Result<Void> update(Notice notice) {
        // 参数校验：公告ID不能为空
        if (notice.getNoticeid() == null) {
            return Result.error("公告ID不能为空");
        }
        
        // 参数校验：标题不能为空
        if (notice.getTitle() == null || notice.getTitle().trim().isEmpty()) {
            return Result.error("公告标题不能为空");
        }
        
        // 参数校验：内容不能为空
        if (notice.getContent() == null || notice.getContent().trim().isEmpty()) {
            return Result.error("公告内容不能为空");
        }
        
        // 检查公告是否存在
        Notice existing = noticeMapper.findById(notice.getNoticeid());
        if (existing == null) {
            return Result.error("公告不存在");
        }
        
        // 去除标题首尾空格
        notice.setTitle(notice.getTitle().trim());
        
        // 更新数据
        int rows = noticeMapper.update(notice);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("更新失败");
    }

    /**
     * 删除公告
     * 
     * @param noticeid 公告ID
     * @return 操作结果
     */
    public Result<Void> delete(Integer noticeid) {
        // 参数校验
        if (noticeid == null) {
            return Result.error("公告ID不能为空");
        }
        
        // 检查公告是否存在
        Notice existing = noticeMapper.findById(noticeid);
        if (existing == null) {
            return Result.error("公告不存在");
        }
        
        // 删除数据
        int rows = noticeMapper.delete(noticeid);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("删除失败");
    }
}
