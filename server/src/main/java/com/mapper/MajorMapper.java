package com.mapper;

import com.model.Major;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 专业数据访问接口
 */
@Mapper
public interface MajorMapper {

    /**
     * 查询专业列表（分页）
     * 
     * @param offset 偏移量
     * @param limit  每页数量
     * @return 专业列表
     */
    List<Major> findList(@Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 查询所有专业
     * 
     * @return 专业列表
     */
    List<Major> findAll();

    /**
     * 查询专业总数
     * 
     * @return 总数
     */
    Long count();

    /**
     * 根据ID查询专业
     * 
     * @param majorid 专业ID
     * @return 专业对象
     */
    Major findById(@Param("majorid") Integer majorid);

    /**
     * 添加专业
     * 
     * @param major 专业对象
     * @return 影响行数
     */
    int insert(Major major);

    /**
     * 更新专业
     * 
     * @param major 专业对象
     * @return 影响行数
     */
    int update(Major major);

    /**
     * 删除专业
     * 
     * @param majorid 专业ID
     * @return 影响行数
     */
    int delete(@Param("majorid") Integer majorid);
}
