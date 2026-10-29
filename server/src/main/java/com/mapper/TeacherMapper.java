package com.mapper;

import com.model.Teacher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 教师数据访问接口
 */
@Mapper
public interface TeacherMapper {

    /**
     * 查询教师列表（分页、搜索）
     * 
     * @param keyword 搜索关键字（工号或姓名）
     * @param offset  偏移量
     * @param limit   每页数量
     * @return 教师列表
     */
    List<Teacher> findList(@Param("keyword") String keyword,
                           @Param("offset") Integer offset,
                           @Param("limit") Integer limit);

    /**
     * 查询教师总数（带搜索条件）
     * 
     * @param keyword 搜索关键字
     * @return 总数
     */
    Long count(@Param("keyword") String keyword);

    /**
     * 根据ID查询教师
     * 
     * @param teacherid 教师ID
     * @return 教师对象
     */
    Teacher findById(@Param("teacherid") Integer teacherid);

    /** 仅供认证/改密使用，包含密码哈希。 */
    Teacher findCredentialById(@Param("teacherid") Integer teacherid);

    /**
     * 根据工号查询教师
     * 
     * @param teacherno 工号
     * @return 教师对象
     */
    Teacher findByTeacherno(@Param("teacherno") String teacherno);

    /**
     * 添加教师
     * 
     * @param teacher 教师对象
     * @return 影响行数
     */
    int insert(Teacher teacher);

    /**
     * 更新教师
     * 
     * @param teacher 教师对象
     * @return 影响行数
     */
    int update(Teacher teacher);

    /**
     * 删除教师
     * 
     * @param teacherid 教师ID
     * @return 影响行数
     */
    int delete(@Param("teacherid") Integer teacherid);

    /**
     * 根据工号和密码查询教师（登录验证）
     * 
     * @param teacherno 工号
     * @param password  密码
     * @return 教师对象
     */
    /**
     * 更新教师密码
     * 
     * @param teacherid 教师ID
     * @param password  新密码
     * @return 影响行数
     */
    int updatePassword(@Param("teacherid") Integer teacherid, @Param("password") String password);

    /**
     * 获取所有教师（下拉选择用）
     * 
     * @return 教师列表
     */
    List<Teacher> findAll();

    /**
     * 更新教师头像
     * 
     * @param id     教师ID
     * @param avatar 头像路径
     * @return 影响行数
     */
    int updateAvatar(@Param("id") Integer id, @Param("avatar") String avatar);
}
