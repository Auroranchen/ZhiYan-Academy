package com.mapper;

import com.model.ClassInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 班级数据访问接口
 */
@Mapper
public interface ClassMapper {

    /**
     * 查询班级列表（分页）
     * 
     * @param offset 偏移量
     * @param limit  每页数量
     * @return 班级列表
     */
    List<ClassInfo> findList(@Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 查询所有班级
     * 
     * @return 班级列表
     */
    List<ClassInfo> findAll();

    /**
     * 根据专业ID查询班级列表
     * 
     * @param majorid 专业ID
     * @return 班级列表
     */
    List<ClassInfo> findByMajorid(@Param("majorid") Integer majorid);

    /**
     * 查询班级总数
     * 
     * @return 总数
     */
    Long count();

    /**
     * 根据专业ID统计班级数量
     * 
     * @param majorid 专业ID
     * @return 班级数量
     */
    Long countByMajorid(@Param("majorid") Integer majorid);

    /**
     * 根据ID查询班级
     * 
     * @param classid 班级ID
     * @return 班级对象
     */
    ClassInfo findById(@Param("classid") Integer classid);

    /**
     * 添加班级
     * 
     * @param classInfo 班级对象
     * @return 影响行数
     */
    int insert(ClassInfo classInfo);

    /**
     * 更新班级
     * 
     * @param classInfo 班级对象
     * @return 影响行数
     */
    int update(ClassInfo classInfo);

    /**
     * 删除班级
     * 
     * @param classid 班级ID
     * @return 影响行数
     */
    int delete(@Param("classid") Integer classid);
}
