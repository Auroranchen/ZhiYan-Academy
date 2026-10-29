package com.mapper;

import com.model.Homework;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 作业数据访问接口
 */
@Mapper
public interface HomeworkMapper {

    /**
     * 查询作业列表（分页、搜索）
     * 
     * @param keyword 搜索关键字（作业标题）
     * @param offset  偏移量
     * @param limit   每页数量
     * @return 作业列表
     */
    List<Homework> findList(@Param("keyword") String keyword,
                            @Param("offset") Integer offset,
                            @Param("limit") Integer limit);

    /**
     * 查询作业总数（带搜索条件）
     * 
     * @param keyword 搜索关键字
     * @return 总数
     */
    Long count(@Param("keyword") String keyword);

    /**
     * 根据ID查询作业
     * 
     * @param homeworkid 作业ID
     * @return 作业对象
     */
    Homework findById(@Param("homeworkid") Integer homeworkid);

    /**
     * 根据课程ID查询作业
     * 
     * @param courseid 课程ID
     * @return 作业列表
     */
    List<Homework> findByCourseid(@Param("courseid") Integer courseid);

    /**
     * 根据教师ID查询作业
     * 
     * @param teacherid 教师ID
     * @param keyword   搜索关键字
     * @param offset    偏移量
     * @param limit     每页数量
     * @return 作业列表
     */
    List<Homework> findByTeacherid(@Param("teacherid") Integer teacherid,
                                   @Param("classid") Integer classid,
                                   @Param("keyword") String keyword,
                                   @Param("semester") String semester,
                                   @Param("offset") Integer offset,
                                   @Param("limit") Integer limit);

    /**
     * 根据教师ID查询作业总数
     * 
     * @param teacherid 教师ID
     * @param keyword   搜索关键字
     * @param semester  学期
     * @return 总数
     */
    Long countByTeacherid(@Param("teacherid") Integer teacherid,
                          @Param("classid") Integer classid,
                          @Param("keyword") String keyword,
                          @Param("semester") String semester);

    /**
     * 根据学生班级查询作业（学生可见的作业）
     * 
     * @param classid 班级ID
     * @param semester 学期
     * @param offset  偏移量
     * @param limit   每页数量
     * @return 作业列表
     */
    List<Homework> findByClassid(@Param("classid") Integer classid,
                                 @Param("semester") String semester,
                                 @Param("offset") Integer offset,
                                 @Param("limit") Integer limit);

    /**
     * 根据学生班级查询作业总数
     * 
     * @param classid 班级ID
     * @param semester 学期
     * @return 总数
     */
    Long countByClassid(@Param("classid") Integer classid,
                        @Param("semester") String semester);

    /**
     * 添加作业
     * 
     * @param homework 作业对象
     * @return 影响行数
     */
    int insert(Homework homework);

    /**
     * 更新作业
     * 
     * @param homework 作业对象
     * @return 影响行数
     */
    int update(Homework homework);

    /**
     * 删除作业
     * 
     * @param homeworkid 作业ID
     * @return 影响行数
     */
    int delete(@Param("homeworkid") Integer homeworkid);
}
