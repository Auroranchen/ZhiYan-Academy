package com.mapper;

import com.model.Student;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 学生数据访问接口
 */
@Mapper
public interface StudentMapper {

    /**
     * 查询学生列表（分页、搜索）
     * 
     * @param keyword 搜索关键字（学号或姓名）
     * @param classid 班级ID
     * @param majorid 专业ID
     * @param offset  偏移量
     * @param limit   每页数量
     * @return 学生列表
     */
    List<Student> findList(@Param("keyword") String keyword,
                           @Param("classid") Integer classid,
                           @Param("majorid") Integer majorid,
                           @Param("offset") Integer offset,
                           @Param("limit") Integer limit);

    /**
     * 查询学生总数（带搜索条件）
     * 
     * @param keyword 搜索关键字
     * @param classid 班级ID
     * @param majorid 专业ID
     * @return 总数
     */
    Long count(@Param("keyword") String keyword,
               @Param("classid") Integer classid,
               @Param("majorid") Integer majorid);

    /**
     * 根据班级ID统计学生数量
     * 
     * @param classid 班级ID
     * @return 学生数量
     */
    Long countByClassid(@Param("classid") Integer classid);

    /**
     * 根据ID查询学生
     * 
     * @param studentid 学生ID
     * @return 学生对象
     */
    Student findById(@Param("studentid") Integer studentid);

    /** 仅供认证/改密使用，包含密码哈希。 */
    Student findCredentialById(@Param("studentid") Integer studentid);

    /**
     * 根据学号查询学生
     * 
     * @param studentno 学号
     * @return 学生对象
     */
    Student findByStudentno(@Param("studentno") String studentno);

    /**
     * 添加学生
     * 
     * @param student 学生对象
     * @return 影响行数
     */
    int insert(Student student);

    /**
     * 更新学生
     * 
     * @param student 学生对象
     * @return 影响行数
     */
    int update(Student student);

    /**
     * 删除学生
     * 
     * @param studentid 学生ID
     * @return 影响行数
     */
    int delete(@Param("studentid") Integer studentid);

    /**
     * 根据学号和密码查询学生（登录验证）
     * 
     * @param studentno 学号
     * @param password  密码
     * @return 学生对象
     */
    /**
     * 更新学生密码
     * 
     * @param studentid 学生ID
     * @param password  新密码
     * @return 影响行数
     */
    int updatePassword(@Param("studentid") Integer studentid, @Param("password") String password);

    /**
     * 根据班级ID查询学生列表
     * 
     * @param classid 班级ID
     * @return 学生列表
     */
    List<Student> findByClassid(@Param("classid") Integer classid);

    /**
     * 更新学生头像
     * 
     * @param id     学生ID
     * @param avatar 头像路径
     * @return 影响行数
     */
    int updateAvatar(@Param("id") Integer id, @Param("avatar") String avatar);
}
