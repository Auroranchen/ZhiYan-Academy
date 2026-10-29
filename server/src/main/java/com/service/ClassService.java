package com.service;

import com.mapper.ClassMapper;
import com.mapper.StudentMapper;
import com.model.ClassInfo;
import com.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 班级业务逻辑层
 */
@Service
public class ClassService {

    @Autowired
    private ClassMapper classMapper;

    @Autowired
    private StudentMapper studentMapper;

    /**
     * 分页查询班级列表
     * 
     * @param page     页码
     * @param pageSize 每页数量
     * @return 班级列表
     */
    public List<ClassInfo> getList(Integer page, Integer pageSize) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;
        return classMapper.findList(offset, pageSize);
    }

    /**
     * 获取所有班级
     * 
     * @return 班级列表
     */
    @Cacheable(value = "classAll")
    public List<ClassInfo> getAll() {
        return classMapper.findAll();
    }

    /**
     * 根据专业ID获取班级列表
     * 
     * @param majorid 专业ID
     * @return 班级列表
     */
    public List<ClassInfo> getByMajorid(Integer majorid) {
        return classMapper.findByMajorid(majorid);
    }

    /**
     * 获取班级总数
     * 
     * @return 总数
     */
    public Long getCount() {
        return classMapper.count();
    }

    /**
     * 根据ID获取班级
     * 
     * @param classid 班级ID
     * @return 班级对象
     */
    public ClassInfo getById(Integer classid) {
        if (classid == null) {
            return null;
        }
        return classMapper.findById(classid);
    }

    /**
     * 添加班级
     * 
     * @param classInfo 班级对象
     * @return 操作结果
     */
    @CacheEvict(value = "classAll", allEntries = true)
    public Result<Void> add(ClassInfo classInfo) {
        // 参数校验：班级名称不能为空
        if (classInfo.getClassname() == null || classInfo.getClassname().trim().isEmpty()) {
            return Result.error("班级名称不能为空");
        }
        
        // 参数校验：专业ID不能为空
        if (classInfo.getMajorid() == null) {
            return Result.error("请选择所属专业");
        }
        
        // 去除首尾空格
        classInfo.setClassname(classInfo.getClassname().trim());
        
        // 插入数据
        int rows = classMapper.insert(classInfo);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("添加失败");
    }

    /**
     * 更新班级
     * 
     * @param classInfo 班级对象
     * @return 操作结果
     */
    @CacheEvict(value = "classAll", allEntries = true)
    public Result<Void> update(ClassInfo classInfo) {
        // 参数校验：班级ID不能为空
        if (classInfo.getClassid() == null) {
            return Result.error("班级ID不能为空");
        }
        
        // 参数校验：班级名称不能为空
        if (classInfo.getClassname() == null || classInfo.getClassname().trim().isEmpty()) {
            return Result.error("班级名称不能为空");
        }
        
        // 参数校验：专业ID不能为空
        if (classInfo.getMajorid() == null) {
            return Result.error("请选择所属专业");
        }
        
        // 检查班级是否存在
        ClassInfo existing = classMapper.findById(classInfo.getClassid());
        if (existing == null) {
            return Result.error("班级不存在");
        }
        
        // 去除首尾空格
        classInfo.setClassname(classInfo.getClassname().trim());
        
        // 更新数据
        int rows = classMapper.update(classInfo);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("更新失败");
    }

    /**
     * 删除班级
     * 
     * @param classid 班级ID
     * @return 操作结果
     */
    @CacheEvict(value = "classAll", allEntries = true)
    public Result<Void> delete(Integer classid) {
        // 参数校验
        if (classid == null) {
            return Result.error("班级ID不能为空");
        }
        
        // 检查班级是否存在
        ClassInfo existing = classMapper.findById(classid);
        if (existing == null) {
            return Result.error("班级不存在");
        }
        
        // 检查是否有关联的学生
        Long studentCount = studentMapper.countByClassid(classid);
        if (studentCount > 0) {
            return Result.error("该班级下存在学生，无法删除");
        }
        
        // 删除数据
        int rows = classMapper.delete(classid);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("删除失败");
    }
}
