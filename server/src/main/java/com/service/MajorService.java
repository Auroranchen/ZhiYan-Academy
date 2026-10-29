package com.service;

import com.mapper.ClassMapper;
import com.mapper.MajorMapper;
import com.model.Major;
import com.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 专业业务逻辑层
 */
@Service
public class MajorService {

    @Autowired
    private MajorMapper majorMapper;

    @Autowired
    private ClassMapper classMapper;

    /**
     * 分页查询专业列表
     * 
     * @param page     页码
     * @param pageSize 每页数量
     * @return 专业列表
     */
    public List<Major> getList(Integer page, Integer pageSize) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;
        return majorMapper.findList(offset, pageSize);
    }

    /**
     * 获取所有专业（下拉选择用）
     * 
     * @return 专业列表
     */
    @Cacheable(value = "majorAll")
    public List<Major> getAll() {
        return majorMapper.findAll();
    }

    /**
     * 获取专业总数
     * 
     * @return 总数
     */
    public Long getCount() {
        return majorMapper.count();
    }

    /**
     * 根据ID获取专业
     * 
     * @param majorid 专业ID
     * @return 专业对象
     */
    public Major getById(Integer majorid) {
        if (majorid == null) {
            return null;
        }
        return majorMapper.findById(majorid);
    }

    /**
     * 添加专业
     * 
     * @param major 专业对象
     * @return 操作结果
     */
    @CacheEvict(value = "majorAll", allEntries = true)
    public Result<Void> add(Major major) {
        // 参数校验：专业名称不能为空
        if (major.getMajorname() == null || major.getMajorname().trim().isEmpty()) {
            return Result.error("专业名称不能为空");
        }
        
        // 去除首尾空格
        major.setMajorname(major.getMajorname().trim());
        
        // 插入数据
        int rows = majorMapper.insert(major);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("添加失败");
    }

    /**
     * 更新专业
     * 
     * @param major 专业对象
     * @return 操作结果
     */
    @CacheEvict(value = "majorAll", allEntries = true)
    public Result<Void> update(Major major) {
        // 参数校验：专业ID不能为空
        if (major.getMajorid() == null) {
            return Result.error("专业ID不能为空");
        }
        
        // 参数校验：专业名称不能为空
        if (major.getMajorname() == null || major.getMajorname().trim().isEmpty()) {
            return Result.error("专业名称不能为空");
        }
        
        // 检查专业是否存在
        Major existing = majorMapper.findById(major.getMajorid());
        if (existing == null) {
            return Result.error("专业不存在");
        }
        
        // 去除首尾空格
        major.setMajorname(major.getMajorname().trim());
        
        // 更新数据
        int rows = majorMapper.update(major);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("更新失败");
    }

    /**
     * 删除专业
     * 
     * @param majorid 专业ID
     * @return 操作结果
     */
    @CacheEvict(value = "majorAll", allEntries = true)
    public Result<Void> delete(Integer majorid) {
        // 参数校验
        if (majorid == null) {
            return Result.error("专业ID不能为空");
        }
        
        // 检查专业是否存在
        Major existing = majorMapper.findById(majorid);
        if (existing == null) {
            return Result.error("专业不存在");
        }
        
        // 检查是否有关联的班级
        Long classCount = classMapper.countByMajorid(majorid);
        if (classCount > 0) {
            return Result.error("该专业下存在班级，无法删除");
        }
        
        // 删除数据
        int rows = majorMapper.delete(majorid);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("删除失败");
    }
}
