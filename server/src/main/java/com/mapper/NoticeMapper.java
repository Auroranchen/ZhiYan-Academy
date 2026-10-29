package com.mapper;

import com.model.Notice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通知公告数据访问接口
 */
@Mapper
public interface NoticeMapper {

    /**
     * 查询公告列表（分页）
     * 
     * @param offset 偏移量
     * @param limit  每页数量
     * @return 公告列表
     */
    List<Notice> findList(@Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 查询公告总数
     * 
     * @return 总数
     */
    Long count();

    /**
     * 根据ID查询公告
     * 
     * @param noticeid 公告ID
     * @return 公告对象
     */
    Notice findById(@Param("noticeid") Integer noticeid);

    /**
     * 添加公告
     * 
     * @param notice 公告对象
     * @return 影响行数
     */
    int insert(Notice notice);

    /**
     * 更新公告
     * 
     * @param notice 公告对象
     * @return 影响行数
     */
    int update(Notice notice);

    /**
     * 删除公告
     * 
     * @param noticeid 公告ID
     * @return 影响行数
     */
    int delete(@Param("noticeid") Integer noticeid);
}
