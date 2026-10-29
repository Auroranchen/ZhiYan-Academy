package com.mapper;

import com.model.CourseClass;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 课程班级关联数据访问接口
 */
@Mapper
public interface CourseClassMapper {

    /**
     * 根据课程ID查询关联的班级
     * 
     * @param courseid 课程ID
     * @return 关联列表
     */
    List<CourseClass> findByCourseid(@Param("courseid") Integer courseid);

    /**
     * 根据班级ID查询关联的课程
     * 
     * @param classid 班级ID
     * @return 关联列表
     */
    List<CourseClass> findByClassid(@Param("classid") Integer classid);

    /**
     * 添加课程班级关联
     * 
     * @param courseClass 关联对象
     * @return 影响行数
     */
    int insert(CourseClass courseClass);

    /**
     * 批量添加课程班级关联
     * 
     * @param list 关联列表
     * @return 影响行数
     */
    int insertBatch(@Param("list") List<CourseClass> list);

    /**
     * 根据课程ID查询关联的班级ID列表
     * 
     * @param courseid 课程ID
     * @return 班级ID列表
     */
    List<Integer> findClassIdsByCourseId(@Param("courseid") Integer courseid);

    /**
     * 统计课程与班级的关联数量
     * 
     * @param courseid 课程ID
     * @param classid 班级ID
     * @return 数量
     */
    int countByCourseIdAndClassId(@Param("courseid") Integer courseid, @Param("classid") Integer classid);

    /**
     * 删除课程的所有班级关联
     * 
     * @param courseid 课程ID
     * @return 影响行数
     */
    int deleteByCourseId(@Param("courseid") Integer courseid);

    /**
     * 删除课程的所有班级关联（兼容旧方法名）
     * 
     * @param courseid 课程ID
     * @return 影响行数
     */
    int deleteByCourseid(@Param("courseid") Integer courseid);

    /**
     * 删除班级的所有课程关联
     * 
     * @param classid 班级ID
     * @return 影响行数
     */
    int deleteByClassid(@Param("classid") Integer classid);
}
