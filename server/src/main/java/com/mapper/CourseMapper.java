package com.mapper;

import com.model.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 课程数据访问接口
 */
@Mapper
public interface CourseMapper {

    /**
     * 查询课程列表（分页、搜索）
     * 
     * @param keyword 搜索关键字（课程名称）
     * @param offset  偏移量
     * @param limit   每页数量
     * @return 课程列表
     */
    List<Course> findList(@Param("keyword") String keyword,
                          @Param("offset") Integer offset,
                          @Param("limit") Integer limit);

    /**
     * 查询课程总数（带搜索条件）
     * 
     * @param keyword 搜索关键字
     * @return 总数
     */
    Long count(@Param("keyword") String keyword);

    /**
     * 查询所有课程（下拉选择用）
     * 
     * @return 课程列表
     */
    List<Course> findAll();

    /**
     * 根据ID查询课程
     * 
     * @param courseid 课程ID
     * @return 课程对象
     */
    Course findById(@Param("courseid") Integer courseid);

    /**
     * 根据教师ID查询课程
     * 
     * @param teacherid 教师ID
     * @param semester 学期（可选）
     * @return 课程列表
     */
    List<Course> findByTeacherId(@Param("teacherid") Integer teacherid, @Param("semester") String semester);

    /**
     * 根据班级ID查询课程
     * 
     * @param classid 班级ID
     * @param semester 学期（可选）
     * @return 课程列表
     */
    List<Course> findByClassId(@Param("classid") Integer classid, @Param("semester") String semester);

    /**
     * 根据课程码查询课程
     * 
     * @param coursecode 课程码
     * @return 课程对象
     */
    Course findByCode(@Param("coursecode") String coursecode);

    /**
     * 添加课程
     * 
     * @param course 课程对象
     * @return 影响行数
     */
    int insert(Course course);

    /**
     * 更新课程
     * 
     * @param course 课程对象
     * @return 影响行数
     */
    int update(Course course);

    /**
     * 删除课程
     * 
     * @param courseid 课程ID
     * @return 影响行数
     */
    int delete(@Param("courseid") Integer courseid);

    /**
     * 检查课程下是否有作业
     * 
     * @param courseid 课程ID
     * @return 作业数量
     */
    int countHomework(@Param("courseid") Integer courseid);
}
